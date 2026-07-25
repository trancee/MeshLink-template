package ch.trancee.meshlink

import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.SessionId
import ch.trancee.meshlink.model.TransferSession
import ch.trancee.meshlink.model.TransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock

class SessionTest {
    @Test
    fun `SessionId generate creates non-zero`() {
        val id = SessionId.generate()
        assertNotEquals(SessionId.ZERO.raw, id.raw)
    }

    @Test
    fun `SessionId ZERO is zero`() {
        assertEquals(0u, SessionId.ZERO.raw)
    }

    @Test
    fun `TransferSession creates with correct values`() {
        val session =
            TransferSession(
                sessionId = SessionId.ZERO,
                destination = PeerIdentity.ZERO,
                priority = Priority.HIGH,
                state = TransferState.IN_PROGRESS,
                chunkSize = 256,
                totalChunks = 10u,
                scoreboard = ch.trancee.meshlink.model.Scoreboard(10u),
                totalBytes = 1000L,
                bytesReceived = 0L,
                startedAt = Clock.System.now(),
                expiresAt = null,
                retryCount = 0,
                failureReason = null,
            )
        assertEquals(Priority.HIGH, session.priority)
        assertEquals(TransferState.IN_PROGRESS, session.state)
    }
}
