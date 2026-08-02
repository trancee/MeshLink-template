package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/**
 * Ed25519 public key for MeshLink identity verification and signing.
 *
 * SPEC-ANCHOR: identity-key-model
 */
@JvmInline
public value class IdentityKey(private val bytes: ByteArray) {
    override fun toString(): String = bytes.toHexString()

    public companion object {
        public fun fromHex(hex: String): IdentityKey {
            require(hex.length == PUBLIC_KEY_HEX_LENGTH) {
                "IdentityKey must be $PUBLIC_KEY_HEX_LENGTH hex chars ($PUBLIC_KEY_BYTE_LENGTH bytes)"
            }
            return IdentityKey(hex.hexToByteArray())
        }

        public fun fromBytes(bytes: ByteArray): IdentityKey {
            require(bytes.size == PUBLIC_KEY_BYTE_LENGTH) {
                "IdentityKey must be $PUBLIC_KEY_BYTE_LENGTH bytes"
            }
            return IdentityKey(bytes)
        }
    }
}

private const val PUBLIC_KEY_HEX_LENGTH: Int = 64
private const val PUBLIC_KEY_BYTE_LENGTH: Int = 32
