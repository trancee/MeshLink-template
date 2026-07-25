package ch.trancee.meshlink.model

import kotlinx.serialization.Serializable

/** Power tier governing BLE radio operation parameters. */
@Serializable
enum class PowerTier {
    HIGH,
    MEDIUM,
    LOW;

    /** Returns the configuration parameters for this power tier. */
    val config: PowerTierConfig
        get() =
            when (this) {
                HIGH -> HIGH_CONFIG
                MEDIUM -> MEDIUM_CONFIG
                LOW -> LOW_CONFIG
            }

    companion object {
        val HIGH_CONFIG =
            PowerTierConfig(
                scanDutyCyclePercent = 20,
                advertisementIntervalMs = 100,
                connectionIntervalMs = 7,
                concurrentConnections = 8,
                chunkSize = 512,
                maxRetries = 10,
                retryBudgetSeconds = 60,
                gracePeriodSeconds = 15,
            )

        val MEDIUM_CONFIG =
            PowerTierConfig(
                scanDutyCyclePercent = 10,
                advertisementIntervalMs = 500,
                connectionIntervalMs = 15,
                concurrentConnections = 4,
                chunkSize = 256,
                maxRetries = 5,
                retryBudgetSeconds = 30,
                gracePeriodSeconds = 30,
            )

        val LOW_CONFIG =
            PowerTierConfig(
                scanDutyCyclePercent = 5,
                advertisementIntervalMs = 1000,
                connectionIntervalMs = 30,
                concurrentConnections = 2,
                chunkSize = 128,
                maxRetries = 3,
                retryBudgetSeconds = 15,
                gracePeriodSeconds = 45,
            )
    }
}

/** Configuration parameters for a [PowerTier]. */
@Serializable
data class PowerTierConfig(
    /** Scan duty cycle as a percentage of the BLE connection interval. */
    val scanDutyCyclePercent: Int,
    /** Minimum advertisement interval in milliseconds. */
    val advertisementIntervalMs: Int,
    /** Connection interval in milliseconds. */
    val connectionIntervalMs: Int,
    /** Maximum number of concurrent BLE connections this tier supports. */
    val concurrentConnections: Int,
    /** Transfer chunk size in bytes, bounded by peer MTU. */
    val chunkSize: Int,
    /** Maximum number of retransmit attempts per chunk. */
    val maxRetries: Int,
    /** Time budget for retry attempts before marking a transfer as timed out. */
    val retryBudgetSeconds: Int,
    /** Grace period after disconnection before a peer transitions to GONE. */
    val gracePeriodSeconds: Int,
)
