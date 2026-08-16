package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/**
 * X25519 public key for Noise handshake key agreement.
 *
 * SPEC-ANCHOR: handshake-key-model
 */
@JvmInline
public value class HandshakeKey(private val bytes: ByteArray) {
    override fun toString(): String = bytes.toHexString()

    /** Returns a defensive copy of the raw 32-byte X25519 key data. */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    public companion object {
        public fun fromHex(hex: String): HandshakeKey {
            require(hex.length == CryptoKeyConstants.PUBLIC_KEY_HEX_LENGTH) {
                "HandshakeKey must be ${CryptoKeyConstants.PUBLIC_KEY_HEX_LENGTH} hex " +
                    "chars (${CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH} bytes)"
            }
            return HandshakeKey(hex.hexToByteArray())
        }

        public fun fromBytes(bytes: ByteArray): HandshakeKey {
            require(bytes.size == CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH) {
                "HandshakeKey must be exactly ${CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH} bytes"
            }
            return HandshakeKey(bytes)
        }
    }
}
