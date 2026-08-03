package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TransferSourceTest {

    @Test
    fun `TransferSource interface exists`() {
        // Compile-time test: interface must be implementable
        val source =
            object : TransferSource {
                override val total: Long = 100L

                override suspend fun read(offset: Long, length: Int): ByteArray = ByteArray(length)
            }
        assertEquals(100L, source.total)
    }
}
