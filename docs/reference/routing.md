# Routing Layer

> **Specification**: [SPEC.md §8](../../SPEC.md#routing-layer)  
> **Design rationale**: [Routing Design](../decisions/routing/routing-design.md)

## Design Principles

- Babel-inspired distance-vector with feasibility condition (RFC 8966)
- **Destination self-reports SeqNo** — originated only on cold start, not on reconnect
- **No Hello/IHU** — BLE connection state provides liveness (RFC 8966 §3.4)
- **RouteDigest triggers full table push** on mismatch
- **Always-encrypted metadata** — no plaintext routing frames
- **Composite link metric** — RSSI + capability flags

## SeqNo Ownership (Critical Fix)

Each node owns **one** local seqno counter (32-bit unsigned), incremented **only on cold start** (`MeshLink.start()`). On new direct connection, each side sends self-origin `RouteUpdate`:

- `destination = <own peerId>`
- `nextHop = <own peerId>` (null = self-origin)
- `metric = DIRECT_ROUTE_METRIC`
- `seqNo = <own current counter>`

Receiving side adopts reported seqNo as authoritative. Eliminates "relay reports higher seqno than live direct route" edge case.

## Hello/IHU Removal

Removed `WireFrame.Hello`, `WireFrame.Ihu`, type codes, and no-op dispatch branches. BLE connect/disconnect provides immediate liveness. Route metric stays flat `+1` hop count.

## RouteDigest Resync

On receiving `RouteDigest` that doesn't match local table → re-send full current route table to that peer via `RouteAdvertisementPlanner`. Mirrors RFC 8966 wildcard route request → full table dump. No new frame type, no request/response round trip.

## Routing Metadata Privacy

`ROUTE_UPDATE` (0x01) and `ROUTE_WITHDRAWAL` (0x02) **always** AEAD-encrypted. No plaintext mode, no negotiation, no fallback. `ROUTE_DIGEST` (0x03) is plaintext 32-bit FNV-1a hash only.

**Encryption**: ChaCha20-Poly1305 (Noise session AEAD). Nonce from session counter. Ciphertext = `encrypted_payload || 16-byte tag`. AAD = frame type + version.

**Fail-closed**: Decrypt/auth failures drop frame immediately. No retry with different mode.

**Diagnostics**: `route.decrypt_failures` count, `route.frame_type` (UPDATE/WITHDRAWAL)

## Link Quality Metric

Composite `UInt32`:

- Low byte (8 bits): RSSI normalized 0-255 (0=unusable, 255=excellent)
- Bit 8: `supportsL2CAP`
- Bit 9: `lowLatency`
- Bit 10: `highPower`
- Bits 11-31: Reserved

**RSSI Normalization**: ≥-30→255, ≤-100→0, else `((rssi + 100) * 255 / 70)`

**Path Selection**: 1) Feasible routes only, 2) Lower hop count, 3) Higher metric score

---

## Quick Links

- [SPEC.md §8 — Full routing spec](../../SPEC.md#routing-layer)
- [Routing Design ADR](../decisions/routing/routing-design.md)
- [State Machines Spec](../../specs/state-machines.yaml)
- [Wire Frames Spec](../../specs/wire-frames.yaml)
- [MTU Negotiation ADR](../decisions/transport/mtu-negotiation.md)
- [E2E Handshake Pattern ADR](../decisions/crypto/crypto-design.md)
