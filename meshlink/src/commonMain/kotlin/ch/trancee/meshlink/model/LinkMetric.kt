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
    public val supportsCoc: Boolean,
    /** Whether the connection interval is fast (<= 15 ms). */
    public val fastInterval: Boolean,
    /** Whether the high power tier is active on this link. */
    public val highPowerTier: Boolean,
) {
    /** Composite metric value: low byte = RSSI, high bits = flags. */
    public val composite: UInt =
        ((supportsCoc.bit(8) or fastInterval.bit(9) or highPowerTier.bit(10)) shl 8) or
            rssiNormalized
}

private fun Boolean.bit(position: Int): UInt = if (this) 1u shl position else 0u
