# Wire Format Specification — Reference

For the design decisions rationale, see [`wire-format-contract.md`](wire-format-contract.md).

For complete type definitions, see [SPEC.md](../../../SPEC.md#3-core-data-models).

---

## Quick Reference

| Frame Type | Section | Purpose |
|------------|---------|---------|
| `RoutingFrame` | SPEC.md §3.5 | Wire-level routing container |
| `RouteUpdate` | SPEC.md §3.5, [`routing-metadata-privacy.md`](../routing/routing-metadata-privacy.md) | Encrypted route announcement |
| `RouteWithdrawal` | SPEC.md §3.5, [`routing-metadata-privacy.md`](../routing/routing-metadata-privacy.md) | Encrypted route retraction |
| `RouteDigest` | SPEC.md §8.3 | Table hash for synchronization |
| `TransferChunk` | SPEC.md §3.5 | Payload chunk |
| `TransferAck` | SPEC.md §9.4 | Selective acknowledgment |
| `TransferCancel` | SPEC.md §3.5 | Session termination |
| `KeyRotationAnnouncement` | SPEC.md §5.6 | Key rotation wire format |

---

## Testing Requirements

See SPEC.md §13.7 for complete wire compatibility testing requirements.
