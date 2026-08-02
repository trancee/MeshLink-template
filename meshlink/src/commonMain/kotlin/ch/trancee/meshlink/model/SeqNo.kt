package ch.trancee.meshlink.model

import ch.trancee.meshlink.util.toBytesBE
import ch.trancee.meshlink.util.toUIntBE
import kotlin.jvm.JvmInline

/**
 * Unsigned 32-bit sequence number with safe wrap-around comparison.
 *
 * RFC 8966 §3.7 requires signed interpretation for seqno comparison. All comparisons use `(this -
 * other).toInt() > 0` which correctly handles wrap at 2^32: if `old = 0xFFFFFFFE` and `new = 1`
 * (wrapped), `1 - 0xFFFFFFFE = 3` (signed) > 0 → newer.
 *
 * The modular comparison window is 2^31: a seqno is considered "newer" if the signed distance
 * `(this - other)` is in `(0, 2^31]`, and "older" if in `[-2^31, 0)`. At exactly ±2^31 the
 * comparison is ambiguous (the two values are equidistant on the circle); `isNewerThan` returns
 * false at the boundary, matching RFC 8966's conservative interpretation.
 *
 * See SPEC.md §3.2 and
 * [destination-sourced seqno design](docs/decisions/routing/routing-design.md).
 *
 * SPEC-ANCHOR: seqno-model
 */
@JvmInline
@Suppress("TooManyFunctions")
public value class SeqNo(private val value: UInt) {
    /** Returns the decimal string representation of this sequence number. */
    override fun toString(): String = value.toString()

    /** Raw 32-bit unsigned value, for wire serialization and deserialization. */
    public fun toUInt(): UInt = value

    /** Factory and constants for [SeqNo]. */
    public companion object {
        /** Sequence number zero. */
        public val ZERO: SeqNo = SeqNo(0u)

        /** Maximum sequence number value (2^32 - 1 = 4294967295). */
        public val MAX_VALUE: SeqNo = SeqNo(UInt.MAX_VALUE)

        /**
         * Creates a [SeqNo] from a raw [UInt] value (e.g., read from the wire).
         *
         * This is the deserialization counterpart to [toUInt], used when decoding `ROUTE_UPDATE`,
         * `ROUTE_WITHDRAWAL`, and `KEY_ROTATION` frames.
         */
        public fun fromUInt(value: UInt): SeqNo = SeqNo(value)

        /**
         * Creates a [SeqNo] from a 4-byte big-endian representation, for wire deserialization.
         *
         * This is the deserialization counterpart to [toByteArray], used when decoding
         * `ROUTE_UPDATE`, `ROUTE_WITHDRAWAL`, and `KEY_ROTATION` frames from a byte stream.
         *
         * @param bytes exactly 4 bytes; throws [IllegalArgumentException] if [bytes.size] is not 4.
         */
        public fun fromBytes(bytes: ByteArray): SeqNo {
            require(bytes.size == SEQNO_BYTE_LENGTH) {
                "Expected $SEQNO_BYTE_LENGTH bytes, got ${bytes.size}"
            }
            return SeqNo(bytes.toUIntBE())
        }
    }

    /** True if this seqno equals [ZERO]. */
    public val isZero: Boolean
        get() = value == 0u

    /**
     * Returns true if this seqno is newer than [other], handling 32-bit wrap-around.
     *
     * Uses signed comparison: `(this - other)` interpreted as signed 32-bit integer > 0. Returns
     * false when equal or when the distance is at/ beyond the 2^31 ambiguity boundary.
     */
    public fun isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0

    /**
     * Returns true if this seqno is newer than or equal to [other].
     *
     * Symmetric to [isOlderThanOrEqualTo] per RFC 8966 §3.7. Used by the Babel feasibility
     * condition where `>=` comparison determines route admissibility.
     */
    public fun isNewerThanOrEqualTo(other: SeqNo): Boolean = (value - other.value).toInt() >= 0

    /**
     * Returns true if this seqno is older than [other]. Symmetric to [isNewerThan] per RFC 8966
     * §3.7.
     */
    public fun isOlderThan(other: SeqNo): Boolean = other.isNewerThan(this)

    /**
     * Returns true if this seqno is older than or equal to [other].
     *
     * Symmetric to [isNewerThanOrEqualTo].
     */
    public fun isOlderThanOrEqualTo(other: SeqNo): Boolean = other.isNewerThanOrEqualTo(this)

    /**
     * Increments this seqno by 1, wrapping at 2^32. Operator form for idiomatic `seqNo++` usage.
     */
    public operator fun inc(): SeqNo = SeqNo(value + SEQNO_INCREMENT)

    /**
     * Returns the 4-byte big-endian wire representation of this seqno.
     *
     * Matches the wire format defined in wire-frames.yaml (4 bytes, big-endian, unsigned 32-bit).
     * See [fromBytes] for the deserialization counterpart.
     */
    public fun toByteArray(): ByteArray = value.toBytesBE()

    /**
     * Returns the modular unsigned forward distance from [other] to this seqno.
     *
     * Computed as `this - other` in [UInt] arithmetic (wrapping subtraction). Useful for
     * determining how far behind a peer's seqno is relative to the local value, and for route
     * staleness diagnostics.
     *
     * Example: `SeqNo(1u).distanceFrom(SeqNo(0xFFFFFFFEu))` returns `3u` (three forward steps from
     * 0xFFFFFFFE through zero to 1).
     */
    public fun distanceFrom(other: SeqNo): UInt = value - other.value
}

/** Number of bytes in wire representation of SeqNo (4 bytes = 32 bits). */
private const val SEQNO_BYTE_LENGTH = 4

/** Increment value for sequence number advancement (wraps at 2^32). */
private const val SEQNO_INCREMENT: UInt = 1u
