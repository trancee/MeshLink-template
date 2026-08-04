package ch.trancee.meshlink.model

import ch.trancee.meshlink.diagnostics.DiagnosticCode
import kotlin.time.Instant

/**
 * Snapshot of a known peer's observable state.
 *
 * Included in [MeshLink.peers] StateFlow. Advertisement-only candidates are not canonical peers.
 * Trusted, mismatched, and revoked records remain visible as disconnected. Transient
 * unverified/verifying observations are removed when their work ends.
 *
 * SPEC-ANCHOR: known-peer-model
 */
public data class KnownPeer(
    /** Stable per-installation peer identifier. */
    public val identity: PeerIdentity,

    /** Current BLE link state. */
    public val state: PeerState,

    /** Trust classification. */
    public val trust: PeerTrust,

    /** Current route cost to this peer, if known. */
    public val routeCost: UInt?,

    /** Current hop count to this peer, if known. */
    public val hopCount: UByte?,

    /** Noise session count (hop + E2E). */
    public val sessionCount: Int,

    /** Diagnostic event code for this peer, if any. */
    public val diagnosticCode: DiagnosticCode?,

    /** Severity of diagnostic event. */
    public val diagnosticSeverity: DiagnosticSeverity? = null,

    /** Immutable instant the full canonical identity was first learned. */
    public val seenAt: Instant,

    /** Nullable instant of the latest successful authentication. */
    public val verifiedAt: Instant?,
) {
    /** Creates a minimal [KnownPeer] for a newly discovered peer. */
    public constructor(
        identity: PeerIdentity,
        state: PeerState,
        trust: PeerTrust,
        seenAt: Instant,
    ) : this(
        identity = identity,
        state = state,
        trust = trust,
        routeCost = null,
        hopCount = null,
        sessionCount = 0,
        diagnosticCode = null,
        diagnosticSeverity = null,
        seenAt = seenAt,
        verifiedAt = null,
    )
}
