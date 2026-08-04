package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferTypeTest {

    @Test
    fun `TransferType has MESSAGE and PAYLOAD`() {
        assertEquals(2, TransferType.values().size)
        assertTrue(TransferType.MESSAGE != TransferType.PAYLOAD)
    }

    @Test
    fun `TransferType wire codes match spec`() {
        // Arrange & Act
        val messageCode = TransferType.MESSAGE.code
        val payloadCode = TransferType.PAYLOAD.code

        // Assert — matches specs/codecs/enums.yaml
        assertEquals(0x00u, messageCode)
        assertEquals(0x01u, payloadCode)
    }
}
