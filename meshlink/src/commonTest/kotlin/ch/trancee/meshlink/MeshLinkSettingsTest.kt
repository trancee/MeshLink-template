package ch.trancee.meshlink

import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.ScoreboardEncoding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MeshLinkSettingsTest {
    @Test
    fun `default MeshLinkSettings`() {
        val settings = MeshLinkSettingsBuilder().build()
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
        assertEquals(true, settings.diagnostics.emitToLog)
        assertEquals(1000, settings.diagnostics.eventBufferSize)
    }

    @Test
    fun `custom MeshLinkSettings`() {
        val builder = MeshLinkSettingsBuilder()
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
        builder.diagnosticsEmitToLog = false
        builder.diagnosticsBufferSize = 500
        val settings = builder.build()
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
        assertEquals(false, settings.diagnostics.emitToLog)
        assertEquals(500, settings.diagnostics.eventBufferSize)
    }
}
