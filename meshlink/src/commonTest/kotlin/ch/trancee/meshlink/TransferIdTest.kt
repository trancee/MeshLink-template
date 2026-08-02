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
        assertFailsWith<IllegalArgumentException> { TransferId.fromHex("000000000") }
    }

    @Test
    fun `toUInt returns raw value`() {
        val id = TransferId.fromHex("deadbeef")
        assertEquals(0xDEADBEEFu, id.toUInt())
    }

    @Test
    fun `fromUInt creates id from raw value`() {
        val id = TransferId.fromUInt(0xCAFEBABEu)
        assertEquals(0xCAFEBABEu, id.toUInt())
        assertEquals("cafebabe", id.toString())
    }

    @Test
    fun `toByteArray produces 4-byte big-endian`() {
        val id = TransferId.fromHex("01020304")
        val bytes = id.toByteArray()
        assertEquals(4, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
        assertEquals(0x03.toByte(), bytes[2])
        assertEquals(0x04.toByte(), bytes[3])
    }

    @Test
    fun `fromBytes roundtrips through toByteArray`() {
        val original = TransferId.fromHex("aabbccdd")
        val bytes = original.toByteArray()
        val restored = TransferId.fromBytes(bytes)
        assertEquals(original, restored)
    }

    @Test
    fun `fromBytes throws for invalid byte array size`() {
        assertFailsWith<IllegalArgumentException> { TransferId.fromBytes(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { TransferId.fromBytes(ByteArray(5)) }
    }

    @Test
    fun `inc operator increments with wrap`() {
        var id = TransferId.fromHex("00000000")
        id = id.inc()
        assertEquals("00000001", id.toString())

        id = TransferId.fromHex("ffffffff")
        id = id.inc()
        assertEquals("00000000", id.toString())
    }

    @Test
    fun `compareTo orders by UInt value`() {
        val id1 = TransferId.fromHex("00000001")
        val id2 = TransferId.fromHex("00000002")
        val id3 = TransferId.fromHex("ffffffff")

        assertEquals(-1, id1.compareTo(id2))
        assertEquals(1, id2.compareTo(id1))
        assertEquals(0, id1.compareTo(id1))
        assertEquals(-1, id1.compareTo(id3)) // 1 < UINT_MAX
    }
}
