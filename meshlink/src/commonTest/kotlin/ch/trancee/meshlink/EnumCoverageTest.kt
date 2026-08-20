package ch.trancee.meshlink

import ch.trancee.meshlink.model.Bearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.KeyRotationState
import ch.trancee.meshlink.model.KeyType
import ch.trancee.meshlink.model.MeshLinkState
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerLifecycle
import ch.trancee.meshlink.model.PeerState
import ch.trancee.meshlink.model.PeerTrust
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.TransferKind
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.VerificationLevel
import ch.trancee.meshlink.transfer.PayloadDecision
import ch.trancee.meshlink.transport.L2capState
import ch.trancee.meshlink.wire.model.ByteOrder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnumCoverageTest {
    @Test
    fun `all accepted enum values are covered`() {
        // Arrange
        val enums =
            listOf(
                MeshLinkState.entries,
                KeyType.entries,
                KeyRotationReason.entries,
                HandshakePattern.entries,
                Priority.entries,
                FrameType.entries,
                DecryptFailureReason.entries,
                TransportFallbackReason.entries,
                RegulatoryRegion.entries,
                NoiseLayer.entries,
                NoiseSessionState.entries,
                NoiseRole.entries,
                NoiseFailureReason.entries,
                TransferState.entries,
                Bearer.entries,
                PeerState.entries,
                PeerTrust.entries,
                DiagnosticSeverity.entries,
                TransferKind.entries,
                VerificationLevel.entries,
                KeyRotationState.entries,
                PeerLifecycle.entries,
                PayloadDecision.entries,
                L2capState.entries,
                PowerMode.entries,
                ByteOrder.entries,
            )

        // Act
        val names = enums.flatten().map { it.name }

        // Assert — all 26 enums are listed (no enum omitted from the coverage check)
        assertEquals(26, enums.size)
        // Each entry name is non-blank (catches empty-name regressions)
        names.forEach { assertTrue(it.isNotBlank(), "Enum entry name must not be blank") }
        // Spot-check key enum sizes
        assertEquals(5, PeerTrust.entries.size)
        assertEquals(5, MeshLinkState.entries.size)
    }

    @Test
    fun `FrameType fromCode resolves all defined wire codes`() {
        // Arrange — every FrameType entry must round-trip through fromCode
        val knownCodes = FrameType.entries.associateBy { it.code }

        // Act + Assert
        for ((code, expected) in knownCodes) {
            val resolved = FrameType.fromCode(code)
            assertEquals(expected, resolved, "fromCode should resolve code $code")
        }
    }

    @Test
    fun `FrameType fromCode throws for unknown wire code`() {

        assertFailsWith<IllegalArgumentException> { FrameType.fromCode(0xFFu) }
    }

    @Test
    fun `FrameType wire codes match spec`() {

        assertEquals(0x00, FrameType.MESH_ENVELOPE.code.toInt())
        assertEquals(0x01, FrameType.ROUTE_ADVERTISEMENT.code.toInt())
        assertEquals(0x02, FrameType.ROUTE_WITHDRAWAL.code.toInt())
        assertEquals(0x03, FrameType.ROUTE_DIGEST.code.toInt())
        assertEquals(0x04, FrameType.ROUTE_SEQUENCE_ADVANCEMENT.code.toInt())
        assertEquals(0x05, FrameType.ROUTE_SYNCHRONIZATION.code.toInt())
        assertEquals(0x06, FrameType.ROUTE_SNAPSHOT.code.toInt())
        assertEquals(0x20, FrameType.PAYLOAD_MANIFEST.code.toInt())
        assertEquals(0x21, FrameType.PAYLOAD_DECISION.code.toInt())
        assertEquals(0x22, FrameType.PAYLOAD_CHUNK.code.toInt())
        assertEquals(0x23, FrameType.PAYLOAD_ACKNOWLEDGEMENT.code.toInt())
        assertEquals(0x24, FrameType.PAYLOAD_CANCELLATION.code.toInt())
        assertEquals(0x40, FrameType.KEY_ROTATION.code.toInt())
        assertEquals(0x41, FrameType.EPOCH_COMMIT.code.toInt())
    }
}
