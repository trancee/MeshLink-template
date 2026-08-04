package ch.trancee.meshlink

import ch.trancee.meshlink.diagnostics.DiagnosticCodes
import ch.trancee.meshlink.diagnostics.DiagnosticEvent
import ch.trancee.meshlink.diagnostics.HandshakeId
import ch.trancee.meshlink.diagnostics.NoiseSessionId
import ch.trancee.meshlink.model.Bearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.TransferFailureReason
import ch.trancee.meshlink.model.TransferId
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DiagnosticEventTest {
    @Test
    fun `events expose accepted common metadata and logical field order`() {
        // Arrange
        val events = diagnosticEvents()

        // Act
        val first = events.first()
        val transfer = events[8] as DiagnosticEvent.TransferSessionTransitionEvent

        // Assert
        assertEquals(10, events.size)
        assertEquals(0x0501u, first.code.value)
        assertEquals(1u, (first as DiagnosticEvent.RouteDecryptFailureEvent).frameType)
        assertEquals(10L, transfer.offset)
    }

    @Test
    fun `diagnostic codes are unique`() {
        // Arrange
        val codes =
            listOf(
                DiagnosticCodes.ROUTE_DECRYPTION_FAILED,
                DiagnosticCodes.TRANSPORT_FALLBACK,
                DiagnosticCodes.TRANSFER_BEARER,
                DiagnosticCodes.POWER_MODE_SETTINGS,
                DiagnosticCodes.HANDSHAKE,
                DiagnosticCodes.KEY_ROTATION,
                DiagnosticCodes.NOISE_SESSION,
                DiagnosticCodes.ROUTE_DIGEST_MISMATCH,
                DiagnosticCodes.TRANSFER_STATE,
                DiagnosticCodes.TRANSFER_FAILURE,
            )

        // Act
        val uniqueCodes = codes.distinctBy { it.value }

        // Assert
        assertEquals(codes.size, uniqueCodes.size)
    }

    private fun diagnosticEvents(): List<DiagnosticEvent> {
        val identity = PeerIdentity.ZERO
        return listOf(
            DiagnosticEvent.RouteDecryptFailureEvent(
                identity,
                1u,
                DecryptFailureReason.MALFORMED_FRAME,
            ),
            DiagnosticEvent.TransportFallbackEvent(
                identity,
                TransportFallbackReason.L2CAP_STREAM_ERROR,
            ),
            DiagnosticEvent.TransportLayerEvent(id = TransferId(1u), bearer = Bearer.GATT),
            powerEvent(),
            DiagnosticEvent.HandshakeEvent(
                HandshakeId(2u),
                HandshakePattern.XX,
                VerificationLevel.TOFU_PIN,
                false,
            ),
            rotationEvent(identity),
            noiseEvent(identity),
            DiagnosticEvent.RouteDigestMismatchEvent(identity, 1u, 2u),
            DiagnosticEvent.TransferSessionTransitionEvent(
                id = TransferId(4u),
                identity,
                TransferState.TRANSFERRING,
                10L,
                100L,
                null,
            ),
            DiagnosticEvent.TransferFailureEvent(
                id = TransferId(4u),
                identity,
                TransferFailureReason.Unrecoverable("failure"),
            ),
        )
    }

    private fun powerEvent(): DiagnosticEvent.PowerModeEffectiveEvent =
        DiagnosticEvent.PowerModeEffectiveEvent(
            PowerMode.MEDIUM,
            PowerMode.MEDIUM,
            RegulatoryRegion.DEFAULT,
            10,
            500.milliseconds,
            15.milliseconds,
            30.milliseconds,
            5.seconds,
            4,
            256,
            5,
            30.seconds,
            30.seconds,
        )

    private fun rotationEvent(identity: PeerIdentity): DiagnosticEvent.KeyRotationEvent =
        DiagnosticEvent.KeyRotationEvent(
            identity,
            1u,
            2u,
            KeyRotationReason.PERIODIC,
            true,
            false,
            true,
        )

    private fun noiseEvent(identity: PeerIdentity): DiagnosticEvent.NoiseSessionEvent =
        DiagnosticEvent.NoiseSessionEvent(
            NoiseSessionId(3u),
            identity,
            NoiseLayer.HOP_BY_HOP,
            NoiseRole.INITIATOR,
            HandshakePattern.IK,
            NoiseSessionState.HANDSHAKING_IK,
            NoiseSessionState.ESTABLISHED,
            null,
        )

    @Test
    fun `failure events expose error severity`() {
        // Arrange
        val identity = PeerIdentity.ZERO

        // Act
        val events =
            listOf(
                DiagnosticEvent.HandshakeEvent(
                    id = HandshakeId(1u),
                    pattern = HandshakePattern.IK,
                    verificationLevel = VerificationLevel.NONE,
                    nonceReplayDetected = true,
                ),
                DiagnosticEvent.KeyRotationEvent(
                    peerIdentity = identity,
                    oldGeneration = 2u,
                    newGeneration = 4u,
                    reason = KeyRotationReason.SECURITY_EVENT,
                    continuityVerified = false,
                    conflictDetected = true,
                    propagationDeadlineMet = false,
                ),
                DiagnosticEvent.KeyRotationEvent(
                    peerIdentity = identity,
                    oldGeneration = 2u,
                    newGeneration = 3u,
                    reason = KeyRotationReason.MANUAL,
                    continuityVerified = true,
                    conflictDetected = true,
                    propagationDeadlineMet = true,
                ),
                DiagnosticEvent.NoiseSessionEvent(
                    id = NoiseSessionId(2u),
                    peerIdentity = identity,
                    layer = NoiseLayer.END_TO_END,
                    role = NoiseRole.RESPONDER,
                    pattern = HandshakePattern.XX,
                    fromState = NoiseSessionState.HANDSHAKING_XX,
                    toState = NoiseSessionState.FAILED,
                    failureReason = NoiseFailureReason.HANDSHAKE_MESSAGE_MALFORMED,
                ),
                DiagnosticEvent.TransferSessionTransitionEvent(
                    id = TransferId(5u),
                    peerIdentity = identity,
                    state = TransferState.FAILED,
                    offset = 0L,
                    total = 10L,
                    reason = null,
                ),
                DiagnosticEvent.TransferSessionTransitionEvent(
                    id = TransferId(6u),
                    peerIdentity = identity,
                    state = TransferState.EXPIRED,
                    offset = 0L,
                    total = 10L,
                    reason = null,
                ),
            )

        // Assert
        assertEquals(6, events.size)
        events.forEach { event -> assertEquals(DiagnosticSeverity.ERROR, event.severity) }
    }
}
