# Transport Layer

> **Specification**: [SPEC.md §6](../../SPEC.md#6-transport-layer)  
> **Design rationale**: [MTU Negotiation](../decisions/transport/mtu-negotiation.md)

## Bearer Selection

| Traffic Type | Preferred Bearer | Fallback |
|--------------|------------------|----------|
| Control plane | GATT (unconditionally) | None — GATT always available |
| Data plane | L2CAP CoC | GATT with same correctness guarantees |

**Control plane MUST work over GATT alone** for reliability.

## GATT Service

| Characteristic | UUID | Properties |
|----------------|------|------------|
| Metadata | `4D455348-0001-1000-8000-00805F9B34FB` | read |
| Channel | `4D455348-0002-1000-8000-00805F9B34FB` | write, write-without-response, notify, indicate |

The service UUID is the private unassigned `0x4D455348` marker. Control uses
write-with-response/indication; fallback data uses
write-without-response/notification plus SACK. Channel subscription must be
confirmed before Noise begins.

## Negotiation Sequence

1. GATT connection establishes
2. `Noise_XX_25519_ChaChaPoly_SHA256` handshake completes (control plane)
3. After Noise validates GATT metadata, attempt L2CAP when its 16-bit PSM is valid/non-zero; advertised capability is only a hint
4. On CoC success, promote data-plane traffic to CoC
5. On CoC failure, continue on GATT

## Fallback Reasons (Machine Observable)

| Reason | Description |
|--------|-------------|
| `L2CAP_UNAVAILABLE` | No usable L2CAP capability/PSM after metadata validation |
| `L2CAP_CONNECT_FAILED` | CoC connection failed at the BLE link layer |
| `L2CAP_OPEN_TIMEOUT` | L2CAP channel open attempt timed out before connection completed |
| `L2CAP_STREAM_ERROR` | L2CAP CoC stream error (EOF or channel-level stream failure) |
| `L2CAP_STALLED` | L2CAP CoC stream stalled — no progress within partial-frame timeout |
| `L2CAP_DROPPED_MID_TRANSFER` | CoC channel dropped during transfer |
| `LOCAL_POLICY` | Local configuration disabled CoC |

## Bearer Framing

GATT uses connection-local strict fragments:

```text
index: UShort
if index == 0: totalLength: UShort
payload
```

Maximum frame length is 65,535 bytes and pre-auth frames are limited to 4 KiB.
Each `(TransportHandle, generation)` context has independent reassembly, so
concurrent peers may all send index zero safely. L2CAP uses a little-endian
UShort length prefix around the same MeshLink frame bytes.

## L2CAP Health and GATT Fallback

L2CAP support is a capability, not routing quality. Each authenticated adjacent
peer tracks process-local channel health. Open failure, timeout, EOF, stream
error, stall, partial-frame timeout, or channel drop moves new data assignment
to GATT immediately while preserving route, trust, E2E session, and transfer
state. SACK retransmits missing chunks.

Backoff is 15–30 seconds, 1–2 minutes, then 5–10 minutes. A fourth failure
disables L2CAP for the process lifetime. Failure history clears only after ten
healthy minutes or one error-free transfer of at least 1 MiB.

## MTU & Chunk Size

| Power Mode | Chunk Size | Min MTU Required |
|------------|------------|------------------|
| HIGH | 512 B | 515 |
| MEDIUM | 256 B | 259 |
| LOW | 128 B | 131 |

**Rule**: If negotiated MTU < `chunkSize + 3` (ATT header), reduce chunk size for that session.

---

## Background Operation

`MeshLinkSettings.enableBackground` defaults to false. When enabled, Android
host apps own the connected-device foreground service and notification; iOS
host apps own Bluetooth background declarations and restoration forwarding.
MeshLink owns BLE state and reconstruction. Execution is best effort, and active
transfers/traffic keys do not survive process death or force-stop/force-quit.

## Quick Links

- [SPEC.md §6 — Full transport spec](../../SPEC.md#6-transport-layer)
- [GATT Channel and Framing ADR](../decisions/transport/gatt-channel-and-framing.md)
- [MTU Negotiation ADR](../decisions/transport/mtu-negotiation.md)
- [Background Operation ADR](../decisions/transport/background-operation.md)
- [Power Mode Spec](power.md)
