# MTU Discovery Mechanism

**Status:** Locked — 2026-07-27

## Context

The transfer layer (SPEC.md §9) specifies that `chunkSize` is "selected by local power mode, bounded by peer MTU." Without knowing the peer's negotiated MTU, the sender cannot correctly size transfer chunks — oversized chunks would be silently dropped by the ATT/MTU layer, and undersized chunks waste bandwidth.

The MTU negotiation ADR (`mtu-negotiation.md`) specifies target MTU of 517 on GATT (BLE 4.2+) and L2CAP CoC MTU of 517, but does not specify how peers learn each other's actual negotiated MTU after the connection is established.

## Decision

**Peer MTU is discovered during GATT service discovery, after the Noise handshake completes.** The procedure:

### Step-by-step

1. **GATT connection established** (before Noise handshake)
2. **Noise XX handshake completes** (or IK/NX as appropriate) — the control plane is now encrypted
3. **Central discovers services** on the peripheral using the MESH_SERVICE_UUID (`4853454D-0000-1000-8000-00805F9B34FB`)
4. **Central discovers the MTU_NEGOTIATION characteristic** (UUID suffix `0x0005`)
5. **Central reads `MTU_NEGOTIATION`** — the peripheral responds with `{ confirmedMtu: UInt16 }`
6. **Central computes effective chunk size:** `effectiveChunkSize = min(powerMode.chunkSize, confirmedMtu - 3)` (ATT header overhead = 3 bytes for GATT write-without-response)
7. **Central writes its own desired MTU** to `MTU_NEGOTIATION` as `{ requestedMtu: UInt16 }` — the peripheral records this for the reverse direction

### For L2CAP CoC (after GATT connection)

1. **L2CAP channel established** (PSM from discovery advertisement)
2. **Each side queries the OS for the negotiated CoC MTU:**
   - Android: `BluetoothSocket.getReceiveBufferSize()` / `BluetoothSocket.getMtu()`
   - iOS: `CBL2CAPChannel.maximumTransmissionUnit` (available after channel opens)
3. **Effective chunk size for CoC:** `effectiveChunkSize = min(powerMode.chunkSize, coCMTU - 4)` (L2CAP header overhead = 4 bytes)

### Why read-after-connect instead of advertising MTU in the discovery advertisement?

- The discovery advertisement is a single BLE advertisement packet with no room for MTU information (the 12-byte PeerFingerprint already consumes significant space in the 31-byte ADV packet)
- MTU is negotiated per-connection, not per-discovery. Different GATT connections to the same peer can yield different MTUs depending on the radio/environment at connection time
- Reading after the Noise handshake ensures the MTU value is available before any encrypted control-plane traffic flows

### Fallback

If `MTU_NEGOTIATION` characteristic is not found (older peer firmware), fall back to:

- GATT: MTU = 23 (BLE 4.0 default, no MTU exchange)
- L2CAP CoC: MTU = 251 (BLE 4.0 default initial)
- effectiveChunkSize = min(powerMode.chunkSize, mtu - overhead)

### Timing

MTU discovery adds exactly one GATT read round-trip after the Noise handshake. For the XX pattern (1 round-trip for handshake), total setup is:

- Noise XX: 1 RTT (handshake) + 1 RTT (MTU read) = 2 RTTs
- Noise IK: 1 RTT (handshake) + 1 RTT (MTU read) = 2 RTTs (same, since IK is 1 RTT)
- Noise IX: 1 RTT (handshake) + 1 RTT (MTU read) = 2 RTTs

This is acceptable given the cold-start target of <500ms to first advertisement (the MTU read happens after connection, not before).

## Diagnostics

`DiagnosticEvent.MtuNegotiatedEvent` is emitted with:

- `requestedMtu`: the MTU the central requested
- `negotiatedMtu`: the MTU the peripheral confirmed
- `effectiveChunkSize`: the computed chunk size for this session
- `bearer`: GATT or L2CAP

## Cross-References

- MTU negotiation procedure: [MTU Negotiation ADR](mtu-negotiation.md)
- GATT Service UUID: [GATT Service UUID ADR](gatt-service-uuid.md)
- Wire frames: [specs/wire_frames.yaml](../../../specs/wire_frames.yaml)
- Transport selection: [GATT/L2CAP transport selection](gatt-l2cap-transport-selection.md)
