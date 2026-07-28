package ch.trancee.meshlink.model

import kotlin.jvm.JvmInline

/**
 * Ed25519 public key for mesh identity verification. Stored as a 32-byte array, serialized as hex
 * string.
 *
 * SPEC-ANCHOR: identity-key-model
 */
@JvmInline
public value class IdentityKey(private val bytes: ByteArray) {
    /** Provides string representation for implicit conversion in string templates. */
    override fun toString(): String = bytes.toHexString()

    public companion object {
        /** Creates an IdentityKey from a hex string. */
        public fun fromHex(hex: String): IdentityKey {
            require(hex.length == 64) { "IdentityKey must be 64 hex chars (32 bytes)" }
            return IdentityKey(hex.hexToByteArray())
        }

        /** Creates an IdentityKey from raw bytes. */
        public fun fromBytes(bytes: ByteArray): IdentityKey {
            require(bytes.size == 32) { "IdentityKey must be 32 bytes" }
            return IdentityKey(bytes)
        }
    }
}
