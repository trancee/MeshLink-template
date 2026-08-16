# MeshLink-crypto API Reference

> **Dependency**: `ch.trancee.meshlink:meshlink-crypto:0.1.1` (Maven Central, via `libs.meshlink.crypto`)
> **Source**: [GitHub — trancee/MeshLink-crypto](https://github.com/trancee/MeshLink-crypto)
> **ADR**: [meshlink-crypto dependency](../decisions/crypto/meshlink-crypto-dependency.md)
> **API verified**: source code at `MeshLink-crypto/crypto/src/commonMain/` + published sources JAR

This guide documents the `meshlink-crypto` v0.1.1 public API surface as consumed
by the `:meshlink` module. It supplements the external API reference with
local examples for Noise key-pair generation, HKDF key derivation, X25519 DH,
Ed25519 signing, and ChaCha20-Poly1305 AEAD encryption.

## What is shipped in the artifact

The Maven Central artifact `ch.trancee.meshlink:meshlink-crypto:0.1.1` publishes
the following per target (`jvm`, `android`, `iosArm64`):

| Artifact suffix | Content |
|---|---|
| `-sources.jar` | Full Kotlin source (commonMain + platform actuals) — **attached**. IDE source-step works. |
| `-javadoc.jar` | **Fixed in v0.1.1** — contains full Dokka HTML for all public declarations plus Markdown API docs from `docs/reference/` and `docs/how-to/`. IDE "View Documentation" works. |

**Note**: The v0.1.0 `-javadoc.jar` was empty (261 bytes, manifest only).
The `javadocJarJvm` task now uses `from(layout.buildDirectory.dir("dokka/html"))`
with an explicit `dependsOn("dokkaGenerateHtml")` instead of the broken
`from(tasks.named("dokkaGenerateHtml"))` that resolved to an empty file-set.

## API Surface

`meshlink-crypto` exposes **three tiers** of public API on `commonMain`:

1. **`Crypto`** — a Kotlin `object` (singleton) providing all primitives via a
   unified facade. All methods return `Result<T>`. Recommended entry point.
2. **Per-primitive facade objects** — `Hasher`, `Kdf`, `Authenticator`,
   `KeyExchange`, `Signer`, `Aead` — each a public `object` with the same
   `Result<T>`-returning methods. Use these when you need a single primitive
   in isolation; the `Crypto` object delegates to them with zero overhead.
3. **Top-level functions** — `randomBytes(size: Int): ByteArray`.

The internal dispatch layer (`Ed25519`, `X25519`, `ChaCha20Poly1305`, `SHA256`,
`SHA512`, `HMAC_SHA256`, `HKDF_SHA256`) is `internal expect/actual` and is NOT
part of the public API. Platform-native dispatch (JCA, CryptoKit,
Security.framework) is selected at runtime via an injected `CryptoProvider`.

### Key handle types

`meshlink-crypto` provides three `AutoCloseable` key handle classes that wrap
raw byte arrays with zeroization on `close()`:

```kotlin
public class SecretKey(@Secret private val material: ByteArray) : AutoCloseable
public class PrivateKey(@Secret private val material: ByteArray) : AutoCloseable
public class PublicKey(private val material: ByteArray) : AutoCloseable
```

Each exposes a `bytes` property returning a defensive copy:

```kotlin
val bytes: ByteArray  // via material.copyOf()
```

**Important**: Constructors store a **reference**, not a copy. To prevent
accidental mutation of key material, pass a defensive copy:

```kotlin
val privateKey = PrivateKey(identityKey.toByteArray().copyOf())
```

**Important**: `SecretKey`, `PublicKey`, and `PrivateKey` are **not**
type-safe — a 32-byte Ed25519 private key and a 32-byte X25519 private key
are interchangeable at the type level. The caller must track which curve
each key belongs to.

### `@Secret` annotation

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class Secret
```

Marks parameters carrying cryptographic secrets. The
`:crypto-detekt-rules` `ConstantTimeRule` statically bans data-dependent
branches or secret-dependent array indices on `@Secret`-annotated values.

### Module version

```kotlin
public fun moduleVersion(): String  // returns "0.1.0" (note: not yet bumped to "0.1.1")
```

---

## `Crypto` object (unified facade)

```kotlin
// Random byte generation
Crypto.randomBytes(size: Int): ByteArray                              // CSPRNG, e.g. 32 bytes for key seed

// Hashing
Crypto.sha256(message: ByteArray): Result<ByteArray>                   // 32-byte digest
Crypto.sha512(message: ByteArray): Result<ByteArray>                   // 64-byte digest

// HMAC-SHA256 (RFC 2104)
Crypto.hmacSha256(key: SecretKey, message: ByteArray): Result<ByteArray>           // 32-byte tag
Crypto.verifyHmacSha256(key: SecretKey, message: ByteArray, tag: ByteArray): Result<Boolean>

// HKDF-SHA256 (RFC 5869) — parameter order: ikm, salt
Crypto.hkdfSha256(
    ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int,
): Result<ByteArray>
Crypto.extract(ikm: ByteArray, salt: ByteArray): Result<ByteArray>                      // HKDF-Extract → 32-byte PRK
Crypto.expand(prk: ByteArray, info: ByteArray, outputLength: Int): Result<ByteArray>    // HKDF-Expand → OKM

// X25519 (RFC 7748 §5)
Crypto.x25519(scalar: PrivateKey, u: PublicKey): Result<ByteArray>                      // 32-byte shared secret
Crypto.deriveX25519PublicKey(privateKey: PrivateKey): Result<ByteArray>                // 32-byte public u-coordinate

// Ed25519 (RFC 8032 §5.1)
Crypto.ed25519Sign(secretKey: PrivateKey, message: ByteArray): Result<ByteArray>      // 64-byte signature
Crypto.ed25519Verify(publicKey: PublicKey, message: ByteArray, signature: ByteArray): Result<Boolean>
Crypto.ed25519PublicKeyFromPrivate(secretKey: PrivateKey): Result<ByteArray>          // 32-byte public key

// ChaCha20-Poly1305 (RFC 8439) — nonce is internal, prepended to output
Crypto.chacha20Poly1305Encrypt(key: SecretKey, message: ByteArray): Result<ByteArray>  // nonce(12)||ciphertext||tag(16)
Crypto.chacha20Poly1305Decrypt(key: SecretKey, ciphertext: ByteArray): Result<ByteArray?>  // null on auth failure
```

**Important**: `sha256` and `sha512` take a raw `ByteArray` input (no key handle).
HMAC and AEAD methods take a `SecretKey` handle. HKDF methods take raw `ByteArray`
inputs for IKM, salt, info, PRK.
**Parameter order for KDF is `ikm, salt`** (NOT `salt, ikm`).

---

## Per-primitive facade objects

Each object below mirrors the corresponding `Crypto` method — the facade is
a zero-overhead delegation with no added logic.

### `Hasher` (message digests)

```kotlin
Hasher.sha256(message: ByteArray): Result<ByteArray>   // 32 bytes
Hasher.sha512(message: ByteArray): Result<ByteArray>   // 64 bytes
```

### `Kdf` (HKDF-SHA256)

Parameter order: `ikm, salt` — **not** `salt, ikm`.

```kotlin
Kdf.hkdfSha256(
    ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int,
): Result<ByteArray>
Kdf.extract(ikm: ByteArray, salt: ByteArray): Result<ByteArray>           // → 32-byte PRK
Kdf.expand(prk: ByteArray, info: ByteArray, outputLength: Int): Result<ByteArray>  // → OKM
```

### `Authenticator` (HMAC-SHA256)

```kotlin
Authenticator.hmacSha256(key: SecretKey, message: ByteArray): Result<ByteArray>
Authenticator.verify(key: SecretKey, message: ByteArray, tag: ByteArray): Result<Boolean>
```

### `KeyExchange` (X25519)

```kotlin
KeyExchange.x25519(scalar: PrivateKey, u: PublicKey): Result<ByteArray>
KeyExchange.deriveX25519PublicKey(privateKey: PrivateKey): Result<ByteArray>
```

### `Signer` (Ed25519)

```kotlin
Signer.ed25519Sign(secretKey: PrivateKey, message: ByteArray): Result<ByteArray>      // 64-byte signature
Signer.ed25519Verify(publicKey: PublicKey, message: ByteArray, signature: ByteArray): Result<Boolean>
Signer.ed25519PublicKeyFromPrivate(secretKey: PrivateKey): Result<ByteArray>
```

### `Aead` (ChaCha20-Poly1305)

```kotlin
Aead.chacha20Poly1305Encrypt(key: SecretKey, message: ByteArray): Result<ByteArray>
Aead.chacha20Poly1305Decrypt(key: SecretKey, ciphertext: ByteArray): Result<ByteArray?>
```

### Top-level functions

```kotlin
randomBytes(size: Int): ByteArray  // CSPRNG — use for key generation, nonces, etc.
```

---

## `CryptoProvider` interface (platform dispatch)

Optional platform-native crypto provider (CryptoKit, JCA, or Security.framework).
Inject at app startup:

```kotlin
setCryptoProvider(provider: CryptoProvider?)  // pass null to clear
```

When no provider is set (or a provider returns `null` for a primitive), the
library falls back to its native path (CommonCrypto / Security.framework on
iOS, JCA on JVM/Android) or the pure-Kotlin implementation.

```kotlin
public interface CryptoProvider {
    fun supportsX25519(): Boolean
    fun x25519(scalar: ByteArray, u: ByteArray): ByteArray?
    fun x25519PublicKeyFromPrivate(scalar: ByteArray): ByteArray?

    fun supportsEd25519(): Boolean
    fun ed25519PublicKeyFromPrivate(secretKey: ByteArray): ByteArray?
    fun ed25519Sign(secretKey: ByteArray, message: ByteArray): ByteArray?
    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean?

    fun supportsChaCha20Poly1305(): Boolean
    fun chacha20Poly1305Encrypt(
        key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray,
    ): ByteArray?
    fun chacha20Poly1305Decrypt(
        key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertextWithTag: ByteArray,
    ): ByteArray?
}
```

**Notes**:

- All `CryptoProvider` methods return nullable types (`ByteArray?`, `Boolean?`).
  Implementations that cannot handle a primitive should return `null` — the
  library then falls back.
- `chacha20Poly1305Decrypt` receives `ciphertext || tag(16)` (without nonce).
  `chacha20Poly1305Encrypt` receives the nonce separately.
- Parameter names differ from the facade: `scalar`/`u` (not `privateKey`/`publicKey`);
  `ciphertextWithTag` (not `ciphertext`).
- `x25519PublicKeyFromPrivate` was added in v0.1.1 — returns the 32-byte public
  u-coordinate from a 32-byte private scalar.

---

## Usage: Key Pair Generation + Noise XX Handshake

SPEC §5.1 — neither peer starts with a trusted remote key. v0.1.1's public
`deriveX25519PublicKey` / `ed25519PublicKeyFromPrivate` / `randomBytes` make
this possible without a `CryptoProvider`.

```kotlin
import ch.trancee.meshlink.crypto.*
import kotlin.random.Random

// Generate local static Ed25519 key pair
val localStaticPrivBytes = Random.Default.nextBytes(ByteArray(32))
val localStaticPriv = PrivateKey(localStaticPrivBytes.copyOf())
val localStaticPub = PublicKey(
    Signer.ed25519PublicKeyFromPrivate(localStaticPriv).getOrThrow()
)

// Generate local ephemeral X25519 key pair (per-handshake)
val localEphPrivBytes = Random.Default.nextBytes(ByteArray(32))
val localEphemeralPriv = PrivateKey(localEphPrivBytes.copyOf())
val localEphemeralPub = PublicKey(
    KeyExchange.deriveX25519PublicKey(localEphemeralPriv).getOrThrow()
)

// --- Message 1 (initiator → responder): e ---
// Send localEphemeralPub.bytes over the wire

// --- Message 2 (responder → initiator): e, ee, se ---
val remoteEphemeralPub = PublicKey(receivedEphemeralPub)
val responderEphemeralPrivBytes = Random.Default.nextBytes(ByteArray(32))
val responderEphemeralPriv = PrivateKey(responderEphemeralPrivBytes.copyOf())
val responderStaticPrivBytes = Random.Default.nextBytes(ByteArray(32))
val responderStaticPriv = PrivateKey(responderStaticPrivBytes.copyOf())

// DH: ephemeral-ephemeral (both sides compute this)
val dh_ee = KeyExchange.x25519(responderEphemeralPriv, remoteEphemeralPub).getOrThrow()

// DH: responder static - initiator ephemeral (se)
val dh_se = KeyExchange.x25519(responderStaticPriv, remoteEphemeralPub).getOrThrow()

// Responder sends its static public key
val responderStaticPub = PublicKey(
    Signer.ed25519PublicKeyFromPrivate(responderStaticPriv).getOrThrow()
)

// --- Message 3 (initiator → responder): e, ee, se ---
// Initiator already has responderEphemeralPub and responderStaticPub from msg2
val dh_ee_init = KeyExchange.x25519(
    localEphemeralPriv, PublicKey(receivedResponderEphemeralPub)
).getOrThrow()

val dh_se_init = KeyExchange.x25519(
    localStaticPriv, PublicKey(receivedResponderEphemeralPub)
).getOrThrow()

// --- Key derivation ---
// Chain key = HKDF-Extract(ikm, salt) — ikm FIRST, salt SECOND
val chainKey = Kdf.extract(
    ikm = dh_ee + dh_se + dh_ee_init + dh_se_init,
    salt = ByteArray(32) { 0 },
).getOrThrow()
val cipherKey = Kdf.expand(
    prk = chainKey,
    info = "extraction".encodeToByteArray(),
    outputLength = 32,
).getOrThrow()
val secretKey = SecretKey(cipherKey)
```

> **Note**: This is a simplified illustration. The actual Noise XX pattern per
> SPEC §5 uses a single ordered DH combination chain, not independent DHs. See
> `state-machines.yaml` `TransferState` for the precise message ordering and
> `crypto-design.md` for the chaining-KDF details.

---

## Usage: Noise IK Handshake (Reconnect)

SPEC §5.2 — initiator knows the responder's trusted X25519 static key.

```kotlin
import ch.trancee.meshlink.crypto.*

val responderTrustedPub = PublicKey(loadTrustedStaticPub())
val localStaticPriv = PrivateKey(loadLocalStaticPriv().copyOf())
val localEphemeralPriv = PrivateKey(Crypto.randomBytes(32).copyOf())

// Derive ephemeral public key
val localEphemeralPub = KeyExchange.deriveX25519PublicKey(localEphemeralPriv).getOrThrow()

// --- Message 1 (initiator → responder): e ---
// DH: initiator-ephemeral * responder-static (es)
val dh_es = KeyExchange.x25519(localEphemeralPriv, responderTrustedPub).getOrThrow()
// Send PublicKey(localEphemeralPub) over the wire

// --- Message 2 (responder → initiator): e, ee ---
val initiatorEphemeralPub = PublicKey(receivedEphemeralPub)
val responderEphemeralPriv = PrivateKey(Crypto.randomBytes(32).copyOf())

// DH: responder-ephemeral * initiator-ephemeral (ee)
val dh_ee = KeyExchange.x25519(responderEphemeralPriv, initiatorEphemeralPub).getOrThrow()
// DH: responder-static * initiator-ephemeral (se)
val dh_se = KeyExchange.x25519(localStaticPriv, initiatorEphemeralPub).getOrThrow()

// --- Key derivation ---
val chainKey = Kdf.extract(
    ikm = dh_es + dh_ee + dh_se,
    salt = ByteArray(32) { 0 },
).getOrThrow()
```

---

## Usage: HKDF Chaining

```kotlin
// Noise uses HKDF-SHA256 for chain key derivation.
// chainKey = HKDF-Extract(ikm, salt) — ikm FIRST, salt SECOND
val chainKey = Kdf.extract(ikm = dhOutput, salt = ByteArray(32) { 0 }).getOrThrow()

// cipherKey = HKDF-Expand(prk, info, outputLength)
val cipherKey = Kdf.expand(
    prk = chainKey,
    info = "client cipher key".encodeToByteArray(),
    outputLength = 32,
).getOrThrow()
val secretKey = SecretKey(cipherKey)
```

---

## Usage: AEAD Encrypt/Decrypt

```kotlin
// Encrypt — nonce is generated internally, prepended to output
val secretKey = SecretKey(keyBytes.copyOf())
val encrypted = Crypto.chacha20Poly1305Encrypt(secretKey, plaintext).getOrThrow()
// encrypted = nonce(12) || ciphertext || tag(16)

// Decrypt — pass the full encrypted blob (nonce is stripped internally)
val decrypted = Crypto.chacha20Poly1305Decrypt(secretKey, encrypted).getOrThrow()
// Returns null inside Result.success if the auth tag fails
if (decrypted == null) {
    throw IllegalArgumentException("AEAD tag verification failed")
}
```

---

## Usage: Random Key Generation

v0.1.1 promotes `randomBytes` from `internal` to public. Use the facade method:

```kotlin
val privateKeySeed = Crypto.randomBytes(32)   // 32-byte X25519/Ed25519 seed
val symmetricKey = Crypto.randomBytes(32)     // 256-bit ChaCha20-Poly1305 key
```

---

## Documentation availability

| Documentation | Where | In Maven artifact? |
|---|---|---|
| KDoc on every public declaration | In sources JAR (`-sources.jar`) | ✅ Yes |
| Javadoc HTML | In `-javadoc.jar` (fixed in v0.1.1) | ✅ Yes |
| Markdown API docs | Bundled in `-javadoc.jar` (from `docs/reference/`, `docs/how-to/`) | ✅ Yes (v0.1.1+) |
| API reference (this document) | `docs/reference/meshlink-crypto-api.md` in template repo | ❌ No |
| ADR-0005: API surface | [GitHub — MeshLink-crypto](https://github.com/trancee/MeshLink-crypto/blob/v0.1.1/docs/adr/0005-api-surface.md) | ❌ No |
| ADR-0007: Build quality toolchain | [GitHub — MeshLink-crypto](https://github.com/trancee/MeshLink-crypto/blob/v0.1.1/docs/adr/0007-build-quality-toolchain.md) | ❌ No |

**Recommendation**: The v0.1.1 artifact ships both a sources JAR (for IDE source
stepping) and a Javadoc JAR (for IDE documentation view). AI implementors can
also parse the bundled Markdown docs from the `-javadoc.jar` directly, or
refer to this document for the MeshLink-template-specific API contract.
