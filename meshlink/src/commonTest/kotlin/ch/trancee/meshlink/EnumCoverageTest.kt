package ch.trancee.meshlink

import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.DiagnosticSeverity
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.KeyRotationState
import ch.trancee.meshlink.model.KeyType
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.PeerLifecycle
import ch.trancee.meshlink.model.PeerState
import ch.trancee.meshlink.model.PeerTrust
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.TransferDeliveryOutcome
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EnumCoverageTest {
    @Test
    fun `all accepted enum values are covered`() {
        // Arrange
        val enums =
            listOf(
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
                DataPlaneBearer.entries,
                PeerState.entries,
                PeerTrust.entries,
                DiagnosticSeverity.entries,
                TransferDeliveryOutcome.entries,
                KeyRotationState.entries,
                PeerLifecycle.entries,
            )

        // Act
        val names = enums.flatten().map { it.name }

        // Assert
        names.forEach { assertNotNull(it) }
        assertEquals(5, PeerTrust.entries.size)
        assertEquals(6, TransferDeliveryOutcome.entries.size)
    }
}
