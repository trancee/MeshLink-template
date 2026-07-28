# PeerIdentity & TrustStore Persistence Strategy

**Status:** Locked — 2026-07-26

## Decision

**Use `expect`/`actual` platform-specific storage with hardware-backed encryption:**

- Android: `Jetpack DataStore` + `Android Keystore` (AES-256-GCM)
- iOS: `UserDefaults` + `Keychain` (Secure Enclave backed)
- JVM (desktop/test): `File` + `AES-256-GCM` (software, test only)

Only **minimal state** is persisted:

1. **PeerIdentity** (16 bytes) — generated once at install
2. **TrustStore** — map of `PeerIdentity → TrustRecord` (public keys, timestamps, state)
3. **Local Identity Keys** — Ed25519 + X25519 private keys (encrypted)

**NOT persisted:** Diagnostics, route tables, transfer sessions, ephemeral keys, scan results.

## Data Model

### PeerIdentity (Generated Once)

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/storage/PeerIdentityStore.kt
@Serializable
data class StoredPeerIdentity(
    val identity: PeerIdentity,      // 16 bytes
    val version: Int = 1             // Schema version
    val createdAt: Instant,          // Generation timestamp
)
```

### TrustRecord

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/storage/TrustStore.kt
@Serializable
data class TrustRecord(
    val peerIdentity: PeerIdentity,
    val identityKey: ByteArray,     // 32 bytes
    val handshakeKey: ByteArray,      // 32 bytes
    var state: TrustState = TrustState.INITIATED,
    var generation: Int = 0,          // Key rotation count
    val seenAt: Instant,                 // First handshake
    var verifiedAt: Instant,             // Last successful verify
) {
    enum class TrustState {
        INITIATED,   // Handshake in progress
        TRUSTED,     // TOFU pinned
        REVOKED,     // Explicitly revoked
    }
}
```

### Local Identity Keys

```kotlin
@Serializable
data class LocalIdentityKeys(
    val identityKey: ByteArray,     // 32 bytes (seed)
    val handshakeKey: ByteArray,     // 32 bytes (seed)
    val generation: Int,              // Key version
    val createdAt: Instant,
)
```

## Android Implementation

### Storage Layer (DataStore)

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/storage/AndroidPeerIdentityStore.kt

class AndroidPeerIdentityStore @Inject constructor(
    @ApplicationContext context: Context,
    private val masterKey: MasterKey
) : PeerIdentityStore {
    
    private val dataStore: DataStore<Preferences> = 
        context.preferencesDataStore("meshlink_identity", produceMigrations = { 
            listOf(PreferenceMigration(1, { prefs -> /* v1 migration */ })) 
        })
    
    private val serializer = PreferencesSerializer()
    
    override suspend fun getPeerIdentity(): Result<PeerIdentity> = runCatching {
        val prefs = dataStore.data.first()
        val encoded = prefs[Preferences.Key<String>("peer_identity.b64")]!!
        PeerIdentity.fromBytes(Base64.getDecoder().decode(encoded))
    }
    
    override suspend fun setPeerIdentity(identity: PeerIdentity) {
        dataStore.edit { prefs ->
            prefs[Preferences.Key<String>("peer_identity.b64")] = Base64.getEncoder().encodeToString(identity.toByteArray())
        }
    }
}
```

### Encryption (Android Keystore)

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/crypto/AndroidKeyManager.kt

class AndroidKeyManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKeyAlias = "meshlink_master_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setUserAuthenticationRequired(false)
        .setKeyAlias(masterKeyAlias)
        .build()
    
    /** Encrypts data with master key (hardware-backed if available). */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        return cipher.doFinal(plaintext)
    }
    
    /** Decrypts data with master key. */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey)
        return cipher.doFinal(ciphertext)
    }
}
```

### TrustStore (Encrypted DataStore)

