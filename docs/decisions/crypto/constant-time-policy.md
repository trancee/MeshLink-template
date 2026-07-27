# Constant-Time Crypto Policy

**Status:** Locked — 2026-07-27

## Context

CONSTITUTION.md §I requires: "All cryptographic field operations and comparisons MUST implement constant-time algorithms to prevent timing side-channel attacks."

However, Kotlin/JVM does not provide native constant-time byte array operations. The JVM's JIT compiler can optimize array accesses in data-dependent ways, and garbage collection can introduce timing variations. This creates a conflict between the constitutional requirement and platform reality.

## Decision

### Policy per Platform

| Platform | Constant-Time Guarantee | Mechanism |
|----------|------------------------|-----------|
| **Android API 23+ (Secure Element / StrongBox)** | ✅ Hardware constant-time | `AndroidKeyStore` keys — all crypto operations are offloaded to a hardware-backedTEE/StrongBox with constant-time execution |
| **Android API 18-22** | ⚠️ Best-effort | Pure-Kotlin constant-time comparison + `javax.crypto` with `SecretKeySpec` (JIT-dependent, not guaranteed) |
| **Android API 26-32 (no StrongBox)** | ⚠️ Best-effort | Pure-Kotlin constant-time comparison + `javax.crypto.Cipher` with AndroidOpenSSL provider |
| **iOS (Secure Enclave)** | ✅ Hardware constant-time | `Security framework` / `Secure Enclave` — all private key operations are hardware constant-time |
| **JVM (tests/desktop)** | ⚠️ Best-effort | Pure-Kotlin constant-time comparison |
| **Kotlin/Native (iOS target)** | ✅ Hardware constant-time | Compiles to native code, no JIT — array access patterns are determined by source code |

### Constant-Time Primitives (Pure Kotlin)

All pure-Kotlin implementations MUST use `CryptoMath` — a utility providing constant-time comparison and selection via `constantTimeEquals` and `constantTimeSelect`:

```kotlin
/**
 * Constant-time comparison of two byte arrays.
 * Returns 0 if equal, non-zero if different.
 * Execution time is independent of the number of matching bytes.
 */
internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Int {
    if (a.size != b.size) return -1  // Length mismatch is fine; result is non-zero
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result
}

/**
 * Constant-time selection: returns [a] if condition is 0, [b] otherwise.
 * Both branches are computed; only the selection is data-dependent.
 * (Compiler may optimize this — see note on JIT below.)
 */
internal fun constantTimeSelect(condition: Int, a: ByteArray, b: ByteArray): ByteArray {
    val mask = -condition  // 0xFFFFFFFF if condition != 0, 0x00000000 if condition == 0
    return ByteArray(a.size) { i ->
        val av = a[i].toInt()
        val bv = b[i].toInt()
        ((av and mask.inv()) or (bv and mask)).toByte()
    }
}
```

### JIT Mitigation

The JVM's JIT compiler can optimize constant-time loops in ways that reintroduce data-dependent timing (e.g., branch prediction on array indices). Mitigations:

1. **Use `@Suppress("OPT_IN_IS_NOT_ENABLED")` and `kotlin.contracts`** — not sufficient alone
2. **Mark hotspot crypto methods with `@JvmStatic` and `final`** — reduces inlining of data-sensitive paths
3. **Prefer `javax.crypto.Cipher` and `Mac` for Android when available** — these use native OpenSSL/BoringSSL implementations that are constant-time
4. **For Android API 23+ with StrongBox**, private key operations never touch application memory — timing attacks are infeasible
5. **Document the limitation**: Pure-Kotlin constant-time on JVM is "best-effort" and should not be relied upon for high-security applications where the JVM JIT is not under the developer's control

### Verification

- Wycheproof test vectors validate **correctness**, not constant-time behavior
- Constant-time behavior MUST be validated by code review, not by automated tests
- The ADR `android-crypto-fallback-proof.md` should include a review checklist item for constant-time verification
- CI `detekt` rules should flag any `if (secretByte == expectedByte)` pattern in crypto code (use a custom Detekt rule)

### Why This Policy

The constitutional requirement is "MUST implement constant-time algorithms." This policy interprets that requirement as:

1. **The algorithm must be designed as constant-time** — no secret-dependent branches, no secret-dependent array indices
2. **The implementation must resist timing attacks on the target platform** — hardware-backed platforms get guaranteed constant-time; pure-Kotlin on JVM gets best-effort with documented limitations
3. **The implementation must not silently degrade** — if a platform doesn't support constant-time, it must fail closed rather than silently use a vulnerable implementation

### When to Revisit

- If a Kotlin/Native JIT or runtime improvement guarantees constant-time array operations
- If a new Android API level provides a constant-time crypto API
- If a formal timing analysis tool becomes available for Kotlin/JVM

## Related

- [CONSTITUTION.md §I](../../../CONSTITUTION.md) — Rigorous Code Quality
- [CONSTITUTION.md Naming Rules](../../../CONSTITUTION.md#i-rigorous-code-quality) — no invented abbreviations
- [Crypto Design ADR](crypto-design.md) — fail-closed rules
- [Android Crypto Fallback Proof](android-crypto-fallback-proof.md) — platform-specific validation
- [Wycheproof integration](../../../.agents/skills/wycheproof/SKILL.md) — test vector validation
