package ch.trancee.meshlink

import ch.trancee.meshlink.model.PowerTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PowerTierTest {
    @Test
    fun `config values`() {
        assertEquals(20, PowerTier.HIGH.config.scanDutyCyclePercent)
        assertEquals(10, PowerTier.MEDIUM.config.scanDutyCyclePercent)
        assertEquals(5, PowerTier.LOW.config.scanDutyCyclePercent)
    }

    @Test
    fun `all entries have non-null name`() {
        PowerTier.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `HIGH config has all expected values`() {
        val config = PowerTier.HIGH.config
        assertEquals(20, config.scanDutyCyclePercent)
        assertEquals(100, config.advertisementIntervalMs)
        assertEquals(7.5, config.connectionIntervalMs)
        assertEquals(8, config.concurrentConnections)
        assertEquals(512, config.chunkSize)
        assertEquals(10, config.maxRetries)
        assertEquals(60, config.retryBudgetSeconds)
        assertEquals(15, config.gracePeriodSeconds)
    }

    @Test
    fun `LOW config has all expected values`() {
        val config = PowerTier.LOW.config
        assertEquals(5, config.scanDutyCyclePercent)
        assertEquals(1000, config.advertisementIntervalMs)
        assertEquals(30.0, config.connectionIntervalMs)
        assertEquals(2, config.concurrentConnections)
        assertEquals(128, config.chunkSize)
        assertEquals(3, config.maxRetries)
        assertEquals(15, config.retryBudgetSeconds)
        assertEquals(45, config.gracePeriodSeconds)
    }

    @Test
    fun `HIGH_CONFIG scanDutyCyclePercent`() {
        assertEquals(20, PowerTier.HIGH_CONFIG.scanDutyCyclePercent)
    }

    @Test
    fun `MEDIUM_CONFIG scanDutyCyclePercent`() {
        assertEquals(10, PowerTier.MEDIUM_CONFIG.scanDutyCyclePercent)
    }

    @Test
    fun `LOW_CONFIG scanDutyCyclePercent`() {
        assertEquals(5, PowerTier.LOW_CONFIG.scanDutyCyclePercent)
    }
}