```kotlin
// meshlink/src/androidMain/kotlin/ch/trancee/meshlink/android/storage/AndroidTrustStore.kt

class AndroidTrustStore @Inject constructor(
    @ApplicationContext context: Context,
    private val keyManager: AndroidKeyManager
) : TrustStore {
    
    private val TRUST_STORE_KEY = "trust_store_v1"
    
    override suspend fun getAll(): Result<Map<PeerIdentity, TrustRecord>> = runCatching {
        val prefs = dataStore.data.first()
        val encoded = prefs[Preferences.Key<String>("$TRUST_STORE_KEY.b64")]
        if (encoded == null) return@runCatching emptyMap()
        
        val decrypted = keyManager.decrypt(Base64.getDecoder().decode(encoded))
        Json.decodeFromByteArray(TrustRecord.mapSerializer(), decrypted)
    }
    
    override suspend fun put(record: TrustRecord) {
        val current = getAll().getOrNull() ?: emptyMap()
        val updated = current + (record.peerIdentity to record)
        val encrypted = keyManager.encrypt(Json.encodeToByteArray(TrustRecord.mapSerializer(), updated))
        dataStore.edit { prefs ->
            prefs[Preferences.Key<String>("$TRUST_STORE_KEY.b64")] = Base64.getEncoder().encodeToString(encrypted)
        }
    }
    
    override suspend fun remove(peer: PeerIdentity) {
        val current = getAll().getOrNull() ?: emptyMap()
        val updated = current - peer
        val encrypted = keyManager.encrypt(Json.encodeToByteArray(TrustRecord.mapSerializer(), updated))
        dataStore.edit { prefs ->
            prefs[Preferences.Key<String>("$TRUST_STORE_KEY.b64")] = Base64.getEncoder().encodeToString(encrypted)
        }
    }
}
```

## iOS Implementation

### Storage Layer (Keychain + UserDefaults)

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/storage/IosPeerIdentityStore.swift

import Foundation
import Security

class IosPeerIdentityStore: PeerIdentityStore {
    private let service = "ch.trancee.meshlink"
    private let peerIdentityAccount = "peer_identity"
    private let trustStoreAccount = "trust_store"
    private let localKeysAccount = "local_keys"
    
    // PeerIdentity: Store in Keychain (persists across app deletes if keychain not cleared)
    func getPeerIdentity() -> PeerIdentity? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: peerIdentityAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return PeerIdentity.fromBytes([UInt8](data))
    }
    
    func setPeerIdentity(_ identity: PeerIdentity) {
        let data = Data(identity.toByteArray())
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: peerIdentityAccount,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemDelete(query as CFDictionary) // Remove old
        SecItemAdd(query as CFDictionary, nil)
    }
}
```

### TrustStore (Keychain with Encryption)

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/storage/IosTrustStore.swift

class IosTrustStore: TrustStore {
    private let crypto = IosCrypto()
    
    func getAll() -> [PeerIdentity: TrustRecord] {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: trustStoreAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let encrypted = result as? Data else { return [:] }
        
        let decrypted = crypto.decrypt(encrypted)
        return try! JSONDecoder().decode([PeerIdentity: TrustRecord].self, from: decrypted)
    }
    
    func put(_ record: TrustRecord) {
        var current = getAll()
        current[record.peerIdentity] = record
        let data = try! JSONEncoder().encode(current)
        let encrypted = crypto.encrypt(data)
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: trustStoreAccount,
            kSecValueData as String: encrypted,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }
}
```

### Local Keys (Keychain with Secure Enclave)

```swift
// meshlink/src/iosMain/kotlin/ch/trancee/meshlink/ios/crypto/IosCrypto.swift

class IosCrypto {
    private let keyTag = "ch.trancee.meshlink.master_key".data(using: .utf8)!
    
    // Generate/retrieve master key in Secure Enclave
    private func getMasterKey() -> SecKey {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: keyTag,
            kSecAttrKeyType as String: kSecAttrKeyTypeAES,
            kSecAttrKeySizeInBits as String: 256,
            kSecReturnRef as String: true
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess { return result as! SecKey }
        
        // Generate new
        let genQuery: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: keyTag,
            kSecAttrKeyType as String: kSecAttrKeyTypeAES,
            kSecAttrKeySizeInBits as String: 256,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave, // Secure Enclave!
            kSecReturnRef as String: true
        ]
        var newKey: AnyObject?
        SecItemAdd(genQuery as CFDictionary, &newKey)
        return newKey as! SecKey
    }
    
    func encrypt(_ plaintext: Data) -> Data {
        let key = getMasterKey()
        var error: Unmanaged<CFError>?
        let ciphertext = SecKeyCreateEncryptedData(
            key, .eciesEncryptionCofactorX963SHA256AESGCM, plaintext as CFData, &error
        ) as Data? ?? Data()
        return ciphertext
    }
    
    func decrypt(_ ciphertext: Data) -> Data {
        let key = getMasterKey()
        var error: Unmanaged<CFError>?
        let plaintext = SecKeyCreateDecryptedData(
            key, .eciesEncryptionCofactorX963SHA256AESGCM, ciphertext as CFData, &error
        ) as Data? ?? Data()
        return plaintext
    }
}
```

