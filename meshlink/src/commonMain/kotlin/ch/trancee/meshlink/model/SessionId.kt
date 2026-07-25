package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline
import kotlin.random.Random

/**
 * A random 64-bit token used to correlate chunks within a transfer session.
 *
 * The session ID uniquely identifies one transfer session between a specific origin and
 * destination. It is generated at session creation and persists for the lifetime of that transfer.
 */
@JvmInline
public value class SessionId(private val value: ULong) {
    /** Raw 64-bit value of this session ID. */
    public val raw: ULong
        get() = value

    public companion object {
        /** A zero session ID (placeholder, not for real sessions). */
        public val ZERO: SessionId = SessionId(0UL)

        /** Generates a random 64-bit session token. */
        public fun generate(): SessionId =
            SessionId(((Random.Default.nextLong().toULong()) shl 1) or 1UL)
    }
}
