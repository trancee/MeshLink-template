package ch.trancee.meshlink.util

/**
 * Mesh hash derivation for application isolation.
 *
 * MeshHash = FNV-1a 32-bit(appId) truncated to 16 bits.
 *
 * Used in the discovery advertisement to prevent cross-application discovery. See
 * docs/decisions/discovery/mesh-hash-derivation.md.
 *
 * SPEC-ANCHOR: mesh-hash
 */
public object MeshHash {

    private const val FNV_OFFSET_BASIS: UInt = 0x811c9dc5u
    private const val FNV_PRIME: UInt = 0x01000193u
    private const val MESH_HASH_MASK: UInt = 0xFFFFu

    /**
     * Derives a 16-bit mesh hash from an application ID using FNV-1a 32-bit.
     *
     * @param appId Application identifier (reverse-DNS format recommended)
     * @return 16-bit hash value (0-65535)
     */
    public fun derive(appId: String): UInt =
        appId.encodeToByteArray().fold(FNV_OFFSET_BASIS) { hash, byte ->
            (hash xor byte.toUInt()) * FNV_PRIME
        } and MESH_HASH_MASK
}
