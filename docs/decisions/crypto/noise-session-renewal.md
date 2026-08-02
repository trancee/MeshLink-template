# Noise session renewal

**Status:** Locked — 2026-07-31

> Normative session states and triggers live in
> [SPEC.md §5](../../../SPEC.md#trust-model-tofu). This decision record explains
> lifetime, anti-herd scheduling, initiator selection, record limits, and
> fail-closed expiry.

## Context

Long-lived hop and E2E sessions must not use one traffic-key epoch indefinitely.
Renewing every session at a fixed process-wide time would synchronize peers and
create a thundering herd. Allowing both endpoints to initiate would create
competing IK handshakes. Time limits alone also do not protect against an
unexpectedly high record rate.

## Decision

Every hop and E2E Noise session has a hard 24-hour lifetime and per-direction
record limits. Renewal performs a fresh
`Noise_IK_25519_ChaChaPoly_SHA256` handshake and therefore obtains fresh DH,
transcript, and transport keys.

Long-term Ed25519/X25519 identity rotation is separate from session renewal.
GATT-to-L2CAP migration changes only the bearer and does not renew the Noise
session.

## Jittered time schedule

All scheduling uses monotonic elapsed time from successful handshake completion:

```text
establishedAt = completed handshake instant
renewalAt     = establishedAt + uniform random duration in [21h, 23h]
expiresAt     = establishedAt + 24h
```

Randomness is injectable for deterministic tests. There is no wall-clock,
midnight, process-wide, or fleet-wide renewal timer.

An idle suspended application is not awakened solely for renewal. On the next
permitted activity:

- before `renewalAt`, the existing session may continue;
- after `renewalAt` but before `expiresAt`, renewal starts before new application
  traffic is admitted; and
- at or after `expiresAt`, old keys are discarded and fresh IK must complete
  before protected traffic resumes.

## Preferred initiator and takeover

The peer with the lexicographically lower `PeerIdentity` is the preferred
renewal initiator. Rotating peerHint and platform TransportHandle values never
select a long-lived session role. Equal full identities across two installations
fail closed.

The non-preferred peer waits unless renewal has not completed near expiry. Its
takeover time is independently jittered in the interval from 23h30m through
23h50m. A successful inbound renewal cancels pending local attempts.

The same ordering resolves simultaneous opposite-direction GATT connections
after GATT has supplied claimed identities: when duplicates exist, retain the
link on which the lower-identity peer is the central and Noise initiator. If only one viable connection exists, retain it
regardless of preference.

## Retry behavior

- Only one renewal attempt may be active for a peer and Noise layer.
- Initiator retries use exponential backoff with full jitter.
- Retries never extend beyond `expiresAt`.
- Responders do not create autonomous retry loops.
- A failed attempt does not mutate the current valid epoch before expiry.
- At expiry, failure to renew closes the affected session rather than continuing
  with expired keys.

## Record limits

Counters are independent in each traffic direction:

```text
soft renewal threshold = 2^31 authenticated records
hard record limit      = 2^32 authenticated records
Noise nonce capacity   = 2^64 - 1
```

Reaching the soft threshold starts renewal. Reaching the hard limit blocks new
records under that epoch. The time and record limits are independent; whichever
requires renewal first wins.

Malformed or unauthenticated input does not advance authenticated record counts.
The implementation never approaches the Noise reserved terminal nonce.

## Epoch transition

Fresh IK key derivation prepares a new epoch but does not immediately switch
application traffic. MeshLink uses an authenticated prepare/commit protocol so
a lost renewal response cannot leave peers unknowingly using different epochs.

### Prepare

Both sides complete fresh IK and retain the resulting keys as a pending epoch.
The old epoch remains active. A failed or interrupted IK does not modify the
current epoch before hard expiry.

### Initiator commit

The preferred initiator stops assigning application records to the old epoch
and sends this control record under the pending new epoch:

```text
EpochCommit {
    newEpoch: UInt
    finalOldOutboundCounter: ULong
    handshakeHash: Byte[32]
}
```

### Responder acknowledgement

After validating the commit, the responder stops assigning old-epoch records
and sends under the pending epoch:

```text
EpochAcknowledgement {
    newEpoch: UInt
    finalOldOutboundCounter: ULong
    handshakeHash: Byte[32]
}
```

The responder activates the new outbound epoch after sending the
acknowledgement. The initiator activates it after validating the
acknowledgement.

Commit and acknowledgement handling is idempotent. Every retransmission is a
new authenticated record with a fresh record counter; ciphertext/nonces are
never reused. Duplicate valid commits produce the same acknowledgement and do
not create another epoch.

### Old-epoch drain

Each side starts a 30-second old-epoch receive-drain timer when it locally
activates the new epoch. It accepts old-epoch records only through the
peer-declared final counter. The old epoch closes when all expected records
arrive, after 30 seconds, or at hard expiry, whichever occurs first.

Pending new-epoch records may be authenticated and held in a bounded queue while
the commit exchange converges, but they are not delivered to application state
before local activation. Missing transfer chunks are retransmitted under the
new epoch through normal SACK behavior; routing/control state recovers through
its idempotent update or full-resynchronization path.

A disconnect does not extend key retention. Before commit completes, the old
epoch remains valid until hard expiry. After activation, the 30-second drain is
not a connectivity grace period.

## Process and route behavior

Noise traffic keys are never persisted. Process death discards all sessions.
E2E sessions may remain in memory across route changes and temporary hop
reconnections, but their original time and record limits continue unchanged.

## Testing requirements

Tests use virtual time and injected randomness to prove:

- renewal targets cover the full 21–23 hour interval;
- a fleet does not synchronize on one renewal instant;
- only the preferred peer initiates during normal operation;
- takeover occurs only in its final jitter window;
- inbound success cancels local retry work;
- exponential backoff cannot cross hard expiry;
- soft and hard record thresholds are direction-specific;
- expired or exhausted epochs cannot send or accept new records;
- lost commits and acknowledgements converge through fresh-nonce idempotent retries;
- old records above the declared final counter are rejected;
- the receive-drain timer starts only at local activation;
- pending new-epoch application records remain bounded and undispatched; and
- process restart never restores traffic keys.

## Related

- [Noise and key-rotation design](crypto-design.md)
- [Identity binding and fail-closed behavior](identity-binding-and-fail-closed.md)
- [Replay window](replay-window.md)
- [Connectable advertisement](../discovery/connectable-advertisement.md)
