# Transport Layer

> **Specification**: [SPEC.md §6](../../SPEC.md#transport-layer)  
> **Design rationale**: [MTU Negotiation](../decisions/transport/mtu-negotiation.md)

## Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None — GATT always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Control plane MUST work over GATT alone** for reliability.

## Negotiation Sequence

1. GATT connection establishes
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane)
3. If both peers advertised PSM hint, attempt L2CAP CoC channel
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

## Fallback Reasons (Machine Observable)

| Reason | Description |
|--------|-------------|
| `NO_PSM_ADVERTISED` | Peer didn't advertise PSM in discovery |
| `L2CAP_CONNECT_FAILED` | CoC connection failed |
| `L2CAP_DROPPED_MID_TRANSFER` | CoC channel dropped during transfer |
| `LOCAL_POLICY` | Local configuration disabled CoC |

## MTU & Chunk Size

| Power Mode | Chunk Size | Min MTU Required |
|------------|------------|------------------|
| HIGH | 512 B | 515 |
| MEDIUM | 256 B | 259 |
| LOW | 128 B | 131 |

**Rule**: If negotiated MTU < `chunkSize + 3` (ATT header), reduce chunk size for that session.

---

## Quick Links

- [SPEC.md §6 — Full transport spec](../../SPEC.md#transport-layer)
- [MTU Negotiation ADR](../decisions/transport/mtu-negotiation.md)
- [Power Mode Spec](power.md)
