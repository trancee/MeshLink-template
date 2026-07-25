package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

/**
 * A random 64-bit token used to correlate chunks within a transfer session.
 *
 * The session ID uniquely identifies one transfer session between a specific origin and
 * destination. It is generated at session creation and persists for the lifetime of that transfer.
 */
@JvmInline
@Serializable
value class SessionId(private val value: ULong) {
    /** Raw 64-bit value of this session ID. */
    val raw: ULong
        get() = value

    companion object {
        /** A zero session ID (placeholder, not for real sessions). */
        val ZERO: SessionId = SessionId(0UL)

        /** Generates a random 64-bit session token. */
        fun generate(): SessionId =
            SessionId(((kotlin.random.Random.Default.nextULong()) shl 1) or 1UL)
    }
}
