# MeshLink Noise and key-rotation design

**Status:** Locked — 2026-07-31

This record consolidates the rationale for Noise pattern selection, session
behavior, key rotation, and E2E handshake routing. Normative state machines,
parameters, and wire layouts live in [SPEC.md §§5 and
7](../../../SPEC.md#5-trust-model-tofu).

All Noise layers use X25519, HKDF-SHA256, ChaCha20-Poly1305, and SHA-256.
Ed25519 signs the stable MeshLink identity binding carried inside encrypted
handshake payloads.

## Noise pattern selection

MeshLink uses two patterns:

| Trust state | Pattern | Applies to |
|-------------|---------|------------|
| No trusted destination pin | Noise XX | Direct first contact and routed E2E first contact |
| Trusted, current destination pin | Noise IK | Direct reconnect and routed E2E reconnect |

A pinned identity, key, or generation mismatch fails closed. It never starts a
new first-contact exchange until the application explicitly resets trust.

### First contact with XX

Neither peer begins with a trusted remote static key. XX exchanges both X25519
static keys under handshake encryption. Both peers validate the encrypted,
Ed25519-signed identity binding before automatic TOFU pinning.

The three-message exchange costs an additional message compared with a
one-sided handshake but gives both endpoints authenticated key possession and
one coherent trust transition.

### Reconnect with IK

The initiator already knows the responder's trusted X25519 static key. IK binds
the first message to that key and encrypts the initiator static key before
transmission. Both sides validate the current signed identity binding and key
generation before updating `verifiedAt`.

MeshLink sends no application early data in the first IK message. Avoiding early
data removes application replay semantics from v0.1.

### Route-learned identity hints

Routing metadata may carry candidate identity bindings and keys for discovery
and planning, but an unpinned route candidate is not a trust
credential. An E2E first contact still uses routed XX. Only an existing trusted
pin permits routed IK.

## Noise session behavior

- At most one hop-by-hop and one E2E Noise session exists per peer and layer.
- New attempts for the same peer/layer are serialized or rejected.
- GATT-to-L2CAP migration changes the bearer without restarting the handshake or
  changing traffic keys.
- L2CAP failure may return data traffic to GATT because both bearers preserve
  the same application-layer security guarantees.
- Every new Noise session produces a fresh transcript `handshakeHash` and fresh
  directional transport keys.
- Responders do not perform autonomous retry loops; initiators use bounded
  retries in the same pattern and security mode.

## Key rotation

Key rotation is an explicit, dual-signed continuity event. The old Ed25519 key
signs continuity and the new Ed25519 key signs possession over stable
PeerIdentity/appHash, contiguous generations, new Ed25519/X25519 keys, and
reason. Routing SeqNo is independent. Existing Noise sessions may continue
according to the configured grace policy; new sessions use the accepted current
binding.

Planned rotations may retain the old key for a bounded grace period. Security
rotations default to immediate old-key rejection. A key change without valid
continuity proof is an identity mismatch, not a rotation.

Proofs remain in an installation-lifetime chain. Direct-neighbor propagation is
expected within one second and two-hop propagation within the routing-convergence
budget. A peer that missed generations uses rotation-recovery XX to validate the
chain back to its existing pin. The application continues using one stable
PeerIdentity and never handles keys, generations, or proofs.

## E2E handshake routing

E2E XX and IK messages are carried inside the existing routed mesh envelope.
Relays decrypt only adjacent hop encryption, inspect the routing fields required
for forwarding, and pass the E2E handshake bytes without inspection.

Reusing the routing layer avoids a second route-discovery protocol and keeps
route metrics applicable to handshake and application traffic. Relays are not
trusted with E2E plaintext or identity decisions.

## Related

- [Identity Binding and Fail-Closed Behavior](identity-binding-and-fail-closed.md)
- [Noise Session Renewal](noise-session-renewal.md)
- [Peer Hints and Identity Races](../discovery/peer-hint-and-identity-races.md)
- [Routing Design](../routing/routing-design.md)
- [Transport Bearer and MTU](../transport/mtu-negotiation.md)
- [SPEC.md §5, §7](../../../SPEC.md)
