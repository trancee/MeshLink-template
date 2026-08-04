package ch.trancee.meshlink

import ch.trancee.meshlink.model.PowerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        assertEquals(100.milliseconds, settings.advertisementInterval)
        assertEquals((7.5).milliseconds, settings.activeConnectionInterval)
        assertEquals(15.milliseconds, settings.idleConnectionInterval)
        assertEquals(60.seconds, settings.idleTransitionDelay)
        assertEquals(8, settings.concurrentConnectionLimit)
        assertEquals(512, settings.chunkSize)
        assertEquals(10, settings.retryLimit)
        assertEquals(60.seconds, settings.retryBudget)
        assertEquals(15.seconds, settings.disconnectGracePeriod)
    }

    @Test
    fun `LOW settings has all expected values`() {
        val settings = PowerMode.LOW.settings
        assertEquals(5, settings.scanDutyCycle)
        assertEquals(1000.milliseconds, settings.advertisementInterval)
        assertEquals(30.milliseconds, settings.activeConnectionInterval)
        assertEquals(60.milliseconds, settings.idleConnectionInterval)
        assertEquals(300.seconds, settings.idleTransitionDelay)
        assertEquals(2, settings.concurrentConnectionLimit)
        assertEquals(128, settings.chunkSize)
        assertEquals(3, settings.retryLimit)
        assertEquals(15.seconds, settings.retryBudget)
        assertEquals(45.seconds, settings.disconnectGracePeriod)
    }

    @Test
    fun `MEDIUM settings has all expected values`() {
        // Arrange
        val settings = PowerMode.MEDIUM.settings

        // Act & Assert
        assertEquals(10, settings.scanDutyCycle)
        assertEquals(500.milliseconds, settings.advertisementInterval)
        assertEquals(15.milliseconds, settings.activeConnectionInterval)
        assertEquals(30.milliseconds, settings.idleConnectionInterval)
        assertEquals(120.seconds, settings.idleTransitionDelay)
        assertEquals(4, settings.concurrentConnectionLimit)
        assertEquals(256, settings.chunkSize)
        assertEquals(5, settings.retryLimit)
        assertEquals(30.seconds, settings.retryBudget)
        assertEquals(30.seconds, settings.disconnectGracePeriod)
    }
}
