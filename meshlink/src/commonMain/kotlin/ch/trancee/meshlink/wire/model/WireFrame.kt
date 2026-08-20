package ch.trancee.meshlink.wire.model

import ch.trancee.meshlink.model.FrameType

/**
 * Decoded canonical MeshLink wire codec frame.
 *
 * After [ch.trancee.meshlink.wire.codec.FrameCodec.decode] splits the 4-byte
 * envelope (code + version + length), the remaining bytes become [payload].
 * The [type] is resolved via [FrameType.fromCode], which rejects unknown codes.
 *
 * Equality compares payload *content*, not array identity.
 *
 * SPEC-ANCHOR: wire-frame
 */
public data class WireFrame(
    public val type: FrameType,
    public val version: UByte,
    public val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireFrame) return false
        return type == other.type &&
            version == other.version &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
