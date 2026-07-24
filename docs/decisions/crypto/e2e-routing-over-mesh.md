# E2E Handshake Routing Over Mesh

## Status: Proposed

## Problem Statement

When an origin peer initiates an end-to-end Noise IX handshake, it must possess the destination's static public key. The current spec assumes this key arrives via "signed identity gossip through the mesh," but this creates failure modes:

1. **Cold start race:** New origin may not have received gossip before attempting connection
2. **Partitioned mesh:** Key gossip may not have reached all nodes
3. **Key rotation:** Updated public key may not have propagated
4. **Unreachable destination:** No path exists to route handshake

## Current Gap

The existing `e2e-handshake-pattern.md` states:
> "E2E IX requires the origin to possess the destination's static public key *before* the handshake."

No fallback behavior is specified.

**Noise Pattern Analysis:**

Using Noise patterns from the framework specification:

| Pattern | Source Auth | Dest Auth | Notes |
|---------|-------------|-----------|-------|
| IX      | 2 (KCI-resistant) | 5 (strong FS) | Initiator knows responder's static key |
| NX      | 0 (none) | 3 (weak FS) | Initiator doesn't know key - revealed in msg2 |

**IX is correct when the key is known.** For unknown keys, **NX provides a degraded but functional fallback** — the initiator's identity is bound in msg1, but the responder's key isn't validated until msg2.

**Mitigation:** NX fallback uses PeerFingerprint verification (see `nx-fallback-mitigation.md`) and rate limiting to prevent abuse.

## Proposed Solution: Route E2E Handshake to Destination

### Core Principle

When the destination's key is unknown or the destination is not a direct neighbor, route the E2E handshake through the mesh to the destination, using the existing routing layer.

### Handshake Flow

```text
┌────────┐     ┌────────┐     ┌────────┐
│ Origin │     │ Relay1 │     │ Destination │
└────────┘     └────────┘     └────────┘

Phase 1: Link Setup (standard Noise XX)
Origin --(GATT/L2CAP)--> Relay1
Relay1 --(GATT/L2CAP)--> Destination

Phase 2: E2E Handshake Routing
Origin wraps IX Msg1 in HopEnvelope:
  HopEnvelope {
    destination: destination.peerId,
    payload: IX_Msg1_encrypted_for_destination
  }

Relay1 receives via HopSession, decrypts link layer,
sees destination != origin, forwards via its hop to Destination

Destination receives Hop Msg2, extracts IX_Msg1,
verifies it's for itself, responds with IX_Msg2
wrapped in HopEnvelope back to Origin

Phase 3: Return Path
Destination --(reverse route)--> Origin

Origin now has E2E traffic keys, can send encrypted payload
```

### Implementation Details

#### 1. HopEnvelope Wire Format

```flatbuffers
table HopEnvelope {
  // Destination peer ID (16-byte hash)
  destination: uint8Vector(16);
  
  // Inner payload (E2E handshake message or encrypted content)
  payload: uint8Vector(0);
  
  // Optional: hop count limit (0 = direct only)
  hopLimit: uint8;
}
```

#### 2. Routing Logic for E2E

```kotlin
// In TransferSession or E2ESession
suspend fun sendE2EHandshake(
  destination: PeerIdentity,
  content: ByteArray
): Result<Unit> {
  // Check if destination key is known
  val destKey = trustStore.getPublicKey(destination)
  
  return when {
    // Direct neighbor - standard IX
    destKey != null && routingTable.isDirectNeighbor(destination) -> {
      noiseIX.initiateHandshake(destination, destKey, content)
    }
    // Known key but not direct - route over mesh
    destKey != null -> {
      routeHandshakeOverMesh(destination, content)
    }
    // Unknown key - request gossip + wait
    else -> {
      requestIdentityGossip(destination)
        .awaitHandshakeCapability()
        .also { routeHandshakeOverMesh(destination, content) }
    }
  }
}
```

#### 3. Relay Behavior

Relays MUST NOT inspect E2E payloads:

```kotlin
// In RoutingLayer.onHopEnvelope()
if (envelope.destination != localPeerIdentity) {
  // E2E payload - forward without inspection
  val nextHop = routingTable.getNextHop(envelope.destination)
  hopSession.send(nextHop, envelope.serialized)
  return // NOT peer to inspect contents
}
```

#### 4. Destination Behavior

```kotlin
// In RoutingLayer.onHopEnvelope()
if (envelope.destination == localPeerIdentity) {
  // This is for me - check if it's E2E handshake
  when (parseE2EPayload(envelope.payload)) {
    is IX_Msg1 -> {
      // Extract originator from link layer (known from hop session)
      noiseIX.processHandshakeMessage(parseHandshake(), originator)
      respondWithIX_Msg2()
    }
    is EncryptedContent -> {
      // Standard E2E transfer
      processE2ETransfer(envelope.payload)
    }
  }
}
```

### Failure Modes & Handling

| Scenario | Detection | Recovery |
|----------|-----------|----------|
| No route to destination | `routingTable.getNextHop()` returns null | Fail with `TransferError.NoRoute` |
| Intermediate peer offline | Timeout on hop session | Use alternate route or fail |
| Destination key mismatch | Verify key hash before IX_Msg2 | Fail with `TrustError.KeyMismatch` |
| Replay attack | Nonce check in Noise state | Fail closed (standard Noise behavior) |

### Security Considerations

1. **Relays cannot read E2E content** — only link-layer encryption applied at each hop
2. **Destination validates originator** — IX_Msg1 binds origin key before processing
3. **Route hijacking detection** — unexpected hop path triggers digest resync
4. **Timing side-channels** — constant-time fallback prevents oracle attacks

### Testing Requirements

- `E2EHandshakeOverMeshTest`: verify IX handshake routes correctly through 2+ hops
- `RelayConfidentialityTest`: verify relays cannot decrypt E2E payloads
- `KeyUnknownFallbackTest`: verify graceful handling of missing public keys
- `ReplayAttackTest`: verify anti-replay window on E2E layer

### Dependencies

- Requires `RouteTable.getNextHop()` for multipath routing
- Requires `TrustStore.getPublicKey()` for key verification
- Requires `HopSession.send()` for hop-by-hop forwarding

## References

- `docs/decisions/crypto/e2e-handshake-pattern.md`
- `docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md`
- `docs/decision/transport/gatt-l2cap-transport-selection.md`
- `docs/decisions/crypto/nx-fallback-mitigation.md`
