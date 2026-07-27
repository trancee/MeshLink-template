# Wire Codec Implementation Decision

**Status:** Locked — 2026-07-26

## Decision

**Use hand-written pure-Kotlin codec (not FlatBuffers schema compilation)** for all wire frame encoding/decoding.

## Rationale

The wire format in `specs/wire_frames.yaml` uses custom bit-packed layouts (e.g., discovery advertisement: 3+2+3+16+8 bits, frame-specific field sizes) that don't map cleanly to FlatBuffers tables. FlatBuffers is designed for schema-evolution-friendly struct layouts with vtables, not for minimal bit-packed BLE packets.

**Trade-offs:**

| Aspect | FlatBuffers | Hand-Written |
|--------|-------------|--------------|
| Bit-packed discovery ad | ❌ Awkward (requires manual bit ops anyway) | ✅ Natural |
| Frame vtable overhead | 4-8 bytes/frame | 0 bytes |
| Schema evolution | ✅ Built-in | Manual (version field in each frame) |
| Code size | +FlatBuffers runtime (~50KB) | Minimal |
| Cross-platform | Same | Same (pure Kotlin) |
| Wycheproof-style testing | Same | Same |

Since MeshLink frames have **fixed versions** (protocol version in discovery, frame type enum) and we control both ends, schema evolution is managed by protocol version bumps, not field-level evolution. Hand-written codec is simpler, smaller, and faster for our use case.

## Implementation

### Codec Structure

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/wire/
package ch.trancee.meshlink.wire

// One file per frame type
WireFrameEncoder.kt
WireFrameDecoder.kt
DiscoveryAdvertisementCodec.kt
RouteUpdateCodec.kt
RouteWithdrawalCodec.kt
RouteDigestCodec.kt
TransferChunkCodec.kt
TransferAcknowledgmentCodec.kt
TransferCancelCodec.kt
KeyRotationCodec.kt
MeshEnvelopeCodec.kt
```

### Versioning

- **Protocol version**: 3 bits in discovery advertisement (0-7)
- **Frame version**: Not in wire; protocol version governs all frames
- **Breaking change**: Increment protocol version, maintain dual codec paths during transition

### Testing

- Round-trip tests for every frame type (`commonTest`)
- Wycheproof-style vector tests with known-good encoded bytes
- Fuzzing: random valid/invalid frames → decoder must not crash

## Wire Frame Reserved Types

Per `specs/wire_frames.yaml` notes:

| Frame Type | Value | Status |
|------------|-------|--------|
| Future experimental | 9-15 | Reserved for future use |

## Related

- [specs/wire_frames.yaml](../../../specs/wire_frames.yaml) — Machine-readable frame definitions
- [Routing Design](../../../docs/decisions/routing/routing-design.md)
