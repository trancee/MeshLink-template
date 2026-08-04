package ch.trancee.meshlink.model

import kotlin.time.Duration

/** Options for outbound payload operations. */
public data class TransferOptions(
    /** Delivery scheduling priority and default timeToLive. */
    public val priority: Priority = Priority.NORMAL,

    /**
     * Optional positive elapsed delivery lifetime. If null, uses [priority]'s default (HIGH=10min,
     * NORMAL=5min, LOW=1min).
     */
    public val timeToLive: Duration? = null,
) {
    public companion object {
        public val DEFAULT: TransferOptions = TransferOptions()
    }
}
