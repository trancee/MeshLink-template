package ch.trancee.meshlink.model

import ch.trancee.meshlink.util.*
import kotlin.jvm.JvmInline

/**
 * A random 64-bit token used to correlate chunks within a transfer session.
 *
 * The session ID uniquely identifies one transfer session between a specific origin and
 * destination. It is generated at session creation and persists for the lifetime of that transfer.
 *
 * SPEC-ANCHOR: session-id-model
 */
@JvmInline
public value class SessionId(private val value: ULong) {
    public companion object {
        /** A zero session ID (placeholder, not for real sessions). */
        public val ZERO: SessionId = SessionId(0UL)

        /** Generates a random 64-bit session token. */
        public fun generate(): SessionId = SessionId(((randomULong() shl 1) or 1UL))

        /** Creates a SessionId from a hex string. */
        public fun fromHex(hex: String): SessionId {
            require(hex.length <= 16) { "SessionId hex must be at most 16 chars (64-bit)" }
            return SessionId(hex.toULong(16))
        }
    }

    /** Provides string representation for implicit conversion in string templates. */
    override fun toString(): String = value.toHexString()
}
