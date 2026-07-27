package ch.trancee.meshlink.model

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Peer lifecycle events surfaced to host applications.
 *
 * These events replace raw BLE connection state with a smoothed model that absorbs momentary
 * transport churn via grace periods.
 *
 * See [docs/explanation/peer-lifecycle.md] for the rationale.
 */
public sealed interface PeerEvent {
    /** A peer was discovered or reconnected. */
    public data class Found(
        public val peerId: PeerIdentity,
        public val state: PeerConnectionState,
        public val timestamp: Instant = Clock.System.now(),
    ) : PeerEvent

    /** A peer's connection state changed. */
    public data class StateChanged(
        public val peerId: PeerIdentity,
        public val state: PeerConnectionState,
        public val timestamp: Instant = Clock.System.now(),
    ) : PeerEvent

    /** A peer was lost after the grace period expired without reconnection. */
    public data class Lost(
        public val peerId: PeerIdentity,
        public val timestamp: Instant = Clock.System.now(),
    ) : PeerEvent
}
