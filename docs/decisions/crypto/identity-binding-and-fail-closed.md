# Identity binding and fail-closed behavior

**Status:** Locked — 2026-07-31

> The normative handshake and failure rules live in
> [SPEC.md §§5 and 7](../../../SPEC.md#trust-model-tofu). This decision record
> defines MeshLink hash terminology, first-contact identity binding, and the
> project-wide fail-closed default. Rotating advertisement hints are specified
> separately because they are not security identity.

## Terminology

MeshLink uses three distinct hashes:

| Name | Size | Meaning |
|------|------|---------|
| `meshHash` | 16 bits | FNV-1a advertisement filter derived from `appId`; discovery optimization only |
| `appHash` | 128 bits | First 128 bits of domain-separated SHA-256 of `appId`; application-isolation value bound into security handshakes |
| `handshakeHash` | 256 bits | Noise transcript hash `h`; channel-binding value produced by the completed handshake |

`handshakeHash` is reserved for the Noise transcript and never names the
`appId`-derived value.

The application hash uses exact UTF-8 prefix bytes and concatenation:

```text
appHash = first128Bits(SHA-256("MeshLink app-id v1" || UTF8(appId)))
```

The advertisement `peerHint` is an independent rotating CSPRNG value. It is not
a hash, identity-binding field, or authentication credential.

## Key generation hint

`keyGeneration` is the current long-term Ed25519/X25519 generation for a stable
PeerIdentity. GATT exposes it as an untrusted hint: equal to the pinned
generation selects IK; a higher value selects rotation recovery; a lower,
malformed, or ambiguous value fails closed. It never changes trust, replaces a
binding, or proves a rotation without the contiguous dual-signed proof chain and
successful Noise authentication.

## First-contact identity binding

Direct first contact uses `Noise_XX_25519_ChaChaPoly_SHA256`. Each peer carries
this canonical structure in its encrypted handshake payload:

```text
IdentityBinding {
    version
    appHash
    peerIdentity
    ed25519PublicKey
    x25519PublicKey
    keyGeneration
}
```

The current Ed25519 private key signs the canonical encoding. Acceptance
requires all of the following:

1. The Ed25519 signature verifies.
2. The bound X25519 key equals the Noise static key proved by the handshake.
3. `appHash` and protocol version match the local instance.
4. Key generation is valid for any existing trust record.
5. The Noise XX transcript completes and yields `handshakeHash`.

The 128-bit `appHash`, not the collision-prone 16-bit `meshHash`, enforces
application isolation at the security boundary.

## Automatic TOFU

After the first XX handshake and identity binding fully validate, MeshLink pins
the identity and keys automatically. `seenAt` records the immutable first
observation of the full identity; `verifiedAt` records the latest successful
authentication.

TOFU proves continuity after first contact, not real-world identity before first
contact. An active attacker present during the first exchange can become the
pinned identity. MeshLink never represents automatic TOFU as out-of-band user
verification.

## Fail-closed default

Fail closed means uncertainty cannot become authority or plaintext. MeshLink
contains the failure at the smallest safe scope while preserving the previous
known-good state.

| Failure | Required closed behavior |
|---------|--------------------------|
| Advertisement or GATT metadata malformed | Ignore candidate or disconnect; do not create peer or trust state |
| Advertisement and GATT metadata disagree | Emit typed diagnostic and reject trust creation/update |
| `appHash` or protocol version mismatch | Abort handshake and disconnect |
| Signature or Noise static-key binding invalid | Abort handshake, discard provisional state, disconnect |
| Pinned identity/key mismatch | Reject; never retry with first-contact TOFU until explicit reset |
| Replay or duplicate outside valid transfer state | Drop without state mutation or response amplification |
| AEAD authentication failure | Drop ciphertext; never parse or retry as plaintext |
| Unsupported secure provider | Use only a specified, validated secure fallback; otherwise fail startup |
| Persisted identity/trust corruption | Fail affected startup/storage operation; never silently regenerate under the same installation |
| Runtime setting application failure | Keep previous effective settings and report a typed failure |
| Route or transfer invariant violation | Reject the affected update/transfer without corrupting unrelated peers |

## What fail closed does not mean

- It does not require crashing the process.
- It does not require stopping unrelated peers when one frame is invalid.
- It does not prohibit GATT fallback when L2CAP fails, because both bearers keep
  the same application-layer authentication and encryption guarantees.
- It does not prohibit bounded retries in the same security mode.
- It does not permit availability pressure to weaken authentication, integrity,
  replay protection, or trust continuity.

## Fallback requirements

Every fallback must be specified before implementation and must:

1. Preserve the security properties required by the operation.
2. Have an explicit trigger and bounded retry/timeout behavior.
3. Emit a machine-observable reason.
4. Avoid changing trust state merely because the preferred path failed.
5. Have success, failure, downgrade, and recovery tests.

An undocumented or less-secure alternative is not a fallback; it is a protocol
violation.

## Testing requirements

Tests cover each validation step independently and prove that failure occurs
before trust, routing, transfer, or persistence mutation. Integration tests also
prove:

- a 16-bit `meshHash` collision cannot cross the `appHash` boundary;
- key and hint rotation preserve the public `PeerIdentity`;
- pinned-key mismatch never falls back to XX;
- malformed metadata cannot allocate durable trust state;
- failed runtime reconfiguration preserves previous effective values; and
- diagnostics contain typed reasons but no secret or plaintext material.

## Related

- [Crypto design](crypto-design.md)
- [Connectable advertisement](../discovery/connectable-advertisement.md)
- [Data model](../model/data-model.md)
- [CONSTITUTION.md Principle I](../../../CONSTITUTION.md#i-rigorous-code-quality)
- [Noise Protocol Framework skill](../../../.agents/skills/noise-protocol-framework/SKILL.md)
