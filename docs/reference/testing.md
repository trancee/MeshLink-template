# Testing & Verification

> **Specification**: [SPEC.md §13](../../SPEC.md#testing--verification)

## Test Suite Structure

| Layer | Location | Coverage |
|-------|----------|----------|
| Unit/JVM | `commonTest` | Full (crypto, routing, transfer, wire codec) |
| Host/Android | `androidHostTest` | Crypto fallback paths |
| Device/Android | `meshlink-proof/android/` | Real BLE behavior |
| Device/iOS | `meshlink-proof/ios/` | Real BLE, Secure Enclave, background modes |
| Reference app | `meshlink-reference` | Public API consumption only |

## Key Requirements

- **Crypto**: Validated against Wycheproof vectors (all primitives)
- **Multi-node**: Virtual mesh harness (no physical hardware in CI)
- **Wire compatibility**: Hex test vectors in `commonTest/resources/wire-compat/`
- **Cross-platform**: Byte-for-byte equality across targets
- **No emulator/simulator BLE tests** — they don't implement real radios

## Acceptance Criteria Per Layer

| Layer | Criteria |
|-------|----------|
| Data Model / Trust | Wire vectors, malformed input rejection |
| Discovery / Advertisement | Two-service-UUID format, peerHint rotation/deduplication, RPA races, and GATT identity resolution |
| Security Contract | Wycheproof vectors, identity binding, appHash isolation, fail-closed on all edge cases |
| Routing Control | Convergence under virtual harness, seqno correctness |
| Chunked Transfer | Fixed 256-chunk sliding SACK window, cut-through relay, adaptive RTO, and retry bounds |
| Power Policy | Mode-to-parameter mapping, EU clamping observable |
| Public API | Identical Android/iOS surface, lifecycle events |

---

## Quick Links

- [SPEC.md §13 — Full testing spec](../../SPEC.md#testing--verification)
- [CONSTITUTION.md §II — Testing Standards](../../CONSTITUTION.md#ii-exhaustive-testing-standards)
- [Module Structure Explanation](../explanation/module-structure.md)
- [Wycheproof Skill](../../.agents/skills/wycheproof/SKILL.md)
