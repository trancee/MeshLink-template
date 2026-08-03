package ch.trancee.meshlink.model

import ch.trancee.meshlink.diagnostics.DiagnosticCode
import kotlin.time.Instant

/**
 * Snapshot of a known peer's observable state.
 *
 * Included in [MeshLink.knownPeers] StateFlow. Advertisement-only candidates are not canonical
 * peers. Trusted, mismatched, and revoked records remain visible as disconnected. Transient
 * unverified/verifying observations are removed when their work ends.
 *
 * SPEC-ANCHOR: known-peer-model
 */
public data class KnownPeer(
    /** Stable per-installation peer identifier. */
    public val peerIdentity: PeerIdentity,

    /** Current BLE link state. */
    public val peerState: PeerState,

    /** Trust classification. */
    public val peerTrust: PeerTrust,

    /** Immutable instant the full canonical identity was first learned. */
    public val seenAt: Instant,

    /** Nullable instant of the latest successful authentication. */
    public val verifiedAt: Instant?,

    /** Current route cost to this peer, if known. */
    public val routeCost: UInt?,

    /** Current hop count to this peer, if known. */
    public val hopCount: UByte?,

    /** Active Noise session count (hop + E2E). */
    public val activeSessionCount: Int,

    /** Diagnostic event code for this peer, if any. */
    public val diagnosticCode: DiagnosticCode?,

    /** Severity of diagnostic event. */
    public val diagnosticSeverity: DiagnosticSeverity? = null,
) {
    /** Creates a minimal [KnownPeer] for a newly discovered peer. */
    public constructor(
        peerIdentity: PeerIdentity,
        peerState: PeerState,
        peerTrust: PeerTrust,
        seenAt: Instant,
    ) : this(
        peerIdentity = peerIdentity,
        peerState = peerState,
        peerTrust = peerTrust,
        seenAt = seenAt,
        verifiedAt = null,
        routeCost = null,
        hopCount = null,
        activeSessionCount = 0,
        diagnosticCode = null,
        diagnosticSeverity = null,
    )
}
