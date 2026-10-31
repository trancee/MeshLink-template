# Experimental BLE spike contract

This contract belongs only to `meshlink-proof`. It is not an accepted change
to SPEC, the production wire format, security requirements or service UUIDs.
The Android application requires API 26; the separate native Swift app
targets iOS 15. Neither app links the MeshLink SDK or crypto dependency.

## GATT attributes

| Attribute | UUID | Behavior |
|---|---|---|
| Primary service | `c247f4e0-18b7-4d48-a73c-7a62f4860190` | Connectable foreground advertisement |
| Request | `c247f4e1-18b7-4d48-a73c-7a62f4860190` | Central writes with response |
| Response | `c247f4e2-18b7-4d48-a73c-7a62f4860190` | Peripheral notifies subscribed central |
| Response CCCD | `00002902-0000-1000-8000-00805f9b34fb` | Notifications enabled before READY |

The custom 128-bit UUIDs deliberately isolate this experiment from production
discovery. No manufacturer data, local name, bonding or encrypted attribute
permissions are used. All data is public synthetic test data.

## Packet format

Every request and response is exactly 20 bytes, compatible with the
ATT MTU 23 baseline. Larger MTUs are neither requested nor required.
Each run consists of 100 sequential request/response exchanges.

| Offset | Width | Meaning |
|---|---|---|
| 0 | 2 | Magic `4d 4c` |
| 2 | 1 | Experimental version `01` |
| 3 | 1 | Kind: request `00`, response `01` |
| 4 | 4 | Random run token, echoed unchanged; no security guarantee |
| 8 | 4 | Zero-based sequence, big-endian; 0 through 99 |
| 12 | 8 | Request: `00 01 02 03 04 05 06 07`; response: `ff fe fd fc fb fa f9 f8` |

Example with token 42, sequence zero:

```text
request:  4d 4c 01 00 00 00 00 2a 00 00 00 00 00 01 02 03 04 05 06 07
response: 4d 4c 01 01 00 00 00 2a 00 00 00 00 ff fe fd fc fb fa f9 f8
```

The central verifies the entire response, not just its length. The peripheral
checks magic, version, kind, sequence, token continuity and synthetic payload.
No fragmentation, prepared writes, retries or resume behavior is supported.

## Bounds and lifecycle

Only one peer and one request/response exchange are active. The Android client
retains at most one early notification until its write callback completes.
The Android server retains at most one early next request until notification
completion; the Swift server retains at most one notification when
`updateValue` reports backpressure and retries only on the ready callback.
These callbacks reflect platform completion/queue acceptance, not authenticated
application delivery.

The 45-second deadline starts when a role starts and covers discovery,
subscription, user delay and exchange. Starting again invalidates old callbacks.
Stop and leaving the foreground release the radio work. Android disconnect
cleanup waits for the disconnect callback, with a two-second close fallback.
No scan/connect retry loop, foreground service, background mode or state
restoration is configured.

Only the central emits PASSED after all 100 replies are verified. The
peripheral reports verified requests and submitted responses, not a delivery
guarantee. Both roles must be restarted before another run.

## Evidence boundaries

This probe can establish a foreground GATT path on tested device pairs. It
cannot establish a mesh topology, secure session, always-on background
availability, CoC fallback, bulk throughput, energy budget or SDK size.
Its elapsed-time display includes UI and callback overhead.

The Kotlin runner's host tests do not execute either platform adapter. The
Swift coordinator is a separate experimental implementation and needs an
Apple compilation and physical-device run. See
[run the spike](../how-to/run-ble-spike.md) for prerequisites and results to record.
