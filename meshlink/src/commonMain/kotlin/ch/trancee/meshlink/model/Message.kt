package ch.trancee.meshlink.model

import kotlin.time.Instant

/**
 * Complete, authenticated incoming MESSAGE.
 *
 * Emitted via [MeshLink.messages] Flow. At most once per (origin, MESSAGE, id).
 */
public data class Message(
    /** Message identifier. */
    public val id: MessageId,

    /** Origin peer identity. */
    public val origin: PeerIdentity,

    /** Payload bytes. */
    public val payload: ByteArray,

    /** Local completion instant. */
    public val completedAt: Instant,
)
