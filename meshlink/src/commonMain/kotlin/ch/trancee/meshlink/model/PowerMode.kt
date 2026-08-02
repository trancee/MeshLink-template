package ch.trancee.meshlink.model

/**
 * Power mode governing BLE radio operation parameters.
 *
 * SPEC-ANCHOR: power-mode-settings
 */
public enum class PowerMode {
    HIGH,
    MEDIUM,
    LOW;

    /** Returns the settings for this power mode. */
    public val settings: PowerModeSettings
        get() =
            when (this) {
                HIGH -> HIGH_SETTINGS
                MEDIUM -> MEDIUM_SETTINGS
                LOW -> LOW_SETTINGS
            }

    public companion object {
        /** Settings for HIGH power mode. */
        public val HIGH_SETTINGS: PowerModeSettings =
            PowerModeSettings(
                scanDutyCycle = 20,
                advertisementInterval = 100,
                activeConnectionInterval = 7.5,
                idleConnectionInterval = 15.0,
                concurrentConnectionLimit = 8,
                chunkSize = 512,
                retryLimit = 10,
                retryBudget = 60,
                disconnectGracePeriod = 15,
            )

        /** Settings for MEDIUM power mode. */
        public val MEDIUM_SETTINGS: PowerModeSettings =
            PowerModeSettings(
                scanDutyCycle = 10,
                advertisementInterval = 500,
                activeConnectionInterval = 15.0,
                idleConnectionInterval = 30.0,
                concurrentConnectionLimit = 4,
                chunkSize = 256,
                retryLimit = 5,
                retryBudget = 30,
                disconnectGracePeriod = 30,
            )

        /** Settings for LOW power mode. */
        public val LOW_SETTINGS: PowerModeSettings =
            PowerModeSettings(
                scanDutyCycle = 5,
                advertisementInterval = 1000,
                activeConnectionInterval = 30.0,
                idleConnectionInterval = 60.0,
                concurrentConnectionLimit = 2,
                chunkSize = 128,
                retryLimit = 3,
                retryBudget = 15,
                disconnectGracePeriod = 45,
            )
    }
}

/** Settings parameters for a [PowerMode]. */
public data class PowerModeSettings(
    /** Scan duty cycle as a percentage of the BLE connection interval. */
    public val scanDutyCycle: Int,
    /** Minimum advertisement interval in milliseconds. */
    public val advertisementInterval: Int,
    /** Active connection interval in milliseconds. */
    public val activeConnectionInterval: Double,
    /** Idle connection interval in milliseconds. */
    public val idleConnectionInterval: Double,
    /** Maximum number of concurrent BLE connections this mode supports. */
    public val concurrentConnectionLimit: Int,
    /** Transfer chunk size in bytes, bounded by peer MTU. */
    public val chunkSize: Int,
    /** Maximum number of retransmit attempts per chunk. */
    public val retryLimit: Int,
    /** Time budget for retry attempts before marking a transfer as timed out. */
    public val retryBudget: Int,
    /** Grace period after disconnection before a peer transitions to GONE. */
    public val disconnectGracePeriod: Int,
)
