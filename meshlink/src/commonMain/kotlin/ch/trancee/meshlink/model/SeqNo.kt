package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/**
 * Unsigned 32-bit sequence number with safe wrap-around comparison.
 *
 * RFC 8966 §3.7 requires signed interpretation for seqno comparison. All comparisons use `(this -
 * other).toInt() > 0` which correctly handles wrap at 2^32: if `old = 0xFFFFFFFE` and `new = 1`
 * (wrapped), `1 - 0xFFFFFFFE = 3` (signed) > 0 → newer.
 *
 * See SPEC.md §8.2 and
 * [destination-sourced seqno design](docs/decisions/routing/routing-design.md).
 *
 * SPEC-ANCHOR: seqno-model
 */
@JvmInline
public value class SeqNo(private val value: UInt) {
    /** Raw sequence number value. */
    public val raw: UInt
        get() = value

    /** Sequence number zero. */
    public companion object {
        public val ZERO: SeqNo = SeqNo(0u)

        /** Creates a SeqNo from a raw value. */
        public fun fromRaw(value: UInt): SeqNo = SeqNo(value)
    }

    /**
     * Returns true if this seqno is newer than [other], handling 32-bit wrap-around. Uses signed
     * comparison: `(this - other)` interpreted as signed 32-bit integer > 0.
     */
    public fun isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0

    /**
     * Returns true if this seqno is older than [other]. Symmetric to [isNewerThan] per RFC 8966
     * §3.7.
     */
    public fun isOlderThan(other: SeqNo): Boolean = other.isNewerThan(this)

    /**
     * Signed difference for modular arithmetic comparison. `(this - other)` interpreted as signed
     * 32-bit integer.
     */
    public operator fun minus(other: SeqNo): Int = (value - other.value).toInt()

    /**
     * Increments this seqno by 1, wrapping at 2^32. Used on cold start of mesh participation
     * (MeshLink.start()).
     */
    public fun increment(): SeqNo = SeqNo(value + 1u)
}
