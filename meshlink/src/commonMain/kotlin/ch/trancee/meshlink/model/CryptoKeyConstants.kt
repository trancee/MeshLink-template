package ch.trancee.meshlink.model

/**
 * Shared wire/encoding constants for Ed25519 and X25519 public keys.
 *
 * Both key types use 32-byte keys (64 hex chars) per the crypto design ADR.
 */
internal object CryptoKeyConstants {
    const val PUBLIC_KEY_HEX_LENGTH: Int = 64
    const val PUBLIC_KEY_BYTE_LENGTH: Int = 32
}
