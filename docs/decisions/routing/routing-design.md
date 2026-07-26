# MeshLink Routing Design: SeqNo, Hello/IHU Removal, Digest Resync, Metadata Privacy, Link Metric

**Status:** Locked — 2026-07-20

Consolidates three separable decisions for MeshLink's Babel-inspired routing:

1. **SeqNo ownership**: How a route's sequence number is originated and kept fresh across BLE reconnect churn
2. **Hello/IHU**: What replaces the currently-dead `Hello`/`Ihu` wire frames
3. **RouteDigest**: What happens when digest exchange detects a mismatch
4. **Routing Metadata Privacy**: Always-encrypted ROUTE_UPDATE/WITHDRAWAL frames
5. **Link Quality Metric**: Composite metric with RSSI + capability flags

Does not redesign trust, identity, transport bearer selection, or the feasibility condition.

See [SPEC.md §8](../../../SPEC.md#8-routing-layer) for complete specification (tables, state machines, parameter values).
See [specs/state_machines.yaml](../../../specs/state_machines.yaml) for machine-readable state machine.
See [specs/wire_frames.yaml](../../../specs/wire_frames.yaml) for wire format.

---

## 1. Route Freshness: Destination Self-Reports SeqNo

**The bug:** `RouteCoordinator.onPeerConnected` minted a fresh seqno on every BLE reconnect (`directRouteSeqNos[peerId.value] + 1L`). Two different direct neighbors of the same destination each minted their own unrelated seqno sequence. Relays forwarded unchanged, so the value originated at whichever node most recently connected — not the destination itself.

**Why RFC 8966's seqno-request doesn't fit:** It assumes the destination is reachable for a round trip. BLE devices disconnect constantly; the 3-second convergence budget (SPEC.md §13.7) can't accommodate a multi-hop request/response. batman-adv and Bluetooth Mesh both avoid destination round-trips for freshness.

**Decision:** Each node owns **one** local seqno counter (32-bit unsigned), incremented **only on cold start** (`MeshLink.start()`). After a hop session is established with a new direct neighbor, each side sends one `RouteUpdate` about itself: `destination = <own peerId>`, `nextHop = <own peerId>`, `metric = DIRECT_ROUTE_METRIC`, `seqNo = <own current counter value>`. No new wire frame type — `RouteCoordinator.onRouteUpdate` already handles route updates; a self-origin update (`destination == sender`) is a new case.

**Receiving side:** `onPeerConnected` no longer mints a seqno. It installs the direct route provisionally and, on receiving the peer's self-origin `RouteUpdate`, adopts the reported `seqNo` as authoritative — the same path any other peer's self-reported route flows through. If the self-origin update never arrives, the provisional route falls back to existing feasibility/expiry logic.

**Result:** The "relay reports higher seqno than live direct route" edge case becomes structurally impossible — every neighbor converges on the same self-reported value.

**Out of scope:** `SeqNoRequest`-based starvation recovery (RFC 8966 §3.8.2.1) remains unimplemented. The reconnect-driven bump substituted for it; this design's cold-start self-refresh continues that role.

---

## 2. Hello/IHU: Remove, Don't Implement

RFC 8966 §3.4 permits skipping Hello-based discovery if "neighbour discovery is performed by means outside of the Babel protocol" — BLE GATT/L2CAP connect/disconnect is exactly that.

**Decision:** Remove `WireFrame.Hello`, `WireFrame.Ihu`, their envelope type codes, and their no-op dispatch branches. BLE connection state provides liveness immediately, with no periodic-interval polling. Route metric stays flat `+1` hop count.

---

## 3. RouteDigest: On Mismatch, Push Full Table

RFC 8966 has no route-table digest mechanism. `RouteDigestTracker` already computes a 32-bit FNV-1a hash attached to nearly every advertisement. `RouteCoordinator.onRouteDigest` is currently a no-op.

**Decision:** On receiving a `RouteDigest` that doesn't match the local table, the receiver re-sends its full current route table to that one peer via the existing `RouteAdvertisementPlanner` path — mirroring RFC 8966's wildcard route request → full table dump. No new wire frame type, no new field, no request/response round trip.

---

## 4. Routing Metadata Privacy: Always-Encrypted Design

### Scope

Covers routing-control metadata only. Does **not** redesign trust, identity, or payload-layer application encryption.

### Goals

- Protect route-control metadata from passive BLE observers
- No negotiation overhead — encryption is always on
- Fail-closed: decrypt/auth failures drop the frame, never fall back to plaintext

### Design

ROUTE_UPDATE (0x21) and ROUTE_WITHDRAWAL (0x22) always carry AEAD-encrypted payloads. There is no plaintext mode, no negotiation, and no fallback. ROUTE_DIGEST (0x04) carries only a 32-bit FNV-1a hash — it reveals no route contents and is left as plaintext for synchronization.

#### Wire Format

```flatbuffers
table RouteUpdate {
  destination: uint8Vector(16);   // Destination peer ID
  next_hop: uint8Vector(16);       // Next hop toward destination
  seq_no: uint32;                  // Sequence number
  metric: uint32;                 // RSSI + flags
  flags: uint8;                   // Direct route, stale bit, etc.
  ciphertext: uint8Vector(0);     // AEAD encrypted payload + 16-byte tag
}

table RouteWithdrawal {
  destination: uint8Vector(16);
  seq_no: uint32;
  ciphertext: uint8Vector(0);     // AEAD encrypted payload + 16-byte tag
}
```

#### Encryption

- **Algorithm:** ChaCha20-Poly1305 (Noise session AEAD)
- **Nonce:** Derived from the Noise session's internal counter — not transmitted
- **Ciphertext:** `encrypted_payload || 16-byte Poly1305 tag`
- **AAD:** Frame type + version (bound to ciphertext integrity)

The encrypted plaintext is the existing route-frame decode output. For UPDATE frames, the plaintext also includes the destination peer's public key (32 bytes), enabling identity distribution through the routing table.

#### Why No Negotiation?

Since no MeshLink release has shipped, there are no legacy peers to be compatible with. Always-encrypt is simpler and more secure:

- No downgrade attacks (plaintext is never an option)
- No negotiation overhead (encryption is always on)
- No fallback logic (no graceful degradation to plaintext)
- Simpler implementation (one code path, not two)

#### Fail-Closed Rules

- Decrypt/auth failures drop the frame immediately
- No silent fallback to plaintext
- No retry with a different encryption mode
- Route table logic only runs after successful decryption

#### Diagnostics Contract

Since there is no negotiation or fallback, the diagnostics contract is minimal:

- `route.decrypt_failures` — count of frames dropped due to decrypt/auth failure
- `route.frame_type` — the wire type (UPDATE or WITHDRAWAL)

---

## 5. Link Quality Metric: Composite Metric with RSSI + Flags

### Context

Hello/IHU frames are removed because BLE connection state provides liveness (see §2). However, multi-hop routing decisions benefit from link quality signals beyond hop count. Both Link A (-60 dBm) and Link B (-85 dBm) cost "1 hop" but Link B should be deprioritized.

### Decision

#### Metric Structure

Composite `UInt32` where:

- **Low byte (8 bits):** RSSI normalized 0-255 (0 = unusable, 255 = excellent)
- **High bits (24 bits):** Flags for CoC support, interval, power mode

Normalization:

```kotlin
rssiNormalized = when {
    rssi >= -30 -> 255
    rssi <= -100 -> 0
    else -> ((rssi + 100) * 255 / 70).toUInt()
}
```

See SPEC.md §3.3 for complete `RouteMetric` and `LinkMetric` definitions.

#### Why RSSI-Based

| Metric | Pros | Cons | Decision |
|--------|------|------|----------|
| RSSI | Immediate, no extra packets | Proxy only, environment-sensitive | **Primary** — baseline for routing |
| Throughput | Direct measure | Requires measurement overhead | Secondary — post-connection refinement |
| Packet Delivery | Reliability measure | Needs feedback loop | Future enhancement |

#### Routing Integration

Path selection prefers:

1. Feasible routes only (RFC 8966 requirement)
2. Lower hop count
3. Higher metric score

See SPEC.md §8.4.1 for loop detection mechanisms.

---

## Wire-Format Impact

| Frame | Before | After |
|-------|--------|-------|
| `RouteUpdate` | destination-route announcement | also used for self-origin announcements (`destination == sender`) on connect |
| `Hello` | dead, encoded/decoded, no-op on receipt | **removed** |
| `Ihu` | dead, encoded/decoded, no-op on receipt | **removed** |
| `SeqNoRequest` | dead, encoded/decoded, no-op on receipt | remains dead, explicitly deferred (kept as reserved wire surface) |
| `RouteDigest` | sent on nearly every advertisement, receive side no-op | receive side triggers a full-table push to the mismatched peer |

---

## SeqNo Comparison with Wrap Handling

Each node owns a single 32-bit unsigned seqno counter (`UInt`), incremented only on cold start. All comparisons use signed 32-bit arithmetic per RFC 8966 §3.7:

```kotlin
@JvmInline
value class SeqNo(private val value: UInt) {
    fun isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0
    fun isOlderThan(other: SeqNo): Boolean = other.isNewerThan(this)
    operator fun minus(other: SeqNo): Int = (value - other.value).toInt()
    fun increment(): SeqNo = SeqNo(value + 1u)
}
```

---

## Testing

### Metric

- `RouteMetricTest`: RSSI normalization
- `MetricForwardingTest`: Peer-to-peer metric propagation
- `PathSelectionTest`: Low-quality path deprioritization

### Privacy

- Decrypt/auth failure handling
- No plaintext routing metadata on wire

---

## Related Docs

- [GATT as control plane, L2CAP CoC as data plane](../transport/gatt-l2cap-transport-selection.md)
- [Wire Format Specification](../wire/wire-format-spec.md)
- [Data Model](../model/data-model.md)
- [E2E Handshake Pattern](../crypto/crypto-design.md) (IX handshake for E2E)
