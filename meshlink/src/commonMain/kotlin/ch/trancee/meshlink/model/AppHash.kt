package ch.trancee.meshlink.model

import ch.trancee.meshlink.crypto.Crypto
import ch.trancee.meshlink.util.toBytesBE
import ch.trancee.meshlink.util.toULongBE
import kotlin.jvm.JvmInline
import kotlin.text.HexFormat

/**
 * 128-bit application isolation hash.
 *
 * AppHash = first128Bits(SHA-256("MeshLink app-id v1" || UTF8(appId))).
 *
 * Never derived from peer identity or keys. Two MeshLink instances with different `appId` values
 * produce different AppHash values and **never** interoperate, even if they share the same
 * meshHash.
 *
 * Backed by `Pair<ULong, ULong>` (two 64-bit limbs) matching the PeerIdentity representation
 * pattern to avoid ByteArray allocation in hot paths.
 *
 * SPEC-ANCHOR: app-hash
 *
 * @see ch.trancee.meshlink.crypto.Crypto
 * @see ch.trancee.meshlink.model.PeerIdentity
 */
@JvmInline
public value class AppHash(private val value: Pair<ULong, ULong>) {

    override fun toString(): String = toHexString()

    private fun toHexString(): String =
        value.first.toHexString(HexFormat { number.minLength = APP_HASH_HALF_HEX_LENGTH }) +
            value.second.toHexString(HexFormat { number.minLength = APP_HASH_HALF_HEX_LENGTH })

    public companion object {

        /** The zero app hash (all bytes zero) — for initialization and comparison. */
        public val ZERO: AppHash = AppHash(0UL to 0UL)

        /**
         * Derives an AppHash from an application ID.
         *
         * Computes the first 128 bits of SHA-256("MeshLink app-id v1" || UTF8(appId)).
         *
         * @param appId Application identifier (reverse-DNS format recommended).
         * @return 16-byte big-endian app hash.
         */
        public fun derive(appId: String): AppHash {
            val prefix = "MeshLink app-id v1".encodeToByteArray()
            val suffix = appId.encodeToByteArray()
            val combined = prefix + suffix
            val digest = Crypto.sha256(combined).getOrThrow()
            return AppHash(digest.toULongBE(0) to digest.toULongBE(APP_HASH_HALF_BYTE_LENGTH))
        }

        /**
         * Creates an AppHash from a 16-byte big-endian ByteArray.
         *
         * @throws IllegalArgumentException if [bytes] is not exactly 16 bytes.
         */
        public fun fromBytes(bytes: ByteArray): AppHash {
            require(bytes.size == APP_HASH_BYTE_LENGTH) {
                "AppHash must be exactly $APP_HASH_BYTE_LENGTH bytes"
            }
            return AppHash(bytes.toULongBE(0) to bytes.toULongBE(APP_HASH_HALF_BYTE_LENGTH))
        }
    }

    /** Returns the 16-byte big-endian representation. */
    public fun toByteArray(): ByteArray {
        val result = ByteArray(APP_HASH_BYTE_LENGTH)
        value.first.toBytesBE().copyInto(result, 0)
        value.second.toBytesBE().copyInto(result, APP_HASH_HALF_BYTE_LENGTH)
        return result
    }
}

private const val APP_HASH_BYTE_LENGTH: Int = 16
private const val APP_HASH_HALF_BYTE_LENGTH: Int = 8
private const val APP_HASH_HALF_HEX_LENGTH: Int = 16
