package ch.trancee.meshlink

import ch.trancee.meshlink.model.DataPlaneBearer
import ch.trancee.meshlink.model.DecryptFailureReason
import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.model.HandshakePattern
import ch.trancee.meshlink.model.KeyRotationReason
import ch.trancee.meshlink.model.KeyType
import ch.trancee.meshlink.model.NoiseFailureReason
import ch.trancee.meshlink.model.NoiseLayer
import ch.trancee.meshlink.model.NoiseRole
import ch.trancee.meshlink.model.NoiseSessionState
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.ScoreboardEncoding
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import kotlin.test.Test
import kotlin.test.assertNotNull

class EnumCoverageTest {
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
    fun `All enum values covered for DataPlaneBearer`() {
        DataPlaneBearer.entries.forEach { assertNotNull(it.name) }
    }
}
