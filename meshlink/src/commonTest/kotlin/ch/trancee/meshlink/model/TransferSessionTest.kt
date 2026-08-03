package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TransferSessionTest {

    @Test
    fun `transfer session exposes accepted progress fields`() {
        // Arrange
        val session =
            TransferSession(
                id = TransferId(1u),
                destination = PeerIdentity.ZERO,
                priority = Priority.NORMAL,
                state = TransferState.TRANSFERRING,
                chunkSize = 256,
                totalChunks = 1u,
                scoreboard = Scoreboard(1u),
                total = 256L,
                offset = 0L,
                startedAt = Instant.fromEpochMilliseconds(0),
                expiresAt = null,
                retryCount = 0,
                failureReason = null,
            )

        // Act
        val actual =
            listOf(
                session.id,
                session.destination,
                session.priority,
                session.state,
                session.chunkSize,
                session.totalChunks,
                session.scoreboard,
                session.total,
                session.offset,
                session.startedAt,
                session.expiresAt,
                session.retryCount,
                session.failureReason,
            )

        // Assert
        assertEquals(13, actual.size)
    }
}
