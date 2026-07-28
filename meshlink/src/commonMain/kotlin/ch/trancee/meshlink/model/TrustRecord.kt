package ch.trancee.meshlink.model

import kotlin.time.Instant

/**
 * Trust record stored in the TrustStore for each peer.
 *
 * Tracks the TOFU pinning lifecycle: INITIATED → TRUSTED → REVOKED. Only minimal state is persisted
 * — identity material and timestamps. No plaintext, no diagnostics, no full identifiers.
 *
 * See docs/decisions/model/data-model.md and docs/decisions/model/persistence-strategy.md.
 *
 * SPEC-ANCHOR: trust-record
 */
public data class TrustRecord(
    public val peerIdentity: PeerIdentity,
    public val identityKey: IdentityKey,
    public val handshakeKey: HandshakeKey,
    public val state: TrustState = TrustState.INITIATED,
    public val generation: Int = 0,
    public val seenAt: Instant,
    public val verifiedAt: Instant,
)
