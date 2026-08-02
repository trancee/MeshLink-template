package ch.trancee.meshlink

import ch.trancee.meshlink.model.PowerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PowerModeTest {
    @Test
    fun `settings values`() {
        assertEquals(20, PowerMode.HIGH.settings.scanDutyCycle)
        assertEquals(10, PowerMode.MEDIUM.settings.scanDutyCycle)
        assertEquals(5, PowerMode.LOW.settings.scanDutyCycle)
    }

    @Test
    fun `all entries have non-null name`() {
        PowerMode.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `HIGH settings has all expected values`() {
        val settings = PowerMode.HIGH.settings
        assertEquals(20, settings.scanDutyCycle)
        assertEquals(100, settings.advertisementInterval)
        assertEquals(7.5, settings.activeConnectionInterval)
        assertEquals(15.0, settings.idleConnectionInterval)
        assertEquals(8, settings.concurrentConnectionLimit)
        assertEquals(512, settings.chunkSize)
        assertEquals(10, settings.retryLimit)
        assertEquals(60, settings.retryBudget)
        assertEquals(15, settings.disconnectGracePeriod)
    }

    @Test
    fun `LOW settings has all expected values`() {
        val settings = PowerMode.LOW.settings
        assertEquals(5, settings.scanDutyCycle)
        assertEquals(1000, settings.advertisementInterval)
        assertEquals(30.0, settings.activeConnectionInterval)
        assertEquals(60.0, settings.idleConnectionInterval)
        assertEquals(2, settings.concurrentConnectionLimit)
        assertEquals(128, settings.chunkSize)
        assertEquals(3, settings.retryLimit)
        assertEquals(15, settings.retryBudget)
        assertEquals(45, settings.disconnectGracePeriod)
    }

    @Test
    fun `HIGH_SETTINGS scanDutyCycle`() {
        assertEquals(20, PowerMode.HIGH_SETTINGS.scanDutyCycle)
    }

    @Test
    fun `MEDIUM_SETTINGS scanDutyCycle`() {
        assertEquals(10, PowerMode.MEDIUM_SETTINGS.scanDutyCycle)
    }

    @Test
    fun `LOW_SETTINGS scanDutyCycle`() {
        assertEquals(5, PowerMode.LOW_SETTINGS.scanDutyCycle)
    }
}
