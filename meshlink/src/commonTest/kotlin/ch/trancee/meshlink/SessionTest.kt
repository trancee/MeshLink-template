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
        assertNotEquals(SessionId.ZERO.toString(), id.toString())
    }

    @Test
    fun `SessionId generate creates non-zero by object equality`() {
        val id = SessionId.generate()
        assertNotEquals(SessionId.ZERO, id)
    }

    @Test
    fun `SessionId constructor with zero equals ZERO`() {
        assertEquals(SessionId.ZERO, SessionId(0UL))
    }

    @Test
    fun `SessionId constructor with non-zero is not ZERO`() {
        assertNotEquals(SessionId.ZERO, SessionId(42UL))
    }

    @Test
    fun `SessionId generate always produces non-zero`() {
        repeat(10) { assertNotEquals(SessionId.ZERO, SessionId.generate()) }
    }

    @Test
    fun `SessionId ZERO is zero`() {
        assertEquals("0000000000000000", SessionId.ZERO.toString())
    }

    @Test
    fun `SessionId fromHex roundtrips with toString`() {
        val id = SessionId.generate()
        val parsed = SessionId.fromHex(id.toString())
        assertEquals(id, parsed)
    }

    @Test
    fun `SessionId fromHex with zero`() {
        assertEquals(SessionId.ZERO, SessionId.fromHex("0"))
        assertEquals(SessionId.ZERO, SessionId.fromHex("0000000000000000"))
    }

    @Test
    fun `SessionId fromHex throws on too-long hex`() {
        try {
            SessionId.fromHex("00000000000000000") // 17 chars
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("SessionId hex must be at most 16 chars (64-bit)", e.message)
        }
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
