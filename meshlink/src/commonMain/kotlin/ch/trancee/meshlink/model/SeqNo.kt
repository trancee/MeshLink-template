package ch.trancee.meshlink.model

/**
 * Sequence number for route versioning. Wrapped for safe comparison to handle wrap-around. Values
 * are 32-bit unsigned to match Babel protocol semantics.
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
}
