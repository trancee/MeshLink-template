package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class MessageTest {

    @Test
    fun `Message has required properties`() {
        val msg =
            Message(
                id = MessageId(789u),
                origin = PeerIdentity.generate(),
                payload = "hello".toByteArray(),
                completedAt = Clock.System.now(),
            )

        assertEquals(MessageId(789u), msg.id)
        assertNotNull(msg.origin)
        assertEquals("hello".toByteArray().toList(), msg.payload.toList())
        assertNotNull(msg.completedAt)
    }
}
