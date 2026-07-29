# MeshLink Routing Design: Key Decisions

**Status:** Locked — 2026-07-20

Consolidates five routing-layer decisions. **Specification content** (tables, state machines, wire formats, parameter values) lives in [SPEC.md §8](../../../SPEC.md). This ADR captures only the *rationale*.

---

## 1. SeqNo Ownership: Destination Self-Reports

### The Bug

`RouteCoordinator.onPeerConnected` minted a fresh seqno on every BLE reconnect (`directRouteSeqNos[peerId.value] + 1L`). Two different direct neighbors of the same destination each minted their own unrelated seqno sequence. Relays forwarded unchanged, so the value originated at whichever node most recently connected — not the destination itself.

### Why RFC 8966's SeqNo-Request Doesn't Fit

RFC 8966 §3.7 assumes the destination is reachable for a round trip. BLE devices disconnect constantly; the 3-second convergence budget (SPEC.md §13.7) can't accommodate a multi-hop request/response. batman-adv and Bluetooth Mesh both avoid destination round-trips for freshness.

### Decision SeqNo Ownership

Each node owns **one** local seqno counter (32-bit unsigned), incremented **only on cold start** (`MeshLink.start()`). After a hop session is established with a new direct neighbor, each side sends one self-origin `RouteUpdate`:

- `destination = <own peerId>`
- `nextHop = <own peerId>` (null = self-origin)
- `metric = DIRECT_ROUTE_METRIC`
- `seqNo = <own current counter value>`

No new wire frame type — `RouteCoordinator.onRouteUpdate` already handles route updates; a self-origin update (`destination == sender`) is a new case.

**Receiving side:** `onPeerConnected` no longer mints a seqno. It installs the direct route provisionally and, on receiving the peer's self-origin `RouteUpdate`, adopts the reported `seqNo` as authoritative — the same path any other peer's self-reported route flows through. If the self-origin update never arrives, the provisional route falls back to existing feasibility/expiry logic.

**Result:** The "relay reports higher seqno than live direct route" edge case becomes structurally impossible — every neighbor converges on the same self-reported value.

---

## 2. Hello/IHU: Remove, Don't Implement

RFC 8966 §3.4 permits skipping Hello-based discovery if "neighbour discovery is performed by means outside of the Babel protocol" — BLE GATT/L2CAP connect/disconnect is exactly that.

**Decision:** Remove `WireFrame.Hello`, `WireFrame.Ihu`, their envelope type codes, and their no-op dispatch branches. BLE connection state provides liveness immediately, with no periodic-interval polling. Route metric stays flat `+1` hop count.

---

## 3. RouteDigest: On Mismatch, Push Full Table

RFC 8966 has no route-table digest mechanism. `RouteDigestTracker` already computes a 32-bit FNV-1a hash attached to nearly every advertisement. `RouteCoordinator.onRouteDigest` is currently a no-op.

**Decision:** On receiving a `RouteDigest` that doesn't match the local table, the receiver re-sends its full current route table to that one peer via the existing `RouteAdvertisementPlanner` path — mirroring RFC 8966's wildcard route request → full table dump. No new wire frame type, no new field, no request/response round trip.

---

## 4. Routing Metadata Privacy: Always-Encrypted

### Scope

Covers routing-control metadata only. Does **not** redesign trust, identity, or payload-layer application encryption.

### Goals

- Protect route-control metadata from passive BLE observers
- No negotiation overhead — encryption is always on
- Fail-closed: decrypt/auth failures drop the frame, never fall back to plaintext

### Decision Routing Metadata Privacy

`ROUTE_UPDATE` (0x01) and `ROUTE_WITHDRAWAL` (0x02) **always** carry AEAD-encrypted payloads. No plaintext mode, no negotiation, no fallback. `ROUTE_DIGEST` (0x03) carries only a 32-bit FNV-1a hash — it reveals no route contents and is left as plaintext for synchronization.

**Encryption:**

- Algorithm: ChaCha20-Poly1305 (Noise session AEAD)
- Nonce: Derived from Noise session internal counter (not transmitted)
- Ciphertext: `encrypted_payload || 16-byte Poly1305 tag`
- AAD: Frame type + version (bound to ciphertext integrity)
- UPDATE plaintext includes destination peer's public key (32 bytes), enabling identity distribution through routing table

### Why No Negotiation?

Since no MeshLink release has shipped, there are no legacy peers to be compatible with. Always-encrypt is simpler and more secure:

- No downgrade attacks (plaintext never an option)
- No negotiation overhead (encryption always on)
- No fallback logic (no graceful degradation to plaintext)
- Simpler implementation (one code path, not two)

### Fail-Closed Rules

- Decrypt/auth failures drop frame immediately
- No silent fallback to plaintext
- No retry with different encryption mode
- Route table logic only runs after successful decryption

### Diagnostics Contract (Minimal)

- `route.decrypt_failures` — count of frames dropped due to decrypt/auth failure
- `route.frame_type` — UPDATE or WITHDRAWAL

---

## 5. Link Quality Metric: Composite (RSSI + Flags)

### Context

Hello/IHU removed because BLE connection state provides liveness (§2). However, multi-hop routing decisions benefit from link quality signals beyond hop count. Both Link A (-60 dBm) and Link B (-85 dBm) cost "1 hop" but Link B should be deprioritized.

### Decision Link Quality Metric

Composite `UInt32` metric:

- Low byte (8 bits): RSSI normalized 0-255 (0 = unusable, 255 = excellent)
- High bits (24 bits): Flags (CoC support bit 8, low latency bit 9, high power bit 10)

**RSSI Normalization:**

```kotlin
rssiNormalized = when {
    rssi >= -30 -> 255
    rssi <= -100 -> 0
    else -> ((rssi + 100) * 255 / 70).toUInt()
}
```

### Why RSSI-Based

| Metric | Pros | Cons | Decision |
|--------|------|------|----------|
| RSSI | Immediate, no extra packets | Proxy only, environment-sensitive | **Primary** — baseline for routing |
| Throughput | Direct measure | Requires measurement overhead | Secondary — post-connection refinement |
| Packet Delivery | Reliability measure | Needs feedback loop | Future enhancement |

### Routing Integration

Path selection prefers:

1. Feasible routes only (RFC 8966 requirement)
2. Lower hop count
3. Higher metric score

See SPEC.md §8.4.1 for loop detection mechanisms.

---

## Testing Focus

- `RouteMetricTest`: RSSI normalization
- `MetricForwardingTest`: Peer-to-peer metric propagation
- `PathSelectionTest`: Low-quality path deprioritization
- Decrypt/auth failure handling
- No plaintext routing metadata on wire

---

## Related

- [MTU Negotiation](../transport/mtu-negotiation.md)
- [Wire Format Specification](../../../specs/wire-frames.yaml)
- [Data Model](../model/data-model.md)
- [E2E Handshake Pattern](../crypto/crypto-design.md)
