# End-to-end handshake pattern: Noise IX (link layer: Noise XX)

- **Status:** Locked — 2026-07-20.
- **Supersedes:** the earlier shorthand that a single "Noise XX mutual handshake"
  served both the hop-by-hop link and the end-to-end transport.

## Context

MeshLink has two encryption layers (Principle 2 — Two-layer encryption):

1. **Hop-by-hop link encryption** between adjacent mesh nodes, so relays can
   forward traffic without reading it.
2. **End-to-end encryption** between the message origin and its final
   destination, carried *inside* the link frames.

The earlier design said "Noise XX mutual handshake" for both. That is correct
for the link layer but **not** for the end-to-end layer, because the two layers
have a different static-key-knowledge asymmetry at handshake start.

### Link layer (direct neighbors, first contact)

Neither side holds the other's *verified* static key yet (TOFU). Both must learn
and pin each other's keys. **Noise XX** (`Noise_XX_25519_ChaChaPoly_SHA256`) is
the textbook mutual-auth first-contact pattern. This remains the locked link-layer
choice. (An **IK** upgrade for post-TOFU reconnects — both ends already hold
pinned keys, giving proactive mutual auth + 0-RTT — is a *considered future
optimization, not adopted*.)

### End-to-end layer (origin → destination, possibly multi-hop)

- The **origin** (initiator) *knows* the destination's static public key: it is
  obtained via **signed identity gossip** through the mesh (the trust/identity
  layer distributes Ed25519/X25519 pubkeys; discovery only carries the 12-byte
  `keyHash` hint).
- The **destination** (responder) does *not* necessarily know the origin's static
  key (multi-hop peers may never have directly handshake).

This asymmetry is exactly what **Noise IX** is for:

```text
Noise_IX_25519_ChaChaPoly_SHA256
  -> e, s, es      (origin:      ephemeral, static, DH(e, rs))
  <- e, ee, se, s  (destination: ephemeral, DH(e,e), DH(s,e), static)
```

## Decision

| Layer | Pattern | Protocol name |
|---|---|---|
| Hop-by-hop link (first contact) | Noise XX | `Noise_XX_25519_ChaChaPoly_SHA256` |
| Hop-by-hop link (post-TOFU, future) | Noise IK *(deferred)* | `Noise_IK_25519_ChaChaPoly_SHA256` |
| End-to-end (origin → destination) | Noise IX | `Noise_IX_25519_ChaChaPoly_SHA256` |

Both layers use the same primitives (X25519 DH, HKDF-SHA256, ChaCha20-Poly1305)
and the same fail-closed crypto contract (`docs/decisions/crypto/vector-policy.md`).

## Rationale — why IX, not XX, for end-to-end

1. **Key-knowledge asymmetry.** XX assumes neither end pre-knows the other's
   static key. For E2E the origin already holds the destination's key (gossiped),
   so XX wastes a round and re-exposes the destination's static key needlessly.
   IX uses the known key as a pre-message: `es = DH(e, rs)` in message 1.
2. **Proactive 0-RTT authentication.** In IX the origin binds the handshake to the
   known destination key in message 1, so a relay / MITM on the multi-hop path
   cannot interpose on the first E2E message. With XX the origin does not
   authenticate proactively, leaving the first E2E message exposed to an active
   on-path attacker.
3. **The destination pins the origin's identity during the handshake.** IX
   transmits the origin's static key (`s`, encrypted under `es`) in message 1; the
   destination decrypts it (it holds `rs`) and TOFU-pins it. The alternative
   K-family pattern **NK** does *not* send the initiator's static key, so the
   destination would only see an authenticated ephemeral and could not pin a static
   identity — wrong for a mesh addressed by peer identity. **IN** was rejected
   because it omits the destination's message-2 static key, losing key-rotation
   refresh.
4. **Key-rotation robustness.** IX re-sends the destination's current static key
   in message 2, letting the origin detect / refresh if its gossiped copy was
   stale. Patterns that suppress the responder static key lose this.

## Why standardize the name (was `2PNI`)

The internal identifier was `Noise_2PNI_25519_ChaChaPoly_SHA256`. Its token
sequence (`-> e, s, es` / `<- e, ee, se, s`) is exactly **standard Noise IX** — a
distinct 2-message pattern, *not* a variant of XX (which is 3-message:
`-> e` / `<- e, ee, s, es` / `-> s, se`). We standardize to
`Noise_IX_25519_ChaChaPoly_SHA256` so the protocol is auditable as a known
pattern and is not mistaken for a bespoke variant. The name string is mixed into
the handshake hash, so it is an internal identifier both ends must agree on;
standardizing costs nothing and aids review.

## Dependencies this decision introduces

1. **Signed identity gossip (trust / identity layer).** E2E IX requires the origin
   to possess the destination's static public key *before* the handshake. The
   trust/identity data model must gossip signed Ed25519/X25519 public keys across
   the mesh — not just the discovery `keyHash` hint. This is an addition to the
   **e01 data-model / trust** work.
2. **Multi-hop E2E handshake transport.** The two IX messages ride as inner frames
   across the mesh; relays decrypt / apply only the link (XX) session and forward
   the sealed E2E frame. The routing / transfer layer must carry E2E
   session-establishment messages as payloads.
3. **E2E replay protection.** Message 1's 0-RTT proactive authentication means a
   relay can replay the origin's first E2E message. The transport layer must
   enforce a monotonic nonce / DTLS-style anti-replay window (RFC 9147) on the E2E
   layer; replay protection is *not* the handshake's job.

## Testing / acceptance

- `NoiseHandshakeTest`: IX establishes bidirectional E2E traffic keys; the origin is
  proactively authenticated (an unknown / missing destination key fails closed
  before key derivation).
- Fail-closed: a malformed / all-zero X25519 shared secret fails the IX handshake
  at the X25519 / HKDF step, identically to the link XX handshake.
- Multi-node harness (`MeshTestHarness` / `VirtualMeshTransport`): an E2E session
  between non-adjacent peers establishes over the relayed path with relays unable
  to read inner content.
- Wire-compat fixtures: `v1_e2e_handshake_ix.hex` (handshake pattern token bytes)
  added under `commonTest/resources/wire-compat/`.

## Related

- `docs/explanation/privacy-pseudonyms.md` — discovery → Noise XX hop handshake → TOFU
- `docs/explanation/cut-through-relay.md` — per-hop Noise session, hop-local re-encryption
- `docs/decisions/crypto/vector-policy.md` — fail-closed crypto contract applies to both XX and IX
- `docs/decisions/crypto/android-crypto-fallback-proof.md`
