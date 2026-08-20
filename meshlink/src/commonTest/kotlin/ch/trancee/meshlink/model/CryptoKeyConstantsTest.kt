package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoKeyConstantsTest {
    @Test
    fun `public key constants match 32-byte Ed25519 and X25519 key sizes`() {
        // Arrange — both Ed25519 and X25519 use 32-byte keys (64 hex chars)

        // Act
        val hexLength = CryptoKeyConstants.PUBLIC_KEY_HEX_LENGTH
        val byteLength = CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH

        // Assert
        assertEquals(64, hexLength, "Hex-encoded key must be 64 characters")
        assertEquals(32, byteLength, "Raw key must be 32 bytes")
    }
}
