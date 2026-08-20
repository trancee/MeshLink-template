# Noise session renewal

**Status:** Locked — 2026-07-31

> Normative session states and triggers live in
> [SPEC.md §5](../../../SPEC.md#5-trust-model-tofu) and
> [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml).
> This decision record explains lifetime, anti-herd scheduling, initiator
> selection, record limits, and fail-closed expiry.

## Why 24-hour hard lifetime with jittered renewal

Every hop and E2E Noise session has a hard 24-hour lifetime and per-direction
record limits. Renewal performs a fresh `Noise_IK_25519_ChaChaPoly_SHA256`
handshake.

```text
establishedAt = completed handshake instant
renewalAt     = establishedAt + uniform random duration in [21h, 23h]
expiresAt     = establishedAt + 24h
```

**Rationale:** Long-lived sessions must not reuse one traffic-key epoch
indefinitely (key wear-out). A fixed process-wide renewal time would synchronize
peers and create a thundering herd of simultaneous handshakes. Jittering
renewal over [21h, 23h] spreads load. 24h hard limit bounds key exposure
window. Monotonic elapsed time avoids clock sync issues across offline devices.

## Why preferred initiator by lexicographic PeerIdentity

The peer with the lexicographically lower `PeerIdentity` is the preferred
renewal initiator. Rotating `peerHint` and platform `TransportHandle` values
never select a long-lived session role. Equal full identities across two
installations fail closed.

**Rationale:** Deterministic initiator selection avoids competing IK handshakes
from both endpoints. `PeerIdentity` is stable (unlike `peerHint`/`TransportHandle`),
so the ordering is stable across reconnections. Equal identities across
installations indicate a clone or provisioning error — failing closed is the
correct response.

## Why takeover window near expiry

The non-preferred peer waits unless renewal has not completed near expiry. Its
takeover time is independently jittered in [23h30m, 23h50m]. A successful
inbound renewal cancels pending local attempts.

**Rationale:** The preferred peer should normally initiate. The takeover window
ensures liveness if the preferred peer fails/suspends before expiry. Independent
jitter prevents both peers attempting simultaneously near expiry. Inbound
success cancels local work to avoid duplicate handshakes.

## Why retry behavior with exponential backoff

Only one renewal attempt per peer/layer. Initiator retries use exponential
backoff with full jitter. Retries never extend beyond `expiresAt`. Responders
do not create autonomous retry loops. A failed attempt does not mutate the
current valid epoch before expiry. At expiry, failure to renew closes the
session rather than continuing with expired keys.

**Rationale:** Single active attempt prevents duplicate handshakes. Exponential
backoff with jitter prevents synchronized retries. Hard expiry bound ensures
the session doesn't linger in retry past the security deadline. Responder
passive role avoids both peers retrying simultaneously. Fail-closed at expiry
prevents stale key use.

## Why independent per-direction record limits

```text
soft renewal threshold = 2^31 authenticated records
hard record limit      = 2^32 authenticated records
Noise nonce capacity   = 2^64 - 1
```

Reaching soft threshold starts renewal. Reaching hard limit blocks new records.
Time and record limits are independent; whichever requires renewal first wins.

**Rationale:** Record limits protect against nonce exhaustion and key wear from
high throughput. 2^31/2^32 per direction fits in 64-bit counters. Independence
means a quiet direction doesn't delay renewal for a busy direction. Hard limit
blocking is fail-closed — no records under exhausted epoch.

## Why epoch transition protocol (Prepare/Commit/Ack/Drain)

Fresh IK prepares a new epoch but does not immediately switch traffic. MeshLink
uses an authenticated prepare/commit protocol:

1. **Prepare**: Both sides complete fresh IK, retain pending keys. Old epoch active.
2. **Commit**: Preferred initiator stops old-epoch allocation, sends `EpochCommit`
   with `finalOldOutboundCounter` under pending keys.
3. **Ack**: Responder validates, stops old-epoch allocation, sends
   `EpochAcknowledgement` with its counter. Responder activates after sending;
   initiator activates after validating.
4. **Drain**: Each side starts 30-second old-epoch receive drain on local
   activation. Accepts old records only through peer-declared final counter.

Idempotent retries use fresh record counters and nonces. Old epoch closes when
all expected records arrive, after 30s, or at hard expiry.

**Rationale:** Atomic epoch transition prevents peers unknowingly using
different epochs (which would cause decryption failures). Commit/ack under
pending keys ensures the new keys are valid before switching. 30-second drain
bounds old-epoch record processing. Idempotent retries with fresh nonces handle
loss without nonce reuse. Drain starting at local activation (not commit send)
ensures the sender doesn't wait for unresponsive peer.

## Why keys never persist and process death discards sessions

Noise traffic keys are never persisted. Process death discards all sessions. E2E
sessions may remain in memory across route changes and temporary hop
reconnections, but their original time and record limits continue unchanged.

**Rationale:** Persisting traffic keys would require encrypted storage and
increase attack surface. Process death discarding sessions is simpler and
safer — fresh IK on restart is a clean slate. Route changes don't reset limits
because the security exposure window is time/record based, not path based.

## Related

- [Noise and key-rotation design](crypto-design.md)
- [Identity binding and fail-closed behavior](identity-binding-and-fail-closed.md)
- [Replay window](replay-window.md)
- [Connectable advertisement](../discovery/connectable-advertisement.md)
- [SPEC.md §5.7](../../../SPEC.md#5-trust-model-tofu)
- [specs/protocol/state-machines.yaml](../../../specs/protocol/state-machines.yaml)
