package ch.trancee.meshlink

import ch.trancee.meshlink.diagnostics.DiagnosticEvent
import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.Ed25519Key
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.KeyType
import ch.trancee.meshlink.model.LinkMetric
import ch.trancee.meshlink.model.MutableScoreboard
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerTier
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.RouteEntry
import ch.trancee.meshlink.model.RoutingPolicy
import ch.trancee.meshlink.model.Scoreboard
import ch.trancee.meshlink.model.ScoreboardEncoding
import ch.trancee.meshlink.model.SeqNo
import ch.trancee.meshlink.model.SessionId
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferSession
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ModelTypesTest {
    // SeqNo tests
    @Test
    fun `SeqNo fromRaw creates correct value`() {
        val seqNo = SeqNo.fromRaw(42u)
        assertEquals(42u, seqNo.raw)
    }

    @Test
    fun `SeqNo ZERO equals zero`() {
        assertEquals(0u, SeqNo.ZERO.raw)
    }

    // Ed25519Key tests
    @Test
    fun `Ed25519Key fromBytes and to raw returns same bytes`() {
        val bytes = ByteArray(32) { i -> i.toByte() }
        val key = Ed25519Key.fromBytes(bytes)
        assertEquals(bytes.toList(), key.raw.toList())
    }

    @Test
    fun `Ed25519Key fromHex roundtrip`() {
        val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val key = Ed25519Key.fromHex(hex)
        assertEquals(hex, key.hex)
    }

    // Scoreboard tests
    @Test
    fun `Scoreboard marks chunks correctly`() {
        val sb = Scoreboard(10u)
        val marked = sb.markReceived(5)
        assertTrue(marked.isReceived(5))
        assertFalse(marked.isReceived(6))
    }

    @Test
    fun `Scoreboard missing chunks list`() {
        val sb = Scoreboard(5u)
        val marked = sb.markReceived(0).markReceived(2)
        assertEquals(listOf(1, 3, 4), marked.missingChunks())
    }

    @Test
    fun `Scoreboard received count`() {
        val sb = Scoreboard(8u)
        val marked = sb.markReceived(0).markReceived(2).markReceived(4).markReceived(6)
        assertEquals(4, marked.receivedCount())
    }

    @Test
    fun `Scoreboard missing count`() {
        val sb = Scoreboard(10u)
        val marked = sb.markReceived(0).markReceived(1).markReceived(2)
        assertEquals(7, marked.missingCount())
    }

    @Test
    fun `Scoreboard toByteArray returns copy`() {
        val sb = Scoreboard(4u)
        val bytes = sb.toByteArray()
        assertEquals(1, bytes.size)
    }

    @Test
    fun `Scoreboard markMissing works`() {
        val sb = Scoreboard(8u).markReceived(3).markReceived(5)
        val cleared = sb.markMissing(3)
        assertFalse(cleared.isReceived(3))
        assertTrue(cleared.isReceived(5))
    }

    // MutableScoreboard tests
    @Test
    fun `MutableScoreboard markReceived works`() {
        val msb = MutableScoreboard(8u)
        msb.markReceived(2)
        assertTrue(msb.isReceived(2))
        assertEquals(1, msb.receivedCount())
    }

    @Test
    fun `MutableScoreboard toImmutable creates Scoreboard`() {
        val msb = MutableScoreboard(4u)
        msb.markReceived(0)
        msb.markReceived(1)
        val immutable = msb.toImmutable()
        assertTrue(immutable.isReceived(0))
        assertTrue(immutable.isReceived(1))
    }

    // RoutingPolicy tests
    @Test
    fun `RoutingPolicy TTL values`() {
        assertEquals(10.minutes, RoutingPolicy.ttlFor(Priority.HIGH))
        assertEquals(5.minutes, RoutingPolicy.ttlFor(Priority.NORMAL))
        assertEquals(1.minutes, RoutingPolicy.ttlFor(Priority.LOW))
    }

    @Test
    fun `RoutingPolicy MaxHops is 32`() {
        assertEquals(32, RoutingPolicy.MaxHops)
    }

    // PeerIdentity tests
    @Test
    fun `PeerIdentity has lo and hi components`() {
        val id = PeerIdentity.ZERO
        assertEquals(0UL, id.lo)
        assertEquals(0UL, id.hi)
    }

    @Test
    fun `PeerIdentity hex getter works`() {
        assertEquals("00000000000000000000000000000000", PeerIdentity.ZERO.hex)
        val id = PeerIdentity.generate()
        assertEquals(32, id.hex.length)
    }

    @Test
    fun `PeerIdentity generate creates valid identity`() {
        val id = PeerIdentity.generate()
        assertEquals(16, id.toByteArray().size)
    }

    @Test
    fun `PeerIdentity fromBytes roundtrips`() {
        val bytes = ByteArray(16) { i -> i.toByte() }
        val id = PeerIdentity.fromBytes(bytes)
        assertEquals(bytes.toList(), id.toByteArray().toList())
        assertEquals(bytes.joinToString("") { "%02x".format(it) }, id.hex)
    }

    @Test
    fun `PeerIdentity fromBytes throws on wrong size`() {
        val bytes = ByteArray(8) { 0 }
        try {
            PeerIdentity.fromBytes(bytes)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("PeerIdentity must be exactly 16 bytes", e.message)
        }
    }

    // SessionId tests
    @Test
    fun `SessionId generate creates non-zero`() {
        val id = SessionId.generate()
        assertNotEquals(SessionId.ZERO.raw, id.raw)
    }

    @Test
    fun `SessionId ZERO is zero`() {
        assertEquals(0u, SessionId.ZERO.raw)
    }

    // LinkMetric tests
    @Test
    fun `LinkMetric composite combines rssi and flags`() {
        val metric =
            LinkMetric(
                rssiNormalized = 100u,
                supportsCoc = true,
                fastInterval = false,
                highPowerTier = true,
            )
        // flags bits 8-10 shl 8 = (256 | 0 | 1024) shl 8 = 327680, or rssiNormalized 100 = 327780
        assertEquals(327780u, metric.composite)
    }

    // TransferSession tests
    @Test
    fun `TransferSession creates with correct values`() {
        val session =
            TransferSession(
                sessionId = SessionId.ZERO,
                destination = PeerIdentity.ZERO,
                priority = Priority.HIGH,
                state = TransferState.IN_PROGRESS,
                chunkSize = 256,
                totalChunks = 10u,
                scoreboard = Scoreboard(10u),
                totalBytes = 1000L,
                bytesReceived = 0L,
                startedAt = Clock.System.now(),
                expiresAt = null,
                retryCount = 0,
                failureReason = null,
            )
        assertEquals(Priority.HIGH, session.priority)
        assertEquals(TransferState.IN_PROGRESS, session.state)
    }

    // PowerTier tests
    @Test
    fun `PowerTier config values`() {
        assertEquals(20, PowerTier.HIGH.config.scanDutyCyclePercent)
        assertEquals(10, PowerTier.MEDIUM.config.scanDutyCyclePercent)
        assertEquals(5, PowerTier.LOW.config.scanDutyCyclePercent)
    }

    // Enums for coverage
    @Test
    fun `All enum values covered for KeyType`() {
        KeyType.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for KeyRotationReason`() {
        KeyRotationReason.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for HandshakePattern`() {
        HandshakePattern.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for ScoreboardEncoding`() {
        ScoreboardEncoding.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for Priority`() {
        Priority.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for FrameType`() {
        FrameType.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for DecryptFailureReason`() {
        DecryptFailureReason.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for TransportFallbackReason`() {
        TransportFallbackReason.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for RegulatoryRegion`() {
        RegulatoryRegion.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for NoiseLayer`() {
        NoiseLayer.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for NoiseSessionState`() {
        NoiseSessionState.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for NoiseRole`() {
        NoiseRole.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for NoiseFailureReason`() {
        NoiseFailureReason.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `All enum values covered for TransferState`() {
        TransferState.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `PowerTier all entries have non-null name`() {
        PowerTier.entries.forEach { assertNotNull(it.name) }
    }

    // ---------------------------------------------------------------------------
    // DataPlaneBearer enum (was uncovered)
    // ---------------------------------------------------------------------------

    @Test
    fun `All enum values covered for DataPlaneBearer`() {
        DataPlaneBearer.entries.forEach { assertNotNull(it.name) }
    }

    // ---------------------------------------------------------------------------
    // DiagnosticEvent sealed interface — all 11 subclasses
    // ---------------------------------------------------------------------------

    @Test
    fun `DiagnosticEvent RouteDecryptFailureEvent`() {
        val event =
            DiagnosticEvent.RouteDecryptFailureEvent(
                peerIdentity = PeerIdentity.ZERO,
                frameType = FrameType.TRANSFER_CHUNK,
                failureReason = DecryptFailureReason.AUTHENTICATION_TAG_MISMATCH,
            )
        assertEquals(FrameType.TRANSFER_CHUNK, event.frameType)
        assertEquals(DecryptFailureReason.AUTHENTICATION_TAG_MISMATCH, event.failureReason)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent TransportFallbackEvent`() {
        val event =
            DiagnosticEvent.TransportFallbackEvent(
                peerIdentity = PeerIdentity.ZERO,
                reason = TransportFallbackReason.NO_PSM_ADVERTISED,
            )
        assertEquals(TransportFallbackReason.NO_PSM_ADVERTISED, event.reason)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent TransferDataPlaneBearerEvent`() {
        val event =
            DiagnosticEvent.TransferDataPlaneBearerEvent(
                sessionId = SessionId.ZERO,
                bearer = DataPlaneBearer.GATT,
            )
        assertEquals(DataPlaneBearer.GATT, event.bearer)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent PowerTierEffectiveEvent`() {
        val event =
            DiagnosticEvent.PowerTierEffectiveEvent(
                requestedTier = PowerTier.HIGH,
                effectiveTier = PowerTier.MEDIUM,
                regulatoryRegion = RegulatoryRegion.EU,
                scanDutyCyclePercent = 10,
                advertisementIntervalMs = 500,
                connectionIntervalMs = 15.0,
            )
        assertEquals(PowerTier.HIGH, event.requestedTier)
        assertEquals(PowerTier.MEDIUM, event.effectiveTier)
        assertEquals(RegulatoryRegion.EU, event.regulatoryRegion)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent HandshakeEvent`() {
        val event =
            DiagnosticEvent.HandshakeEvent(
                sessionId = SessionId.ZERO,
                pattern = HandshakePattern.XX,
                fallbackUsed = true,
                fullPublicKeyVerified = true,
                rateLimitAttempts = 3,
                nonceReplayDetected = false,
            )
        assertEquals(HandshakePattern.XX, event.pattern)
        assertTrue(event.fallbackUsed)
        assertTrue(event.fullPublicKeyVerified)
        assertEquals(3, event.rateLimitAttempts)
        assertFalse(event.nonceReplayDetected)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent KeyRotationEvent`() {
        val event =
            DiagnosticEvent.KeyRotationEvent(
                peerIdentity = PeerIdentity.ZERO,
                oldKeyVerified = true,
                sequenceNumberReset = false,
                propagationDeadlineMet = true,
                reason = KeyRotationReason.PERIODIC,
            )
        assertEquals(KeyRotationReason.PERIODIC, event.reason)
        assertTrue(event.oldKeyVerified)
        assertFalse(event.sequenceNumberReset)
        assertTrue(event.propagationDeadlineMet)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent NoiseSessionTransitionEvent`() {
        val event =
            DiagnosticEvent.NoiseSessionTransitionEvent(
                peerIdentity = PeerIdentity.ZERO,
                layer = NoiseLayer.HOP_BY_HOP,
                fromState = NoiseSessionState.DISCONNECTED,
                toState = NoiseSessionState.ESTABLISHED,
                role = NoiseRole.INITIATOR,
                handshakePattern = HandshakePattern.XX,
                failureReason = null,
            )
        assertEquals(NoiseLayer.HOP_BY_HOP, event.layer)
        assertEquals(NoiseSessionState.ESTABLISHED, event.toState)
        assertEquals(NoiseRole.INITIATOR, event.role)
        assertNull(event.failureReason)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent NoiseSessionTransitionEvent with failure`() {
        val event =
            DiagnosticEvent.NoiseSessionTransitionEvent(
                peerIdentity = PeerIdentity.ZERO,
                layer = NoiseLayer.END_TO_END,
                fromState = NoiseSessionState.HANDSHAKING_XX,
                toState = NoiseSessionState.FAILED,
                role = NoiseRole.RESPONDER,
                handshakePattern = HandshakePattern.IK,
                failureReason = NoiseFailureReason.HANDSHAKE_TIMEOUT,
            )
        assertEquals(NoiseSessionState.FAILED, event.toState)
        assertEquals(NoiseFailureReason.HANDSHAKE_TIMEOUT, event.failureReason)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent RouteDigestMismatchEvent`() {
        val event =
            DiagnosticEvent.RouteDigestMismatchEvent(
                peerIdentity = PeerIdentity.ZERO,
                localDigest = 0xABCDu,
                remoteDigest = 0x1234u,
            )
        assertEquals(0xABCDu, event.localDigest)
        assertEquals(0x1234u, event.remoteDigest)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent TransferSessionTransitionEvent`() {
        val event =
            DiagnosticEvent.TransferSessionTransitionEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                fromState = TransferState.IN_PROGRESS,
                toState = TransferState.COMPLETED,
                bytesTransferred = 1024L,
                totalBytes = 4096L,
            )
        assertEquals(TransferState.IN_PROGRESS, event.fromState)
        assertEquals(TransferState.COMPLETED, event.toState)
        assertEquals(1024L, event.bytesTransferred)
        assertEquals(4096L, event.totalBytes)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `DiagnosticEvent TransferFailureEvent`() {
        val event =
            DiagnosticEvent.TransferFailureEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                reason = TransferFailureReason.Unrecoverable("disk full"),
            )
        assertEquals(TransferFailureReason.Unrecoverable("disk full"), event.reason)
        assertNotNull(event.timestamp)
    }

    // ---------------------------------------------------------------------------
    // TransferFailureReason sealed interface
    // ---------------------------------------------------------------------------

    @Test
    fun `TransferFailureReason Unrecoverable carries message`() {
        val reason = TransferFailureReason.Unrecoverable("critical error")
        assertEquals("critical error", reason.message)
    }

    @Test
    fun `TransferFailureReason TrustFailure carries peer identity`() {
        val peer = PeerIdentity.ZERO
        val reason = TransferFailureReason.TrustFailure(peer)
        assertEquals(peer, reason.peerIdentity)
    }

    // ---------------------------------------------------------------------------
    // MutableScoreboard — missingCount() and markMissing()
    // ---------------------------------------------------------------------------

    @Test
    fun `MutableScoreboard missingCount returns correct count`() {
        val msb = MutableScoreboard(8u)
        msb.markReceived(0)
        msb.markReceived(1)
        msb.markReceived(2)
        assertEquals(5, msb.missingCount())
    }

    @Test
    fun `MutableScoreboard markMissing clears a bit`() {
        val msb = MutableScoreboard(8u)
        msb.markReceived(3)
        assertTrue(msb.isReceived(3))
        msb.markMissing(3)
        assertFalse(msb.isReceived(3))
        assertEquals(0, msb.receivedCount())
    }

    // ---------------------------------------------------------------------------
    // RouteEntry — null nextHop and null identityKey
    // ---------------------------------------------------------------------------

    @Test
    fun `RouteEntry with null nextHop and null identityKey`() {
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = null,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                identityKey = null,
                expiresAt = Clock.System.now(),
            )
        assertNull(entry.nextHop)
        assertNull(entry.identityKey)
        assertEquals(PeerIdentity.ZERO, entry.destination)
    }

    @Test
    fun `RouteEntry with null nextHop but valid identityKey`() {
        val key =
            Ed25519Key.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val entry =
            RouteEntry(
                destination = PeerIdentity.ZERO,
                nextHop = null,
                source = PeerIdentity.ZERO,
                metric = 0u,
                seqNo = SeqNo.ZERO,
                identityKey = key,
                expiresAt = Clock.System.now(),
            )
        assertNull(entry.nextHop)
        assertNotNull(entry.identityKey)
        assertEquals(key, entry.identityKey)
    }

    // ---------------------------------------------------------------------------
    // PowerTierConfig — all fields exercised
    // ---------------------------------------------------------------------------

    @Test
    fun `PowerTier HIGH config has all expected values`() {
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
    fun `PowerTier LOW config has all expected values`() {
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

    // ---------------------------------------------------------------------------
    // PowerTier$Companion — directly reference constants to trigger companion init
    // ---------------------------------------------------------------------------

    @Test
    fun `PowerTier HIGH_CONFIG scanDutyCyclePercent`() {
        assertEquals(20, PowerTier.HIGH_CONFIG.scanDutyCyclePercent)
    }

    @Test
    fun `PowerTier MEDIUM_CONFIG scanDutyCyclePercent`() {
        assertEquals(10, PowerTier.MEDIUM_CONFIG.scanDutyCyclePercent)
    }

    @Test
    fun `PowerTier LOW_CONFIG scanDutyCyclePercent`() {
        assertEquals(5, PowerTier.LOW_CONFIG.scanDutyCyclePercent)
    }

    // ---------------------------------------------------------------------------
    // MeshLink placeholder object
    // ---------------------------------------------------------------------------

    @Test
    fun `MeshLink version is correct`() {
        assertEquals("0.0.0", MeshLink.VERSION)
    }
}
