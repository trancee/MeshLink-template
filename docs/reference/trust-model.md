# Trust Model (TOFU)

> Source: [SPEC.md §5](../../SPEC.md#5-trust-model-tofu)

## 5.1 Handshake Pattern

- **Hop-by-hop link layer (first contact):** `Noise_XX_25519_ChaChaPoly_SHA256` - mutual authentication for initial TOFU
- **Hop-by-hop link layer (post-TOFU reconnect):** `Noise_IK_25519_ChaChaPoly_SHA256` - proactive mutual auth + 0-RTT when both peers hold pinned keys
- **End-to-end layer**: `Noise_IX_25519_ChaChaPoly_SHA256` - origin knows destination key, destination may not know origin

[Decision: docs/decisions/crypto/crypto-design.md]

## 5.2 Trust Flow

```text
Discovery → GATT connection → Noise_XX_25519_ChaChaPoly_SHA256 handshake → INITIATED → TOFU pin → TRUSTED → TrustRecord stored
```

## 5.3 Identity Distribution via Route Updates

- Each peer's public key is included in `ROUTE_UPDATE` frames as part of the encrypted payload
- When a peer connects directly (Noise XX), it learns the neighbor's public key and includes it in route updates about that neighbor
- Route updates propagate hop-by-hop: each relay re-advertises the destination's public key in its own route updates
- This enables E2E IX handshake where the origin knows the destination's static key before connecting
- NX fallback (`Noise_NX`) is used only when the destination's public key is not yet in the routing table (cold start, partition recovery)
- `KEY_ROTATION` updates the public key when a peer rotates its keys

## 5.4 Revocation

- Explicit API action required to reset trust
- No silent re-trust on identity mismatch
- Stored trust records persist until revoked

## 5.5 NX Fallback for Unknown Keys

When destination's public key is unknown, `Noise_NX_25519_ChaChaPoly_SHA256` provides a degraded but functional fallback:

**Security Mitigations:**

- Rate limiting: max 3 NX attempts/minute per destination (prevents DoS)
- Timeout: 10s vs 30s for IX (limits resource window)
- Full public key verification in payload (validates identity claim)
- 32-bit nonce in payload (replay protection)
- Diagnostic flag: `handshake.fallback_used = true` (observability)

**Protocol:** NX_Msg1 includes the full 64-byte concatenated public key (Ed25519 || X25519) + nonce. Destination verifies: `received_ed25519 == expected_ed25519 AND received_x25519 == expected_x25519`. Mismatch or replay = reject.

[Decision: docs/decisions/crypto/crypto-design.md]

## 5.6 Key Rotation Protocol

Key rotation triggered by:

- Periodic timer (default: **3 days**)
- Manual API: `meshLink.rotateIdentity()`
- Security event (compromise detection)

**Wire Protocol:**

```flatbuffers
KeyRotationAnnouncement {
  identityKey: CryptoKey (NEW public key)
  seqNo: UInt (always 1 - new identity)
  signature: ByteArray (Ed25519 signature with OLD private key)
  reason: KeyRotationReason (PERIODIC, MANUAL, SECURITY_EVENT)
}
```

**Neighbor Behavior:**

1. Verify signature with OLD known key
2. Accept new key into TrustStore
3. Seqno resets to 1 (new crypto era)
4. Old key retained for grace period verification

**Grace Period:**

- `PERIODIC` or `MANUAL` rotation: `rotationGracePeriod` (default 1 hour) — both old and new keys accepted for in-flight sessions
- `SECURITY_EVENT` rotation: `compromiseGracePeriod` (default `ZERO`) — old key rejected immediately

**Key Rotation During Active Transfer:**

- Existing Noise sessions (link-layer and E2E) continue using current traffic keys — rotation does not terminate active sessions
- New sessions (new connections, new E2E handshakes) use the rotated keys
- Old identity key retained for the grace period to decrypt any late-arriving handshake messages
- Transfer layer is identity-key agnostic; it only depends on Noise session keys which remain valid

[Decision: docs/decisions/crypto/crypto-design.md]

## 5.7 E2E Handshake Routing Over Mesh

When destination is not a direct neighbor or key is unknown:

```text
Phase 1: Link Setup (standard Noise_XX_25519_ChaChaPoly_SHA256)
Origin --(GATT/L2CAP)--> Relay(s)

Phase 2: E2E Handshake Routing
Origin wraps IX_Msg1 in a RoutingFrame (the wire-level routing frame):
  RoutingFrame {
    destination: destination.peerIdentity,   // set from RoutingMessage.destination
    payload: IX_Msg1_encrypted,               // RoutingMessage serialized + E2E content
    hopLimit: UByte                         // set by routing layer, not application
  }

Relay(s) decrypt hop layer → re-encrypt → forward without inspecting E2E payload

Phase 3: Destination responds with IX_Msg2 wrapped for return path

Phase 4: Origin now has E2E traffic keys
```

**Security:** Relays cannot read E2E content; only link-layer encryption at each hop.

[Decision: docs/decisions/crypto/crypto-design.md]