## JVM Implementation (Desktop/Test Only)

```kotlin
// meshlink/src/jvmMain/kotlin/ch/trancee/meshlink/jvm/storage/JvmPeerIdentityStore.kt

class JvmPeerIdentityStore(private val baseDir: File) : PeerIdentityStore {
    private val identityFile = File(baseDir, "peer_identity.bin")
    private val trustStoreFile = File(baseDir, "trust_store.enc")
    private val keysFile = File(baseDir, "local_keys.enc")
    private val masterKey: SecretKey = generateOrLoadMasterKey()
    
    override suspend fun getPeerIdentity(): Result<PeerIdentity> = runCatching {
        if (!identityFile.exists()) return PeerIdentity.generate().also { save(it) }
        PeerIdentity.fromBytes(identityFile.readBytes())
    }
    
    // Uses AES-256-GCM with software key (NOT for production)
    private fun encrypt(data: ByteArray): ByteArray { /* ... */ }
    private fun decrypt(data: ByteArray): ByteArray { /* ... */ }
}
```

## Expect/Actual Declarations

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/storage/PeerIdentityStore.kt
expect class PeerIdentityStore internal constructor() {
    suspend fun getPeerIdentity(): Result<PeerIdentity>
    suspend fun setPeerIdentity(identity: PeerIdentity)
}

// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/storage/TrustStore.kt
expect class TrustStore internal constructor() {
    suspend fun getAll(): Result<Map<PeerIdentity, TrustRecord>>
    suspend fun put(record: TrustRecord)
    suspend fun remove(peer: PeerIdentity)
    suspend fun clear()
}

// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/storage/LocalKeyStore.kt
expect class LocalKeyStore internal constructor() {
    suspend fun getKeys(): Result<LocalIdentityKeys>
    suspend fun setKeys(keys: LocalIdentityKeys)
    suspend fun rotateKeys(newKeys: LocalIdentityKeys)
}
```

## Migration Strategy

| Version | Change | Migration |
|---------|--------|-----------|
| 1 | Initial | — |
| 2 | Add `keyRotationCount` to TrustRecord | Default to 0 |
| 3 | Add `revokedAt` timestamp | Nullable, default null |

Migrations run automatically via DataStore (Android) / manual (iOS) on app startup.

## Security Considerations

| Threat | Mitigation |
|--------|------------|
| App backup exposes keys | `android:allowBackup="false"` / iOS KeyChain `ThisDeviceOnly` |
| Root/jailbreak extracts keys | Hardware-backed keystore (Android Keystore / Secure Enclave) |
| Uninstall/reinstall loses identity | KeyChain persists on iOS; Android KeyStore clears — **identity is regenerated** (acceptable per TOFU model) |
| TrustStore tampering | AEAD encryption + integrity check on decrypt |

## Diagnostics

```yaml
# specs/diagnostic-events.yaml
- name: PeerIdentityGenerated
  fields:
    - identity: PeerIdentity
    - timestamp: Instant
- name: TrustRecordUpdated
  fields:
    - peerIdentity: PeerIdentity
    - state: TrustState
    - keyRotationCount: Int
- name: StorageError
  fields:
    - operation: String
    - error: String
```

## Related

- [Data Model ADR](../model/data-model.md)
- [Trust Model (TOFU)](../../reference/trust-model.md)
- [Crypto Design](../crypto/crypto-design.md)
- [Kotlin Multiplatform Skill](../../../.agents/skills/kotlin-multiplatform/SKILL.md)
