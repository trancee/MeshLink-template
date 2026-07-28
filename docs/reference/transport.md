# Transport Layer

> Source: [SPEC.md §6](../../SPEC.md#6-transport-layer)

## 6.1 Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None - GATT is always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Important:** Control plane (handshake, routing, transfer control) MUST work over GATT alone for reliability.

[Decision: docs/decisions/transport/mtu-negotiation.md]

## 6.2 Negotiation Sequence

1. GATT connection establishes
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane must work over GATT alone)
3. If both peers advertised PSM hint, attempt L2CAP CoC channel
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

## 6.3 Fallback Reasons (Machine Observable)

- `transport.fallback_no_psm_advertised`
- `transport.fallback_coc_connect_failed`
- `transport.fallback_coc_dropped_mid_transfer`
- `transport.fallback_local_policy`
