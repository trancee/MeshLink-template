package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PowerModeSettingsTest {

    @Test
    fun `PowerModeSettings has all parameters`() {
        val settings =
            PowerModeSettings(
                scanDutyCycle = 20,
                advertisementInterval = 100.milliseconds,
                activeConnectionInterval = 15.milliseconds,
                idleConnectionInterval = 500.milliseconds,
                idleTransitionDelay = 60.seconds,
                concurrentConnectionLimit = 8,
                chunkSize = 512,
                retryLimit = 10,
                retryBudget = 60.seconds,
                disconnectGracePeriod = 15.seconds,
            )

        assertEquals(20, settings.scanDutyCycle)
        assertEquals(8, settings.concurrentConnectionLimit)
        assertEquals(512, settings.chunkSize)
        assertEquals(60.seconds, settings.idleTransitionDelay)
    }
}
