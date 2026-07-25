package ch.trancee.meshlink

import ch.trancee.meshlink.diagnostics.DiagnosticEvent
import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerTier
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.SessionId
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticEventTest {
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
}
