package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/** X25519 public key for Noise handshake key agreement. */
@JvmInline
public value class HandshakeKey(private val bytes: ByteArray) {
    override fun toString(): String = bytes.toHexString()

    public companion object {
        public fun fromHex(hex: String): HandshakeKey {
            require(hex.length == PUBLIC_KEY_HEX_LENGTH) {
                "HandshakeKey must be $PUBLIC_KEY_HEX_LENGTH hex chars ($PUBLIC_KEY_BYTE_LENGTH bytes)"
            }
            return HandshakeKey(hex.hexToByteArray())
        }

        public fun fromBytes(bytes: ByteArray): HandshakeKey {
            require(bytes.size == PUBLIC_KEY_BYTE_LENGTH) {
                "HandshakeKey must be exactly $PUBLIC_KEY_BYTE_LENGTH bytes"
            }
            return HandshakeKey(bytes)
        }
    }
}

private const val PUBLIC_KEY_HEX_LENGTH: Int = 64
private const val PUBLIC_KEY_BYTE_LENGTH: Int = 32
