# Routing Metadata Privacy: Always-Encrypted Design

This RFC defines the wire contract for protecting routing metadata in MeshLink.
ROUTE_UPDATE and ROUTE_WITHDRAWAL frames are always AEAD-encrypted using the
Noise session key — no capability negotiation, no plaintext fallback, no
downgrade path.

## Scope

This contract covers routing-control metadata only. It does **not** redesign:

- trust or identity
- payload-layer application encryption
- product-level UX policy

## Goals

- protect route-control metadata from passive BLE observers
- no negotiation overhead — encryption is always on
- fail-closed: decrypt/auth failures drop the frame, never fall back to plaintext

## Baseline

Before this design, route-control payloads were plaintext `BabelRouteFrameCodec`
values encoded by `BabelRouteFrameCodec` with wire types:

- `0x21` — UPDATE
- `0x22` — WITHDRAWAL

That leaves destination, next-hop, metric, and sequence metadata visible to a
nearby observer.

## Always-Encrypted Design

ROUTE_UPDATE (0x21) and ROUTE_WITHDRAWAL (0x22) always carry AEAD-encrypted
payloads. There is no plaintext mode, no capability negotiation, and no
fallback. ROUTE_DIGEST (0x04) carries only a 32-bit FNV-1a hash of the route
table — it reveals no route contents (destinations, metrics, next hops are all
hashed) and is left as plaintext for synchronization purposes.

### Wire Format

```flatbuffers
table RouteUpdate {
  destination: uint8Vector(16);   // Destination peer ID
  next_hop: uint8Vector(16);       // Next hop toward destination
  seq_no: uint32;                  // Sequence number
  metric: uint32;                 // RSSI + flags
  flags: uint8;                   // Direct route, stale bit, etc.
  // AEAD ciphertext = encrypted_payload || 16-byte Poly1305 tag
  // Nonce derived from Noise session counter (not transmitted)
  ciphertext: uint8Vector(0);
}

table RouteWithdrawal {
  destination: uint8Vector(16);   // Destination peer ID
  seq_no: uint32;                  // Sequence number
  // AEAD ciphertext = encrypted_payload || 16-byte Poly1305 tag
  // Nonce derived from Noise session counter (not transmitted)
  ciphertext: uint8Vector(0);
}
```

### Encryption

- **Algorithm:** ChaCha20-Poly1305 (Noise session AEAD)
- **Nonce:** Derived from the Noise session's internal counter — not transmitted
- **Ciphertext:** `encrypted_payload || 16-byte Poly1305 tag`
- **AAD:** Frame type + version (bound to ciphertext integrity)

The encrypted plaintext is the existing `BabelRouteFrameCodec.encode(...)`
output for UPDATE or WITHDRAWAL, so route-table logic can stay intact after a
successful decrypt. For UPDATE frames, the plaintext also includes the
destination peer's public key (32 bytes), enabling identity distribution
through the routing table — see [Identity Distribution via Route Updates](../crypto/e2e-handshake-pattern.md).

### Why No Negotiation?

Since no MeshLink release has shipped, there are no legacy peers to be
compatible with. Always-encrypt is simpler and more secure:

- No downgrade attacks (plaintext is never an option)
- No negotiation overhead (encryption is always on)
- No fallback logic (no graceful degradation to plaintext)
- Simpler implementation (one code path, not two)

### Fail-Closed Rules

- Decrypt/auth failures drop the frame immediately
- No silent fallback to plaintext
- No retry with a different encryption mode
- Route table logic only runs after successful decryption

## Diagnostics Contract

Since there is no negotiation or fallback, the diagnostics contract is minimal:

- `route.decrypt_failures` — count of frames dropped due to decrypt/auth failure
- `route.frame_type` — the wire type (UPDATE or WITHDRAWAL)

No `negotiated_mode`, `fallback_reason`, `downgrade_verdict`,
`envelope_version`, or `envelope_failure` fields are needed.

## Acceptance Evidence Contract

S02/S03 closeout bundles must include machine-readable evidence for:

- `route.decrypt_failures` count (should be 0 in normal operation)
- `route.frame_type` distribution (UPDATE vs WITHDRAWAL)
- encrypted frame counts on both legs of a transfer

The acceptance runner should fail closed when required fields are missing or
malformed.

## Deterministic Test Vectors

### Test vector 1 — ROUTE_UPDATE with always-encrypted payload

Input:

- destination: `0102030405060708090a0b0c0d0e0f10`
- next_hop: `1112131415161718191a1b1c1d1e1f20`
- seq_no: `42`
- metric: `187`
- flags: `0`
- plaintext: `BabelRouteFrameCodec.encode(UPDATE, ...)`
- ciphertext: `Noise_AEAD_Encrypt(plaintext, AAD=frame_type||version)`

Expected:

- Frame type `0x21`
- Ciphertext is non-empty (encrypted payload + 16-byte tag)
- Plaintext is never visible on the wire

### Test vector 2 — ROUTE_WITHDRAWAL with always-encrypted payload

Input:

- destination: `0102030405060708090a0b0c0d0e0f10`
- seq_no: `43`
- plaintext: `BabelRouteFrameCodec.encode(WITHDRAWAL, ...)`
- ciphertext: `Noise_AEAD_Encrypt(plaintext, AAD=frame_type||version)`

Expected:

- Frame type `0x22`
- Ciphertext is non-empty (encrypted payload + 16-byte tag)
- Plaintext is never visible on the wire

## Implementation Note

The route-table semantics stay unchanged: decrypt the ciphertext first, then
pass the inner payload through the existing route-frame decode/application flow.
The only change is that the payload is encrypted on the wire and decrypted
before processing.

## Related Docs

- [Understanding Babel routing in MeshLink](../../explanation/understanding-babel-routing.md)
- [Destination-sourced route freshness, IHU cost signal removal, and digest-triggered resync](destination-sourced-seqno-ihu-removal-digest-resync-design.md)
- [Link quality metric](link-quality-metric.md)
- [GATT as the always-available control plane, L2CAP CoC as the preferred data plane](../transport/gatt-l2cap-transport-selection.md)
- [Wire Format Specification](../wire/wire-format-spec.md)
- [Core Types](../model/core-types.md)
