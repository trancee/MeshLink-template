package ch.trancee.meshlink

import ch.trancee.meshlink.model.PowerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PowerModeTest {
    @Test
    fun `settings values`() {
        assertEquals(20, PowerMode.HIGH.settings.scanDutyCyclePercent)
        assertEquals(10, PowerMode.MEDIUM.settings.scanDutyCyclePercent)
        assertEquals(5, PowerMode.LOW.settings.scanDutyCyclePercent)
    }

    @Test
    fun `all entries have non-null name`() {
        PowerMode.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `HIGH settings has all expected values`() {
        val settings = PowerMode.HIGH.settings
        assertEquals(20, settings.scanDutyCyclePercent)
        assertEquals(100, settings.advertisementIntervalMs)
        assertEquals(7.5, settings.connectionIntervalMs)
        assertEquals(8, settings.concurrentConnections)
        assertEquals(512, settings.chunkSize)
        assertEquals(10, settings.maxRetries)
        assertEquals(60, settings.retryBudgetSeconds)
        assertEquals(15, settings.gracePeriodSeconds)
    }

    @Test
    fun `LOW settings has all expected values`() {
        val settings = PowerMode.LOW.settings
        assertEquals(5, settings.scanDutyCyclePercent)
        assertEquals(1000, settings.advertisementIntervalMs)
        assertEquals(30.0, settings.connectionIntervalMs)
        assertEquals(2, settings.concurrentConnections)
        assertEquals(128, settings.chunkSize)
        assertEquals(3, settings.maxRetries)
        assertEquals(15, settings.retryBudgetSeconds)
        assertEquals(45, settings.gracePeriodSeconds)
    }

    @Test
    fun `HIGH_SETTINGS scanDutyCyclePercent`() {
        assertEquals(20, PowerMode.HIGH_SETTINGS.scanDutyCyclePercent)
    }

    @Test
    fun `MEDIUM_SETTINGS scanDutyCyclePercent`() {
        assertEquals(10, PowerMode.MEDIUM_SETTINGS.scanDutyCyclePercent)
    }

    @Test
    fun `LOW_SETTINGS scanDutyCyclePercent`() {
        assertEquals(5, PowerMode.LOW_SETTINGS.scanDutyCyclePercent)
    }
}
