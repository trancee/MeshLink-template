package ch.trancee.meshlink.diagnostics

import ch.trancee.meshlink.model.Bearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.ErrorCode
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.TransferId
import ch.trancee.meshlink.model.TransferResult
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        assertEquals(0x8501u, first.code.value)
        assertEquals(1u, (first as DiagnosticEvent.RouteDecryptFailureEvent).frameType)
        assertEquals(10L, transfer.offset)
    }

    @Test
    fun `diagnostic codes are unique`() {
        // Arrange
        val codes =
            listOf(
                DiagnosticCode.ROUTE_DECRYPT_FAILURE,
                DiagnosticCode.TRANSPORT_FALLBACK,
                DiagnosticCode.TRANSFER_BEARER,
                DiagnosticCode.POWER_MODE_EFFECTIVE,
                DiagnosticCode.HANDSHAKE,
                DiagnosticCode.KEY_ROTATION,
                DiagnosticCode.NOISE_SESSION,
                DiagnosticCode.ROUTE_DIGEST_MISMATCH,
                DiagnosticCode.TRANSFER_SESSION_TRANSITION,
                DiagnosticCode.TRANSFER_FAILURE,
            )

        // Act
        val uniqueCodes = codes.distinctBy { it.value }

        // Assert
        assertEquals(codes.size, uniqueCodes.size)
    }

    @Test
    fun `diagnostic codes do not collide with ErrorCode values`() {
        // Arrange — all DiagnosticCode constants
        val diagnosticCodes =
            listOf(
                DiagnosticCode.ROUTE_DECRYPT_FAILURE,
                DiagnosticCode.TRANSPORT_FALLBACK,
                DiagnosticCode.TRANSFER_BEARER,
                DiagnosticCode.POWER_MODE_EFFECTIVE,
                DiagnosticCode.HANDSHAKE,
                DiagnosticCode.KEY_ROTATION,
                DiagnosticCode.NOISE_SESSION,
                DiagnosticCode.ROUTE_DIGEST_MISMATCH,
                DiagnosticCode.TRANSFER_SESSION_TRANSITION,
                DiagnosticCode.TRANSFER_FAILURE,
            )
        // ErrorCode UShort values are reserved for exceptions — diagnostics must not overlap
        val errorCodeValues = ErrorCode.entries.map { it.code() }.toSet()

        // Act & Assert — no DiagnosticCode shares a wire value with any ErrorCode
        val collisions = diagnosticCodes.filter { it.value in errorCodeValues }
        assertTrue(
            collisions.isEmpty(),
            "DiagnosticCode values must not collide with ErrorCode values, " +
                "but found collisions: ${collisions.map { it.value }}",
        )
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
            DiagnosticEvent.TransferBearerEvent(id = TransferId(1u), bearer = Bearer.GATT),
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
                identity = identity,
                state = TransferState.TRANSFERRING,
                offset = 10L,
                total = 100L,
                result = null,
            ),
            DiagnosticEvent.TransferFailureEvent(
                id = TransferId(4u),
                identity = identity,
                result = TransferResult.UnrecoverableFailure("failure"),
            ),
        )
    }

    private fun powerEvent(): DiagnosticEvent.PowerModeEffectiveEvent =
        DiagnosticEvent.PowerModeEffectiveEvent(
            requestedMode = PowerMode.MEDIUM,
            effectiveMode = PowerMode.MEDIUM,
            regulatoryRegion = RegulatoryRegion.DEFAULT,
            scanDutyCycle = 10,
            advertisementInterval = 500.milliseconds,
            activeConnectionInterval = 15.milliseconds,
            idleConnectionInterval = 30.milliseconds,
            idleTransitionDelay = 5.seconds,
            concurrentConnectionLimit = 4,
            chunkSize = 256,
            retryLimit = 5,
            retryBudget = 30.seconds,
            disconnectGracePeriod = 30.seconds,
        )

    private fun rotationEvent(identity: PeerIdentity): DiagnosticEvent.KeyRotationEvent =
        DiagnosticEvent.KeyRotationEvent(
            identity = identity,
            oldGeneration = 1u,
            newGeneration = 2u,
            reason = KeyRotationReason.PERIODIC,
            continuityVerified = true,
            conflictDetected = false,
            propagationDeadlineMet = true,
        )

    private fun noiseEvent(identity: PeerIdentity): DiagnosticEvent.NoiseSessionEvent =
        DiagnosticEvent.NoiseSessionEvent(
            id = NoiseSessionId(3u),
            identity = identity,
            layer = NoiseLayer.HOP_BY_HOP,
            role = NoiseRole.INITIATOR,
            pattern = HandshakePattern.IK,
            fromState = NoiseSessionState.HANDSHAKING_IK,
            toState = NoiseSessionState.ESTABLISHED,
            failureReason = null,
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
                    identity = identity,
                    oldGeneration = 2u,
                    newGeneration = 4u,
                    reason = KeyRotationReason.SECURITY_EVENT,
                    continuityVerified = false,
                    conflictDetected = true,
                    propagationDeadlineMet = false,
                ),
                DiagnosticEvent.KeyRotationEvent(
                    identity = identity,
                    oldGeneration = 2u,
                    newGeneration = 3u,
                    reason = KeyRotationReason.MANUAL,
                    continuityVerified = true,
                    conflictDetected = true,
                    propagationDeadlineMet = true,
                ),
                DiagnosticEvent.NoiseSessionEvent(
                    id = NoiseSessionId(2u),
                    identity = identity,
                    layer = NoiseLayer.END_TO_END,
                    role = NoiseRole.RESPONDER,
                    pattern = HandshakePattern.XX,
                    fromState = NoiseSessionState.HANDSHAKING_XX,
                    toState = NoiseSessionState.FAILED,
                    failureReason = NoiseFailureReason.HANDSHAKE_MESSAGE_MALFORMED,
                ),
                DiagnosticEvent.TransferSessionTransitionEvent(
                    id = TransferId(5u),
                    identity = identity,
                    state = TransferState.TRANSFERRING,
                    offset = 0L,
                    total = 10L,
                    result = TransferResult.UnrecoverableFailure("failure"),
                ),
                DiagnosticEvent.TransferSessionTransitionEvent(
                    id = TransferId(6u),
                    identity = identity,
                    state = TransferState.ROUTE_UNAVAILABLE,
                    offset = 0L,
                    total = 10L,
                    result = TransferResult.UnrecoverableFailure("expired"),
                ),
            )

        // Assert
        assertEquals(6, events.size)
        events.forEach { event -> assertEquals(DiagnosticSeverity.ERROR, event.severity) }
    }

    @Test
    fun `TransferSessionTransitionEvent severity maps each result to correct level`() {
        // Arrange
        val identity = PeerIdentity.ZERO

        // Act & Assert — TrustFailure maps to ERROR
        val trustFailure =
            DiagnosticEvent.TransferSessionTransitionEvent(
                id = TransferId(7u),
                identity = identity,
                state = TransferState.TRANSFERRING,
                offset = 0L,
                total = 10L,
                result = TransferResult.TrustFailure(identity),
            )
        assertEquals(DiagnosticSeverity.ERROR, trustFailure.severity)

        // Completed maps to INFO
        val completed =
            DiagnosticEvent.TransferSessionTransitionEvent(
                id = TransferId(8u),
                identity = identity,
                state = TransferState.TRANSFERRING,
                offset = 10L,
                total = 10L,
                result = TransferResult.Completed,
            )
        assertEquals(DiagnosticSeverity.INFO, completed.severity)

        // Cancelled maps to INFO
        val cancelled =
            DiagnosticEvent.TransferSessionTransitionEvent(
                id = TransferId(9u),
                identity = identity,
                state = TransferState.AWAITING_DECISION,
                offset = 0L,
                total = 10L,
                result = TransferResult.Cancelled,
            )
        assertEquals(DiagnosticSeverity.INFO, cancelled.severity)

        // Expired maps to INFO
        val expired =
            DiagnosticEvent.TransferSessionTransitionEvent(
                id = TransferId(10u),
                identity = identity,
                state = TransferState.TRANSFERRING,
                offset = 0L,
                total = 10L,
                result = TransferResult.Expired,
            )
        assertEquals(DiagnosticSeverity.INFO, expired.severity)
    }
}
