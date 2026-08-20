# Identity binding and fail-closed behavior

**Status:** Locked — 2026-07-31

> The normative handshake and failure rules live in
> [SPEC.md §§5 and 7](../../../SPEC.md#5-trust-model-tofu). This decision record
> captures the design rationale only.

## Key generation hint

`keyGeneration` is the current long-term Ed25519/X25519 generation for a stable
PeerIdentity. GATT exposes it as an untrusted hint: equal to the pinned
generation selects IK; a higher value selects rotation recovery; a lower,
malformed, or ambiguous value fails closed. It never changes trust, replaces a
binding, or proves a rotation without the contiguous dual-signed proof chain and
successful Noise authentication.

**Rationale:** Separating key generation from identity allows key rotation without
breaking peer recognition. The hint is untrusted because only the dual-signed
rotation proof establishes continuity. An attacker could advertise any generation
value; we only trust it after cryptographic validation.

## Application hash (appHash)

`appHash` is the 128-bit application isolation identifier derived from the `appId`
configured in `MeshLinkSettings`. It is never derived from peer identity or keys:

```text
appHash = first128Bits(SHA-256("MeshLink app-id v1" || UTF8(appId)))
```

In Kotlin, `AppHash` is a `@JvmInline value class` backed by `Pair<ULong, ULong>`
(two 64-bit limbs), matching the `PeerIdentity` representation pattern. The
`Pair<ULong, ULong>` backing:

- **JVM**: `@JvmInline` provides zero-allocation value semantics — no `ByteArray`
  object is heap-allocated when an `AppHash` is created or passed to functions.
- **Kotlin/Native (iOS)**: `ULong` is a 64-bit primitive stored directly in the
  `Pair` object — no boxing occurs. A `ByteArray` would allocate a separate array
  object with header overhead; `Pair<ULong, ULong>` avoids that.
- **Android**: Same as JVM via `@JvmInline`.

`AppHash.toBytes()` yields a 16-byte big-endian representation for canonical
wire field encoding. Two MeshLink instances with different `appId`
values produce different `appHash` values and **never** interoperate, even if they
share the same `meshHash`.

**Rationale:** The 128-bit width prevents birthday-scale collision attacks that
a 16-bit `meshHash` cannot. Using the same `Pair<ULong, ULong>` backing as
`PeerIdentity` keeps the encoding consistent across all 128-bit identity-class
values and avoids platform-specific `ByteArray` allocation patterns in
cross-platform code.

## What fail closed does not mean

- It does not require crashing the process.
- It does not require stopping unrelated peers when one frame is invalid.
- It does not prohibit GATT fallback when L2CAP fails, because both bearers keep
  the same application-layer authentication and encryption guarantees.
- It does not prohibit bounded retries in the same security mode.
- It does not permit availability pressure to weaken authentication, integrity,
  replay protection, or trust continuity.

**Rationale:** Fail closed is about *containment*, not *denial of service*. The
smallest safe scope principle means a bad frame affects only that frame's
operation, not the entire mesh. GATT fallback is permitted because it maintains
identical security properties — it's a transport-layer change, not a security
downgrade.

## Fallback requirements

Every fallback must be specified before implementation and must:

1. Preserve the security properties required by the operation.
2. Have an explicit trigger and bounded retry/timeout behavior.
3. Emit a machine-observable reason.
4. Avoid changing trust state merely because the preferred path failed.
5. Have success, failure, downgrade, and recovery tests.

**Rationale:** Unspecified fallbacks are protocol vulnerabilities. By requiring
explicit specification, bounded behavior, observability, trust-state preservation,
and comprehensive testing, we ensure fallbacks don't silently weaken security.
The "no trust state change on fallback" rule prevents an attacker from forcing
a downgrade by causing preferred-path failures.

## Testing requirements

Tests cover each validation step independently and prove that failure occurs
before trust, routing, transfer, or persistence mutation. Integration tests also
prove:

- a 16-bit `meshHash` collision cannot cross the `appHash` boundary;
- key and hint rotation preserve the public `PeerIdentity`;
- pinned-key mismatch never falls back to XX;
- malformed metadata cannot allocate durable trust state;
- failed runtime reconfiguration preserves previous effective values; and
- diagnostics contain typed reasons but no secret or plaintext material.

**Rationale:** These tests encode the security invariants. The meshHash/appHash
boundary test proves the 16-bit filter cannot bypass the 128-bit isolation. The
rotation test ensures identity stability. The pinned-mismatch test enforces
fail-closed. The metadata test proves no trust allocation on bad input. The
reconfiguration test ensures graceful degradation. The diagnostics test proves
no secret leakage.

## Related

- [Crypto design](crypto-design.md)
- [Connectable advertisement](../discovery/connectable-advertisement.md)
- [Data model](../model/data-model.md)
- [CONSTITUTION.md Principle I](../../../CONSTITUTION.md#i-rigorous-code-quality)
- [Noise Protocol Framework skill](../../../.agents/skills/noise-protocol-framework/SKILL.md)
