package ch.trancee.meshlink

import ch.trancee.meshlink.diagnostics.DiagnosticEvent
import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.SessionId
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.VerificationLevel
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
        assertEquals("route", event.category)
        assertEquals(DiagnosticSeverity.WARN, event.severity)
        assertTrue(event.payload.contains("peerIdentity"))
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
        assertEquals("transport", event.category)
        assertEquals(DiagnosticSeverity.WARN, event.severity)
        assertTrue(event.payload.contains("peerIdentity"))
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
        assertEquals("transfer", event.category)
        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertTrue(event.payload.contains("bearer"))
    }

    @Test
    fun `DiagnosticEvent PowerModeEffectiveEvent`() {
        val event =
            DiagnosticEvent.PowerModeEffectiveEvent(
                requestedMode = PowerMode.HIGH,
                effectiveMode = PowerMode.MEDIUM,
                regulatoryRegion = RegulatoryRegion.EU,
                scanDutyCyclePercent = 10,
                advertisementIntervalMs = 500,
                connectionIntervalMs = 15.0,
            )
        assertEquals(PowerMode.HIGH, event.requestedMode)
        assertEquals(PowerMode.MEDIUM, event.effectiveMode)
        assertEquals(RegulatoryRegion.EU, event.regulatoryRegion)
        assertNotNull(event.timestamp)
        assertEquals("power", event.category)
        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertTrue(event.payload.contains("requestedMode"))
    }

    @Test
    fun `DiagnosticEvent HandshakeEvent`() {
        val event =
            DiagnosticEvent.HandshakeEvent(
                sessionId = SessionId.ZERO,
                pattern = HandshakePattern.XX,
                fallbackUsed = true,
                verificationLevel = VerificationLevel.FULL,
                rateLimitAttempts = 3,
                nonceReplayDetected = false,
            )
        assertEquals(HandshakePattern.XX, event.pattern)
        assertTrue(event.fallbackUsed)
        assertEquals(VerificationLevel.FULL, event.verificationLevel)
        assertEquals(3, event.rateLimitAttempts)
        assertFalse(event.nonceReplayDetected)
        assertNotNull(event.timestamp)
        assertEquals("handshake", event.category)
        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertTrue(event.payload.contains("pattern"))
    }

    @Test
    fun `DiagnosticEvent HandshakeEvent with NONE verification level`() {
        val event =
            DiagnosticEvent.HandshakeEvent(
                sessionId = SessionId.ZERO,
                pattern = HandshakePattern.XX,
                fallbackUsed = false,
                verificationLevel = VerificationLevel.NONE,
                rateLimitAttempts = 0,
                nonceReplayDetected = false,
            )
        assertEquals(DiagnosticSeverity.ERROR, event.severity)
    }

    @Test
    fun `DiagnosticEvent KeyRotationEvent`() {
        val event =
            DiagnosticEvent.KeyRotationEvent(
                peerIdentity = PeerIdentity.ZERO,
                reason = KeyRotationReason.PERIODIC,
                oldKeyVerified = true,
            )
        assertEquals(KeyRotationReason.PERIODIC, event.reason)
        assertTrue(event.oldKeyVerified)
        assertNotNull(event.timestamp)
        assertEquals("key_rotation", event.category)
        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertTrue(event.payload.contains("peerIdentity"))
    }

    @Test
    fun `DiagnosticEvent KeyRotationEvent with failed verification`() {
        val event =
            DiagnosticEvent.KeyRotationEvent(
                peerIdentity = PeerIdentity.ZERO,
                reason = KeyRotationReason.SECURITY_EVENT,
                oldKeyVerified = false,
            )
        assertEquals(DiagnosticSeverity.INFO, event.severity)
    }

    @Test
    fun `DiagnosticEvent NoiseSessionEvent`() {
        val event =
            DiagnosticEvent.NoiseSessionEvent(
                sessionId = SessionId.ZERO,
                layer = NoiseLayer.HOP_BY_HOP,
                fromState = NoiseSessionState.DISCONNECTED,
                toState = NoiseSessionState.ESTABLISHED,
                reason = null,
            )
        assertEquals(NoiseLayer.HOP_BY_HOP, event.layer)
        assertEquals(NoiseSessionState.ESTABLISHED, event.toState)
        assertNull(event.reason)
        assertNotNull(event.timestamp)
        assertEquals("noise", event.category)
        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertTrue(event.payload.contains("layer"))
    }

    @Test
    fun `DiagnosticEvent NoiseSessionEvent with failure`() {
        val event =
            DiagnosticEvent.NoiseSessionEvent(
                sessionId = SessionId.ZERO,
                layer = NoiseLayer.END_TO_END,
                fromState = NoiseSessionState.HANDSHAKING_XX,
                toState = NoiseSessionState.FAILED,
                reason = NoiseFailureReason.HANDSHAKE_TIMEOUT,
            )
        assertEquals(NoiseSessionState.FAILED, event.toState)
        assertEquals(NoiseFailureReason.HANDSHAKE_TIMEOUT, event.reason)
        assertNotNull(event.timestamp)
        assertEquals("noise", event.category)
        assertEquals(DiagnosticSeverity.ERROR, event.severity)
    }

    @Test
    fun `DiagnosticEvent TransferSessionEvent`() {
        val event =
            DiagnosticEvent.TransferSessionEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                state = TransferState.COMPLETED,
                reason = null,
            )
        assertEquals(TransferState.COMPLETED, event.state)
        assertNull(event.reason)
        assertNotNull(event.timestamp)
        assertEquals("transfer", event.category)
        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertTrue(event.payload.contains("state"))
    }

    @Test
    fun `DiagnosticEvent TransferSessionEvent with FAILED state`() {
        val event =
            DiagnosticEvent.TransferSessionEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                state = TransferState.FAILED,
                reason = null,
            )
        assertEquals(DiagnosticSeverity.ERROR, event.severity)
    }

    @Test
    fun `DiagnosticEvent TransferSessionEvent with TIMED_OUT state`() {
        val event =
            DiagnosticEvent.TransferSessionEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                state = TransferState.TIMED_OUT,
                reason = null,
            )
        assertEquals(DiagnosticSeverity.ERROR, event.severity)
    }

    @Test
    fun `DiagnosticEvent TransferSessionEvent with IN_PROGRESS state`() {
        val event =
            DiagnosticEvent.TransferSessionEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                state = TransferState.IN_PROGRESS,
                reason = null,
            )
        assertEquals(DiagnosticSeverity.INFO, event.severity)
    }

    @Test
    fun `DiagnosticEvent TransferSessionEvent with failure reason`() {
        val event =
            DiagnosticEvent.TransferSessionEvent(
                sessionId = SessionId.ZERO,
                peerIdentity = PeerIdentity.ZERO,
                state = TransferState.FAILED,
                reason = TransferFailureReason.Unrecoverable("disk full"),
            )
        assertEquals(TransferFailureReason.Unrecoverable("disk full"), event.reason)
        assertNotNull(event.timestamp)
        assertEquals("transfer", event.category)
        assertEquals(DiagnosticSeverity.ERROR, event.severity)
        assertTrue(event.payload.contains("reason"))
    }
}
