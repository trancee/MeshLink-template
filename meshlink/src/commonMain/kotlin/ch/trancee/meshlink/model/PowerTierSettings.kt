package ch.trancee.meshlink.model

/**
 * Power tier governing BLE radio operation parameters.
 *
 * SPEC-ANCHOR: power-tier-settings
 */
public enum class PowerTier {
    HIGH,
    MEDIUM,
    LOW;

    /** Returns the settings for this power tier. */
    public val settings: PowerTierSettings
        get() =
            when (this) {
                HIGH -> HIGH_SETTINGS
                MEDIUM -> MEDIUM_SETTINGS
                LOW -> LOW_SETTINGS
            }

    public companion object {
        /** Settings for HIGH power tier. */
        public val HIGH_SETTINGS: PowerTierSettings =
            PowerTierSettings(
                scanDutyCyclePercent = 20,
                advertisementIntervalMs = 100,
                // 7.5ms = minimum valid BLE connection interval (6 × 1.25ms);
                // Android BLE stack floor
                connectionIntervalMs = 7.5,
                concurrentConnections = 8,
                chunkSize = 512,
                maxRetries = 10,
                retryBudgetSeconds = 60,
                gracePeriodSeconds = 15,
            )

        /** Settings for MEDIUM power tier. */
        public val MEDIUM_SETTINGS: PowerTierSettings =
            PowerTierSettings(
                scanDutyCyclePercent = 10,
                advertisementIntervalMs = 500,
                connectionIntervalMs = 15.0,
                concurrentConnections = 4,
                chunkSize = 256,
                maxRetries = 5,
                retryBudgetSeconds = 30,
                gracePeriodSeconds = 30,
            )

        /** Settings for LOW power tier. */
        public val LOW_SETTINGS: PowerTierSettings =
            PowerTierSettings(
                scanDutyCyclePercent = 5,
                advertisementIntervalMs = 1000,
                connectionIntervalMs = 30.0,
                concurrentConnections = 2,
                chunkSize = 128,
                maxRetries = 3,
                retryBudgetSeconds = 15,
                gracePeriodSeconds = 45,
            )
    }
}

/** Settings parameters for a [PowerTier]. */
public data class PowerTierSettings(
    /** Scan duty cycle as a percentage of the BLE connection interval. */
    public val scanDutyCyclePercent: Int,
    /** Minimum advertisement interval in milliseconds. */
    public val advertisementIntervalMs: Int,
    /** Connection interval in milliseconds. */
    public val connectionIntervalMs: Double,
    /** Maximum number of concurrent BLE connections this tier supports. */
    public val concurrentConnections: Int,
    /** Transfer chunk size in bytes, bounded by peer MTU. */
    public val chunkSize: Int,
    /** Maximum number of retransmit attempts per chunk. */
    public val maxRetries: Int,
    /** Time budget for retry attempts before marking a transfer as timed out. */
    public val retryBudgetSeconds: Int,
    /** Grace period after disconnection before a peer transitions to GONE. */
    public val gracePeriodSeconds: Int,
)
