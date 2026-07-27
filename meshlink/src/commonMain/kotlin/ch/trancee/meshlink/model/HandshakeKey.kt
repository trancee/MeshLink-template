package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/**
 * X25519 Diffie-Hellman public key for Noise handshake key exchange. Stored as a 32-byte array,
 * serialized as hex string.
 *
 * Unlike [IdentityKey] (Ed25519 signing key), this key is used exclusively for DH key agreement in
 * Noise handshakes — it is not used for authentication or identity verification. The
 * signing/identity role is handled by [IdentityKey].
 *
 * SPEC-ANCHOR: handshake-key-model
 */
@JvmInline
public value class HandshakeKey(private val bytes: ByteArray) {
    /** Raw 32-byte key data. */
    public val raw: ByteArray
        get() = bytes

    /** Hex-encoded representation. */
    public val hex: String
        get() = bytes.toHexString()

    public companion object {
        /** Creates a HandshakeKey from a hex string. */
        public fun fromHex(hex: String): HandshakeKey {
            require(hex.length == 64) { "HandshakeKey must be 64 hex chars (32 bytes)" }
            return HandshakeKey(hex.hexToByteArray())
        }

        /** Creates a HandshakeKey from raw bytes. */
        public fun fromBytes(bytes: ByteArray): HandshakeKey {
            require(bytes.size == 32) { "HandshakeKey must be exactly 32 bytes" }
            return HandshakeKey(bytes)
        }
    }
}
