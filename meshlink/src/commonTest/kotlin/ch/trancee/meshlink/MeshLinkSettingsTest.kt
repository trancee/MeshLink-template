@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package ch.trancee.meshlink

import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.ScoreboardEncoding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MeshLinkSettingsTest {
    @Test
    fun `default MeshLinkSettings via imperative builder`() {
        val settings = MeshLinkSettingsBuilder().build()
        assertEquals("", settings.appId)
        assertEquals(PowerMode.MEDIUM, settings.powerMode)
        assertEquals(RegulatoryRegion.DEFAULT, settings.regulatoryRegion)
        assertEquals(3.days, settings.keyRotation.interval)
        assertEquals(1.hours, settings.keyRotation.rotationGracePeriod)
        assertEquals(Duration.ZERO, settings.keyRotation.compromiseGracePeriod)
        assertEquals(5, settings.transfer.maxRetries)
        assertEquals(256, settings.transfer.chunkSize)
        assertEquals(3, settings.transfer.maxConcurrentSessionsPerPeer)
        assertEquals(ScoreboardEncoding.DYNAMIC, settings.transfer.scoreboardEncoding)
        assertEquals(1024u, settings.transfer.maxChunksPerSession)
        assertEquals(1.seconds, settings.routing.routeUpdateMinInterval)
        assertEquals(30.seconds, settings.routing.routeUpdateMaxInterval)
        assertEquals(3, settings.routing.routeUpdateChangeThreshold)
        assertEquals(5.minutes, settings.routing.fullTableSyncInterval)
        assertEquals(15.minutes, settings.routing.routeEntryExpiry)
        assertEquals(true, settings.routing.feasibilityConditionEnabled)
        assertEquals(256, settings.routing.maxRouteEntries)
        assertEquals(3, settings.security.fallbackMaxAttemptsPerMinute)
        assertEquals(10.seconds, settings.security.fallbackTimeout)
        assertEquals(true, settings.security.requireSignatureOnRouteUpdates)
        assertEquals(HandshakePattern.IX, settings.security.defaultHandshakePattern)
        assertEquals(1000, settings.diagnostics.eventBufferSize)
        assertEquals(false, settings.emitToLog)
        assertNull(settings.eventCallback)
    }

    @Test
    fun `custom MeshLinkSettings via imperative builder`() {
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.app"
        builder.powerMode = PowerMode.HIGH
        builder.regulatoryRegion = RegulatoryRegion.EU
        builder.keyRotationInterval = 1.days
        builder.keyRotationGracePeriod = 2.hours
        builder.keyRotationCompromiseGracePeriod = Duration.ZERO
        builder.transferMaxRetries = 10
        builder.transferChunkSize = 512
        builder.routingMinInterval = 5.seconds
        builder.routingMaxEntries = 100
        builder.securityFallbackAttempts = 1
        builder.securityDefaultHandshakePattern = HandshakePattern.NX
        builder.diagnosticsBufferSize = 500
        builder.emitToLog = true
        val settings = builder.build()
        assertEquals("com.example.app", settings.appId)
        assertEquals(PowerMode.HIGH, settings.powerMode)
        assertEquals(RegulatoryRegion.EU, settings.regulatoryRegion)
        assertEquals(1.days, settings.keyRotation.interval)
        assertEquals(2.hours, settings.keyRotation.rotationGracePeriod)
        assertEquals(Duration.ZERO, settings.keyRotation.compromiseGracePeriod)
        assertEquals(10, settings.transfer.maxRetries)
        assertEquals(512, settings.transfer.chunkSize)
        assertEquals(5.seconds, settings.routing.routeUpdateMinInterval)
        assertEquals(100, settings.routing.maxRouteEntries)
        assertEquals(1, settings.security.fallbackMaxAttemptsPerMinute)
        assertEquals(HandshakePattern.NX, settings.security.defaultHandshakePattern)
        assertEquals(500, settings.diagnostics.eventBufferSize)
        assertEquals(true, settings.emitToLog)
    }

    @Test
    fun `lambda DSL produces same settings as imperative builder`() {
        val settings = meshLinkSettings {
            appId = "com.example.app"
            powerMode = PowerMode.HIGH
            regulatoryRegion = RegulatoryRegion.EU
            keyRotation {
                interval = 1.days
                rotationGracePeriod = 30.minutes
                compromiseGracePeriod = Duration.ZERO
            }
            transfer {
                maxRetries = 3
                chunkSize = 512
                maxConcurrentSessionsPerPeer = 2
            }
            routing {
                routeUpdateMinInterval = 1.seconds
                routeUpdateMaxInterval = 30.seconds
                routeUpdateChangeThreshold = 3
                fullTableSyncInterval = 5.minutes
                routeEntryExpiry = 15.minutes
                feasibilityConditionEnabled = true
                maxRouteEntries = 256
            }
            security {
                fallbackMaxAttemptsPerMinute = 3
                fallbackTimeout = 10.seconds
                requireSignatureOnRouteUpdates = true
                defaultHandshakePattern = HandshakePattern.IX
            }
            diagnostics { eventBufferSize = 1000 }
            emitToLog = true
        }
        assertEquals("com.example.app", settings.appId)
        assertEquals(PowerMode.HIGH, settings.powerMode)
        assertEquals(RegulatoryRegion.EU, settings.regulatoryRegion)
        assertEquals(1.days, settings.keyRotation.interval)
        assertEquals(30.minutes, settings.keyRotation.rotationGracePeriod)
        assertEquals(Duration.ZERO, settings.keyRotation.compromiseGracePeriod)
        assertEquals(3, settings.transfer.maxRetries)
        assertEquals(512, settings.transfer.chunkSize)
        assertEquals(2, settings.transfer.maxConcurrentSessionsPerPeer)
        assertEquals(1.seconds, settings.routing.routeUpdateMinInterval)
        assertEquals(256, settings.routing.maxRouteEntries)
        assertEquals(true, settings.emitToLog)
    }

    @Test
    fun `lambda DSL with eventCallback`() {
        var received: ch.trancee.meshlink.diagnostics.DiagnosticEvent? = null
        val settings = meshLinkSettings { eventCallback = { event -> received = event } }
        assertNull(received) // callback not invoked during build
        settings.eventCallback?.let {
            it(
                ch.trancee.meshlink.diagnostics.DiagnosticEvent.TransportFallbackEvent(
                    peerIdentity = ch.trancee.meshlink.model.PeerIdentity.ZERO,
                    reason = ch.trancee.meshlink.model.TransportFallbackReason.NO_PSM_ADVERTISED,
                )
            )
        }
        assert(received != null)
    }
}
