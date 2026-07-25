package ch.trancee.meshlink.model

/** Power tier governing BLE radio operation parameters. */
public enum class PowerTier {
    HIGH,
    MEDIUM,
    LOW;

    /** Returns the configuration parameters for this power tier. */
    public val config: PowerTierConfig
        get() =
            when (this) {
                HIGH -> HIGH_CONFIG
                MEDIUM -> MEDIUM_CONFIG
                LOW -> LOW_CONFIG
            }

    public companion object {
        public val HIGH_CONFIG: PowerTierConfig =
            PowerTierConfig(
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

        public val MEDIUM_CONFIG: PowerTierConfig =
            PowerTierConfig(
                scanDutyCyclePercent = 10,
                advertisementIntervalMs = 500,
                connectionIntervalMs = 15.0,
                concurrentConnections = 4,
                chunkSize = 256,
                maxRetries = 5,
                retryBudgetSeconds = 30,
                gracePeriodSeconds = 30,
            )

        public val LOW_CONFIG: PowerTierConfig =
            PowerTierConfig(
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

/** Configuration parameters for a [PowerTier]. */
public data class PowerTierConfig(
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
