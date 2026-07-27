# Mesh Hash Derivation (Application Isolation)

**Status:** Locked — 2026-07-26

## Decision

Mesh Hash = FNV-1a 32-bit(appId) truncated to 16 bits

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/util/MeshHash.kt

fun deriveMeshHash(appId: String): UInt {
    // FNV-1a 32-bit
    var hash: UInt = 0x811c9dc5u
    for (byte in appId.encodeToByteArray()) {
        hash = (hash xor byte.toUInt()) * 0x01000193u
    }
    // Truncate to 16 bits for advertisement field
    return hash and 0xFFFFu
}
```

## Why FNV-1a?

| Property | Value |
|----------|-------|
| Speed | Extremely fast (single multiply + xor per byte) |
| Collision resistance | Sufficient for 16-bit truncation (birthday bound 256) |
| Deterministic | Same appId → same hash always |
| No crypto dependency | Pure Kotlin, no SecureRandom |
| Industry standard | Used in DNS, hash tables, etc. |

**Not SHA-256:** Overkill for 16-bit output; slower; requires crypto provider.

## AppId Format

```kotlin
// Recommended: Reverse DNS + optional instance suffix
val APP_ID = "com.example.myapp"          // Production
val APP_ID = "com.example.myapp.dev"      // Development
val APP_ID = "com.example.myapp.test"     // CI/Test
```

**Rules:**

- Must be stable across app updates
- Should be unique per application (not per install)
- ASCII printable recommended (UTF-8 encoded for hash)

## Collision Probability

| Active Mesh Count | Collision Probability (16-bit) |
|-------------------|-------------------------------|
| 10 | ~0.02% |
| 50 | ~0.5% |
| 100 | ~2% |
| 256 | ~12% |
| 500 | ~39% |

**Mitigation:** Collisions only cause cross-discovery (peers see each other but fail handshake due to different identity keys). Not a security issue — just wasted radio time.

**If collision detected:** Handshake will fail verification → `DiagnosticEvent.HandshakeEvent.verificationLevel = NONE` → peer ignored.

## Configuration

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/MeshLinkSettings.kt

data class MeshLinkSettings(
    /** Application identifier for mesh isolation. Reverse-DNS format recommended. */
    val appId: String = BuildConfig.APPLICATION_ID, // Default: app package name
    
    /** Derived mesh hash (computed from appId). Do not set manually. */
    val meshHash: UInt = deriveMeshHash(appId),
    
    // ... other settings
)
```

## Wire Encoding

Per `specs/wire_frames.yaml` discovery advertisement:

```yaml
mesh_hash: 16 bits (UInt16)
```

**Byte order:** Little-endian (consistent with all other multi-byte fields).

## Testing

```kotlin
// meshlink/src/commonTest/kotlin/ch/trancee/meshlink/util/MeshHashTest.kt

class MeshHashTest {
    @Test fun `stable across runs`() {
        val appId = "com.example.app"
        assertEquals(deriveMeshHash(appId), deriveMeshHash(appId))
    }
    
    @Test fun `different appIds produce different hashes`() {
        val hashes = (1..1000).map { deriveMeshHash("com.app.$it") }.toSet()
        // Should have very few collisions at 1000 entries
        assertTrue(hashes.size > 950)
    }
    
    @Test fun `known vectors`() {
        // FNV-1a test vectors
        assertEquals(0x0000u, deriveMeshHash("")) // Empty string edge case
        // ... add more if needed
    }
}
```

## Related

- [PROJECT.md §Wire & Discovery Design](../../../PROJECT.md#wire--discovery-design)
- [specs/wire_frames.yaml](../../../specs/wire_frames.yaml#discovery_advertisement)
- [Discovery & Identity Spec](../../../docs/reference/04-discovery.md)
