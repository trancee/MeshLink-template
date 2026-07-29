# Transfer Layer

> **Specification**: [SPEC.md §9](../../SPEC.md#transfer-layer)  
> **Design rationale**: [Data Model ADR](../decisions/model/data-model.md)

## Chunked Transfer with SACK

- **Chunk size**: Selected by local `PowerMode` at session start, bounded by peer's advertised MTU
- **SACK bitfield**: Dynamic length = `ceil(totalChunks / 8)` bytes. Bit N = 1 means chunk N received
- **Cut-through relay**: Relays forward chunks before full reassembly (reduces latency)

## Transfer Session Lifecycle

```text
IN_PROGRESS
    ├── all chunks received → COMPLETED
    ├── route lost → WAITING_FOR_ROUTE
    ├── chunk missing → RETRYING
    ├── error/cancel/trust failure → FAILED
    └── retry budget exhausted → TIMED_OUT

WAITING_FOR_ROUTE
    ├── route found → IN_PROGRESS
    └── grace period exhausted → TIMED_OUT

RETRYING
    ├── retransmission complete → IN_PROGRESS
    └── retry budget exhausted → FAILED
```

## Retry Policy

| Parameter | Default | Per PowerMode |
|-----------|---------|---------------|
| Max retries | 5 | HIGH=10, MEDIUM=5, LOW=3 |
| Retry budget | 30s | HIGH=60s, MEDIUM=30s, LOW=15s |
| Backoff | Exponential + jitter | 1s, 2s, 4s... |

## TransferDeliveryOutcome Mapping

| TransferState | FailureReason | Outcome |
|---------------|---------------|---------|
| COMPLETED | — | `success` |
| IN_PROGRESS | — | `in-progress` |
| RETRYING | — | `retrying` |
| WAITING_FOR_ROUTE | — | `route-waiting` |
| TIMED_OUT | — | `timeout` |
| FAILED | Unrecoverable | `unrecoverable-failure` |
| FAILED | TrustFailure | `trust-failure` |

## Wire Frames

| Frame | Type | Encryption | Key Fields |
|-------|------|------------|------------|
| `TRANSFER_CHUNK` | 4 | Link-layer AEAD | sessionId, chunkIndex, offset, length, payload, isLast |
| `TRANSFER_ACKNOWLEDGMENT` | 5 | Link-layer AEAD | sessionId, bitfield (dynamic) |
| `TRANSFER_CANCEL` | 6 | Link-layer AEAD | sessionId, reason |

---

## Quick Links

- [SPEC.md §9 — Full transfer spec](../../SPEC.md#transfer-layer)
- [Data Model ADR](../decisions/model/data-model.md)
- [State Machines Spec](../../specs/state-machines.yaml#transferstate)
- [Wire Frames Spec](../../specs/wire-frames.yaml)
- [Power Mode Spec](power.md)
