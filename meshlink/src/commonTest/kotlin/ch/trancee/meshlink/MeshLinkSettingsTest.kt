package ch.trancee.meshlink

import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MeshLinkSettingsTest {
    @Test
    fun `valid settings retain accepted defaults`() {
        // Arrange
        val settings = meshLinkSettings { appId = "com.example.mesh" }

        // Act
        val actual = settings

        // Assert
        assertEquals("com.example.mesh", actual.appId)
        assertEquals(PowerMode.MEDIUM, actual.powerMode)
        assertEquals(RegulatoryRegion.DEFAULT, actual.regulatoryRegion)
        assertFalse(actual.isBackground)
        assertEquals(3, actual.transfer.maxTransfersPerPeer)
        assertEquals(5.minutes, actual.routing.routeDigestInterval)
        assertEquals(15.minutes, actual.routing.routeExpiry)
        assertEquals(256, actual.routing.maxRoutes)
        assertEquals(1000, actual.diagnostics.eventBufferSize)
        assertFalse(actual.diagnostics.emitLog)
    }

    @Test
    fun `imperative builder retains default logging choice`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        assertFalse(builder.diagnosticsEmitLog)

        // Act
        val actual = builder.build()

        // Assert
        assertFalse(actual.diagnostics.emitLog)
    }

    @Test
    fun `custom settings preserve background and diagnostics choices`() {
        // Arrange
        val settings = meshLinkSettings {
            appId = "com.example.mesh.dev"
            powerMode = PowerMode.HIGH
            regulatoryRegion = RegulatoryRegion.EU
            isBackground = true
            transfer { maxTransfersPerPeer = 2 }
            routing {
                routeAdvertisementChangeThreshold = 5
                routeDigestInterval = 2.minutes
                routeExpiry = 10.minutes
                maxRoutes = 100
            }
            diagnostics {
                eventBufferSize = 500
                emitLog = true
            }
        }

        // Act
        val actual = settings

        // Assert
        assertEquals(PowerMode.HIGH, actual.powerMode)
        assertEquals(RegulatoryRegion.EU, actual.regulatoryRegion)
        assertTrue(actual.isBackground)
        assertEquals(2, actual.transfer.maxTransfersPerPeer)
        assertEquals(5, actual.routing.routeAdvertisementChangeThreshold)
        assertEquals(2.minutes, actual.routing.routeDigestInterval)
        assertEquals(10.minutes, actual.routing.routeExpiry)
        assertEquals(100, actual.routing.maxRoutes)
        assertEquals(500, actual.diagnostics.eventBufferSize)
        assertTrue(actual.diagnostics.emitLog)
    }
}
