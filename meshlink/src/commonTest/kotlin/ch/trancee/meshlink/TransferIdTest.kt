package ch.trancee.meshlink

import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.Scoreboard
import ch.trancee.meshlink.model.TransferId
import ch.trancee.meshlink.model.TransferSession
import ch.trancee.meshlink.model.TransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class TransferIdTest {
    @Test
    fun `zero id has fixed representation`() {
        // Arrange
        val id = TransferId.ZERO

        // Act
        val actual = (id as Any).toString()

        // Assert
        assertEquals("00000000", actual)
    }

    @Test
    fun `nonzero id exposes padded hexadecimal representation`() {
        // Arrange
        val id = TransferId.fromHex("2a")

        // Act
        val actual = (id as Any).toString()

        // Assert
        assertEquals("0000002a", actual)
    }

    @Test
    fun `hex id round trips`() {
        // Arrange
        val expected = TransferId.fromHex("0000002a")

        // Act
        val actual = TransferId.fromHex(expected.toString())

        // Assert
        assertEquals(expected, actual)
        assertEquals("0000002a", expected.toString())
    }

    @Test
    fun `transfer session exposes accepted progress fields`() {
        // Arrange
        val session =
            TransferSession(
                id = TransferId.fromHex("1"),
                destination = ch.trancee.meshlink.model.PeerIdentity.ZERO,
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

    @Test
    fun `hex id rejects more than eight characters`() {
        // Arrange
        val hex = "000000000"

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { TransferId.fromHex(hex) }
    }
}
