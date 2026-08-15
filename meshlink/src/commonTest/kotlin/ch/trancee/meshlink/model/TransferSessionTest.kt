package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class TransferSessionTest {

    @Test
    fun `transfer session exposes all progress fields with correct values`() {
        // Arrange
        val transferId = TransferId(1u)
        val destination = PeerIdentity.ZERO
        val startedAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(7000)
        val scoreboard = Scoreboard(1u)

        // Act
        val session =
            TransferSession(
                id = transferId,
                destination = destination,
                priority = Priority.NORMAL,
                state = TransferState.TRANSFERRING,
                result = null,
                chunkSize = 256,
                totalChunks = 1u,
                scoreboard = scoreboard,
                total = 256L,
                offset = 0L,
                startedAt = startedAt,
                expiresAt = expiresAt,
                retryCount = 0,
            )

        // Assert — verify each field carries the expected value
        assertEquals(transferId, session.id)
        assertEquals(destination, session.destination)
        assertEquals(Priority.NORMAL, session.priority)
        assertEquals(TransferState.TRANSFERRING, session.state)
        assertNull(session.result)
        assertEquals(256, session.chunkSize)
        assertEquals(1u, session.totalChunks)
        assertEquals(scoreboard, session.scoreboard)
        assertEquals(256L, session.total)
        assertEquals(0L, session.offset)
        assertEquals(startedAt, session.startedAt)
        assertEquals(expiresAt, session.expiresAt)
        assertEquals(0, session.retryCount)
    }

    @Test
    fun `transfer session with terminal result and retry count`() {
        // Arrange
        val transferId = TransferId(42u)
        val destination = PeerIdentity.generate()
        val startedAt = Instant.fromEpochMilliseconds(500)
        val scoreboard = Scoreboard(10u)

        // Act
        val session =
            TransferSession(
                id = transferId,
                destination = destination,
                priority = Priority.HIGH,
                state = TransferState.RETRANSMITTING,
                result = TransferResult.UnrecoverableFailure("timeout"),
                chunkSize = 512,
                totalChunks = 10u,
                scoreboard = scoreboard,
                total = 5120L,
                offset = 2048L,
                startedAt = startedAt,
                expiresAt = null,
                retryCount = 3,
            )

        // Assert — verify all fields including terminal result
        assertEquals(transferId, session.id)
        assertEquals(destination, session.destination)
        assertEquals(Priority.HIGH, session.priority)
        assertEquals(TransferState.RETRANSMITTING, session.state)
        assertNotNull(session.result)
        assertEquals("timeout", (session.result as TransferResult.UnrecoverableFailure).message)
        assertEquals(512, session.chunkSize)
        assertEquals(10u, session.totalChunks)
        assertEquals(scoreboard, session.scoreboard)
        assertEquals(5120L, session.total)
        assertEquals(2048L, session.offset)
        assertEquals(startedAt, session.startedAt)
        assertNull(session.expiresAt)
        assertEquals(3, session.retryCount)

        // Verify data-class equality and hashCode consistency
        val same =
            TransferSession(
                id = transferId,
                destination = destination,
                priority = Priority.HIGH,
                state = TransferState.RETRANSMITTING,
                result = TransferResult.UnrecoverableFailure("timeout"),
                chunkSize = 512,
                totalChunks = 10u,
                scoreboard = scoreboard,
                total = 5120L,
                offset = 2048L,
                startedAt = startedAt,
                expiresAt = null,
                retryCount = 3,
            )
        assertEquals(session, same)
        assertEquals(session.hashCode(), same.hashCode())
    }

    @Test
    fun `transfer session with all non-null fields`() {
        // Arrange — construct with all 13 fields populated
        val transferId = TransferId(7u)
        val destination = PeerIdentity.generate()
        val startedAt = Instant.fromEpochMilliseconds(10)
        val expiresAt = Instant.fromEpochMilliseconds(9999)
        val scoreboard = Scoreboard(4u)

        // Act
        val session =
            TransferSession(
                id = transferId,
                destination = destination,
                priority = Priority.LOW,
                state = TransferState.ROUTE_UNAVAILABLE,
                result = TransferResult.Completed,
                chunkSize = 128,
                totalChunks = 4u,
                scoreboard = scoreboard,
                total = 512L,
                offset = 256L,
                startedAt = startedAt,
                expiresAt = expiresAt,
                retryCount = 1,
            )

        // Assert — verify all non-null values are correct
        assertEquals(transferId, session.id)
        assertEquals(Priority.LOW, session.priority)
        assertEquals(TransferState.ROUTE_UNAVAILABLE, session.state)
        assertEquals(TransferResult.Completed, session.result)
        assertEquals(128, session.chunkSize)
        assertEquals(4u, session.totalChunks)
        assertEquals(512L, session.total)
        assertEquals(256L, session.offset)
        assertEquals(startedAt, session.startedAt)
        assertEquals(expiresAt, session.expiresAt)
        assertEquals(1, session.retryCount)

        // Verify data-class equality and hashCode consistency
        val same =
            TransferSession(
                id = transferId,
                destination = destination,
                priority = Priority.LOW,
                state = TransferState.ROUTE_UNAVAILABLE,
                result = TransferResult.Completed,
                chunkSize = 128,
                totalChunks = 4u,
                scoreboard = scoreboard,
                total = 512L,
                offset = 256L,
                startedAt = startedAt,
                expiresAt = expiresAt,
                retryCount = 1,
            )
        assertEquals(session, same)
        assertEquals(session.hashCode(), same.hashCode())
    }

    @Test
    fun `transfer session with different fields produces different instances`() {
        // Arrange — two sessions that differ only in retryCount
        val base =
            TransferSession(
                id = TransferId(1u),
                destination = PeerIdentity.ZERO,
                priority = Priority.NORMAL,
                state = TransferState.TRANSFERRING,
                result = null,
                chunkSize = 256,
                totalChunks = 1u,
                scoreboard = Scoreboard(1u),
                total = 256L,
                offset = 0L,
                startedAt = Instant.fromEpochMilliseconds(0),
                expiresAt = null,
                retryCount = 0,
            )
        val withRetry = base.copy(retryCount = 1)
        val withHighPriority = base.copy(priority = Priority.HIGH)
        val withCompleted = base.copy(result = TransferResult.Completed)

        // Assert — each copy variation produces a distinct, unequal instance
        assertNotEquals(base, withRetry)
        assertNotEquals(base, withHighPriority)
        assertNotEquals(base, withCompleted)
        assertNotEquals(withRetry, withHighPriority)
        assertNotEquals(withRetry, withCompleted)
        assertNotEquals(withHighPriority, withCompleted)
        assertEquals(0, base.retryCount)
        assertEquals(1, withRetry.retryCount)
        assertEquals(Priority.HIGH, withHighPriority.priority)
        assertEquals(TransferResult.Completed, withCompleted.result)
    }
}
