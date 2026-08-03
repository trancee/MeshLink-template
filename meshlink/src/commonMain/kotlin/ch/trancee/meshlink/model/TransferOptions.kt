package ch.trancee.meshlink.model

/** Options for outbound payload operations. */
public data class TransferOptions(
    /** Delivery scheduling priority and default timeToLive. */
    public val priority: Priority = Priority.NORMAL,

    /**
     * Optional positive elapsed time-to-live override (milliseconds). If null, uses [priority]'s
     * default (HIGH=10min, NORMAL=5min, LOW=1min).
     */
    public val ttlMillis: Long? = null,
) {
    public companion object {
        public val DEFAULT: TransferOptions = TransferOptions()
    }
}
