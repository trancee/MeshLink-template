package ch.trancee.meshlink.model

/**
 * Link quality metric for routing decisions.
 *
 * SPEC-ANCHOR: link-metric-model
 */
public data class LinkMetric(
    /** RSSI normalized to 0-255 scale (0 = unusable, 255 = excellent). */
    public val rssiNormalized: UInt,
    /** Whether the link supports L2CAP Connection-oriented Channels. */
    public val supportsL2CAP: Boolean,
    /** Whether the connection interval is fast (<= 15 ms). */
    public val fastInterval: Boolean,
    /** Whether the high power mode is active on this link. */
    public val highPowerMode: Boolean,
) {
    /** Composite metric value: low byte = RSSI, high bits = flags. */
    public val composite: UInt =
        ((supportsL2CAP.bit(8) or fastInterval.bit(9) or highPowerMode.bit(10)) shl 8) or
            rssiNormalized
}

private fun Boolean.bit(position: Int): UInt = if (this) 1u shl position else 0u
