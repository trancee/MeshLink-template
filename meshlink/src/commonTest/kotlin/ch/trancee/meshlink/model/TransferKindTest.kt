package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferKindTest {

    @Test
    fun `TransferKind has MESSAGE and PAYLOAD`() {
        assertEquals(2, TransferKind.values().size)
        assertTrue(TransferKind.MESSAGE != TransferKind.PAYLOAD)
    }

    @Test
    fun `TransferKind wire codes match spec`() {
        // Arrange & Act
        val messageCode = TransferKind.MESSAGE.code
        val payloadCode = TransferKind.PAYLOAD.code

        // Assert — matches specs/codecs/enums.yaml
        assertEquals(0x00u, messageCode)
        assertEquals(0x01u, payloadCode)
    }
}
