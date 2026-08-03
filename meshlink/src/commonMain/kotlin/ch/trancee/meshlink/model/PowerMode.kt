package ch.trancee.meshlink.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
                HIGH -> high()
                MEDIUM -> medium()
                LOW -> low()
            }

    public companion object {
        /** Settings for HIGH power mode. */
        internal fun high(): PowerModeSettings =
            PowerModeSettings(
                scanDutyCycle = 20,
                advertisementInterval = 100.milliseconds,
                activeConnectionInterval = (7.5).milliseconds,
                idleConnectionInterval = 15.milliseconds,
                concurrentConnectionLimit = 8,
                chunkSize = 512,
                retryLimit = 10,
                retryBudget = 60.seconds,
                disconnectGracePeriod = 15.seconds,
            )

        /** Settings for MEDIUM power mode. */
        internal fun medium(): PowerModeSettings =
            PowerModeSettings(
                scanDutyCycle = 10,
                advertisementInterval = 500.milliseconds,
                activeConnectionInterval = 15.milliseconds,
                idleConnectionInterval = 30.milliseconds,
                concurrentConnectionLimit = 4,
                chunkSize = 256,
                retryLimit = 5,
                retryBudget = 30.seconds,
                disconnectGracePeriod = 30.seconds,
            )

        /** Settings for LOW power mode. */
        internal fun low(): PowerModeSettings =
            PowerModeSettings(
                scanDutyCycle = 5,
                advertisementInterval = 1000.milliseconds,
                activeConnectionInterval = 30.milliseconds,
                idleConnectionInterval = 60.milliseconds,
                concurrentConnectionLimit = 2,
                chunkSize = 128,
                retryLimit = 3,
                retryBudget = 15.seconds,
                disconnectGracePeriod = 45.seconds,
            )
    }
}

/** Settings parameters for a [PowerMode]. */
public data class PowerModeSettings(
    /** Scan duty cycle as a percentage of the BLE connection interval. */
    public val scanDutyCycle: Int,
    /** Minimum advertisement interval. */
    public val advertisementInterval: Duration,
    /** Active connection interval. */
    public val activeConnectionInterval: Duration,
    /** Idle connection interval. */
    public val idleConnectionInterval: Duration,
    /** Maximum number of concurrent BLE connections this mode supports. */
    public val concurrentConnectionLimit: Int,
    /** Transfer chunk size in bytes, bounded by peer MTU. */
    public val chunkSize: Int,
    /** Maximum number of retransmit attempts per chunk. */
    public val retryLimit: Int,
    /** Time budget for retry attempts before marking a transfer as timed out. */
    public val retryBudget: Duration,
    /** Grace period after disconnection before a peer transitions to GONE. */
    public val disconnectGracePeriod: Duration,
)
