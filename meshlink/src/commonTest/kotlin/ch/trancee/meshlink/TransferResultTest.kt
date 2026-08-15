package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.TransferResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TransferResultTest {

    @Test
    fun `terminal data objects are distinct`() {
        // Arrange — three terminal outcomes that must not be confused

        // Act
        val completed: TransferResult = TransferResult.Completed
        val cancelled: TransferResult = TransferResult.Cancelled
        val expired: TransferResult = TransferResult.Expired

        // Assert — each is a distinct canonical singleton (equality + hashCode + toString)
        assertNotEquals(completed, cancelled)
        assertNotEquals(completed.hashCode(), cancelled.hashCode())
        assertNotEquals(completed.toString(), cancelled.toString())
        assertNotEquals(completed, expired)
        assertNotEquals(completed.hashCode(), expired.hashCode())
        assertNotEquals(completed.toString(), expired.toString())
        assertNotEquals(cancelled, expired)
        assertNotEquals(cancelled.hashCode(), expired.hashCode())
        assertNotEquals(cancelled.toString(), expired.toString())
    }

    @Test
    fun `TransferResult subtypes are exhaustive via when`() {
        // Arrange — a when expression on the sealed interface; the compiler errors
        // if a new subtype is added without updating this test.
        val results: List<TransferResult> =
            listOf(
                TransferResult.Completed,
                TransferResult.Cancelled,
                TransferResult.Expired,
                TransferResult.UnrecoverableFailure("err"),
                TransferResult.TrustFailure(PeerIdentity.ZERO),
            )

        // Act
        val classified = results.map { result ->
            when (result) {
                is TransferResult.Completed -> "completed"
                is TransferResult.Cancelled -> "cancelled"
                is TransferResult.Expired -> "expired"
                is TransferResult.UnrecoverableFailure -> "failure"
                is TransferResult.TrustFailure -> "trust"
            }
        }

        // Assert — all five subtypes are represented and correctly classified
        assertEquals(listOf("completed", "cancelled", "expired", "failure", "trust"), classified)
    }

    @Test
    fun `all terminal data objects are singleton canonical`() {
        // Arrange — verify canonical singularity for all three terminal data objects
        val completedA = TransferResult.Completed
        val completedB = TransferResult.Completed
        val cancelledA = TransferResult.Cancelled
        val cancelledB = TransferResult.Cancelled
        val expiredA = TransferResult.Expired
        val expiredB = TransferResult.Expired

        // Assert — same data object is always equal with consistent hashCode
        assertEquals(completedA, completedB)
        assertEquals(completedA.hashCode(), completedB.hashCode())
        assertEquals(cancelledA, cancelledB)
        assertEquals(cancelledA.hashCode(), cancelledB.hashCode())
        assertEquals(expiredA, expiredB)
        assertEquals(expiredA.hashCode(), expiredB.hashCode())
    }

    @Test
    fun `UnrecoverableFailure carries message`() {
        // Arrange
        val result = TransferResult.UnrecoverableFailure("error")

        // Act
        val message = result.message
        val stringRepr = result.toString()
        val equalResult = TransferResult.UnrecoverableFailure("error")

        // Assert — message carried, toString includes it, data-class equality + hashCode
        assertEquals("error", message)
        assertTrue(stringRepr.contains("error"))
        assertEquals(result, equalResult)
        assertEquals(result.hashCode(), equalResult.hashCode())
    }

    @Test
    fun `UnrecoverableFailure with different messages are distinct`() {
        // Arrange
        val first = TransferResult.UnrecoverableFailure("timeout")
        val second = TransferResult.UnrecoverableFailure("corrupted")
        val same = TransferResult.UnrecoverableFailure("timeout")

        // Assert — different messages produce unequal results, hashCodes, and toString
        // representations
        assertNotEquals(first, second)
        assertNotEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first.toString(), second.toString())
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
    }

    @Test
    fun `UnrecoverableFailure with empty message`() {
        // Arrange
        val result = TransferResult.UnrecoverableFailure("")
        val same = TransferResult.UnrecoverableFailure("")

        // Act + Assert — empty string is still a valid message value
        assertEquals("", result.message)
        assertTrue(result.toString().isNotEmpty())
        assertEquals(result, same)
    }

    @Test
    fun `TrustFailure carries peer identity`() {
        // Arrange
        val peer = PeerIdentity.ZERO
        val result = TransferResult.TrustFailure(peer)

        // Act
        val identity = result.identity
        val stringRepr = result.toString()
        val equalResult = TransferResult.TrustFailure(peer)

        // Assert — identity carried, toString is non-empty, data-class equality + hashCode
        assertEquals(peer, identity)
        assertTrue(stringRepr.isNotEmpty())
        assertEquals(result, equalResult)
        assertEquals(result.hashCode(), equalResult.hashCode())
    }

    @Test
    fun `TrustFailure with different identities are distinct`() {
        // Arrange
        val peerA = PeerIdentity.ZERO
        val peerB = PeerIdentity.generate()

        val resultA = TransferResult.TrustFailure(peerA)
        val resultB = TransferResult.TrustFailure(peerB)
        val resultSameA = TransferResult.TrustFailure(PeerIdentity.ZERO)

        // Assert — different peers produce unequal results, hashCodes, and toString representations
        assertNotEquals(resultA, resultB)
        assertNotEquals(resultA.hashCode(), resultB.hashCode())
        assertNotEquals(resultA.toString(), resultB.toString())
        assertEquals(resultA, resultSameA)
        assertEquals(resultA.hashCode(), resultSameA.hashCode())
    }
}
