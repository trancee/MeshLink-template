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

    /** Returns a defensive copy of the raw 32-byte Ed25519 key data. */
    public fun toBytes(): ByteArray = bytes.copyOf()

    public companion object {
        public fun fromHex(hex: String): IdentityKey {
            require(hex.length == CryptoKeyConstants.PUBLIC_KEY_HEX_LENGTH) {
                "IdentityKey must be ${CryptoKeyConstants.PUBLIC_KEY_HEX_LENGTH} hex " +
                    "chars (${CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH} bytes)"
            }
            return IdentityKey(hex.hexToByteArray())
        }

        public fun fromBytes(bytes: ByteArray): IdentityKey {
            require(bytes.size == CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH) {
                "IdentityKey must be ${CryptoKeyConstants.PUBLIC_KEY_BYTE_LENGTH} bytes"
            }
            return IdentityKey(bytes)
        }
    }
}
