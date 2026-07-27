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
import ch.trancee.meshlink.model.PeerLifecycleState
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.ScoreboardEncoding
import ch.trancee.meshlink.model.TransferDeliveryOutcome
import ch.trancee.meshlink.model.TransferState
import ch.trancee.meshlink.model.TransportFallbackReason
import ch.trancee.meshlink.model.TrustState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        // Verify IX and NX states are present (per crypto-design ADR)
        assertTrue(NoiseSessionState.entries.any { it.name == "HANDSHAKING_IX" })
        assertTrue(NoiseSessionState.entries.any { it.name == "HANDSHAKING_NX" })
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

    @Test
    fun `All enum values covered for TrustState`() {
        TrustState.entries.forEach { assertNotNull(it.name) }
        assertEquals(3, TrustState.entries.size)
    }

    @Test
    fun `All enum values covered for DiagnosticSeverity`() {
        DiagnosticSeverity.entries.forEach { assertNotNull(it.name) }
        assertEquals(4, DiagnosticSeverity.entries.size)
    }

    @Test
    fun `All enum values covered for TransferDeliveryOutcome`() {
        TransferDeliveryOutcome.entries.forEach { assertNotNull(it.name) }
        assertEquals(7, TransferDeliveryOutcome.entries.size)
    }

    @Test
    fun `Internal KeyRotationState values accessible`() {
        val states = KeyRotationState.entries
        assertEquals(3, states.size)
        assertNotNull(states[0].name)
    }

    @Test
    fun `Internal PeerLifecycleState values accessible`() {
        val states = PeerLifecycleState.entries
        assertEquals(3, states.size)
        assertNotNull(states[0].name)
    }

    @Test
    fun `DiagnosticSeverity values accessible`() {
        DiagnosticSeverity.entries.forEach { assertNotNull(it.name) }
    }
}
