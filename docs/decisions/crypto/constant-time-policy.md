# Constant-Time Crypto Policy

**Status:** Locked — 2026-07-27 (Updated 2026-07-27)

> Normative requirements and algorithm list live in [SPEC.md §7.4](../../../SPEC.md#constant-time) and implementation in [ConstantTime.kt](../../../meshlink/src/commonMain/kotlin/ch/trancee/meshlink/util/ConstantTime.kt). This record explains the design rationale.

## Why per-platform constant-time guarantees differ

**Decision:** Platform guarantees vary: Android StrongBox/Secure Enclave/iOS hardware = guaranteed constant-time; Android API 26-32 without StrongBox / JVM desktop = best-effort pure-Kotlin.

**Rationale:** Hardware-backed keystores (StrongBox, Secure Enclave) execute crypto operations in isolated TEE/hardware with constant-time execution as a hardware property. Pure-Kotlin on JVM cannot guarantee constant-time because the JIT compiler may optimize array accesses in data-dependent ways and garbage collection introduces timing variations. The policy honestly documents these differences rather than claiming false guarantees.

## Why pure-Kotlin ConstantTime utility with specific primitives

**Decision:** Provide `constantTimeEquals`, `constantTimeSelect`, `constantTimeSwap`, `constantTimeIsZero` as the core constant-time primitives.

**Rationale:** These four primitives cover all crypto algorithm needs:

- Comparison (MAC verification, key equality)
- Selection (constant-time branch-free conditional)
- Swap (constant-time conditional exchange)
- Zero check (constant-time secret clearing)

The `mask = -(condition or -condition ushr 31)` pattern produces `0xFFFFFFFF` for any non-zero condition and `0` for zero, handling positive, negative, and large-magnitude conditions branch-free. Size-mismatch handling in `constantTimeEquals` iterates both arrays to the longer length, folding size XOR into the result so timing depends only on `max(a.size, b.size)`.

## Why best-effort on JVM with documented limitations

**Decision:** Pure-Kotlin on JVM is documented as "best-effort" constant-time, not guaranteed.

**Rationale:** The JVM JIT can reintroduce data-dependent timing through branch prediction, loop unrolling, and escape analysis. The policy does not claim false guarantees. Instead it mandates: (1) algorithm designed as constant-time (no secret-dependent branches/indices), (2) implementation resists timing attacks on target platform, (3) no silent degradation — platforms without constant-time support fail closed rather than silently using vulnerable implementations.

## Why JIT mitigations are best-practice, not requirements

**Decision:** Mitigations (`@JvmStatic`, `final`, preferring `javax.crypto` on Android) are recommended, not required.

**Rationale:** These mitigations reduce but don't eliminate JIT timing variation. They're best-practice because they help without adding complexity. The fundamental limitation (JIT unpredictability) remains; mitigations are a layer of defense, not a solution.

## Why verification by code review, not automated tests

**Decision:** Constant-time behavior MUST be validated by code review; Wycheproof validates correctness only.

**Rationale:** Automated timing tests on JVM are unreliable due to JIT/GC noise. A custom Detekt rule flags secret-dependent patterns (`if (secretByte == expectedByte)`), but this is a static analysis aid, not a proof. Code review by security-aware engineers is the only practical validation.

## Why fail closed on unsupported platforms

**Decision:** If a platform doesn't support constant-time for a required primitive, the operation fails closed.

**Rationale:** Silent fallback to variable-time implementation is a security vulnerability. Explicit failure forces the developer to address the gap (use hardware-backed keys, upgrade platform, or accept the risk with informed consent).

## Related

- [CONSTITUTION.md §I](../../../CONSTITUTION.md#i-rigorous-code-quality) — Rigorous Code Quality
- [Crypto Design ADR](crypto-design.md) — fail-closed rules
- [Wycheproof integration](../../../.agents/skills/wycheproof/SKILL.md) — test vector validation
- [SPEC.md §7.4](../../../SPEC.md#constant-time)
- [ConstantTime.kt](../../../meshlink/src/commonMain/kotlin/ch/trancee/meshlink/util/ConstantTime.kt)
