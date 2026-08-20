package ch.trancee.meshlink.wire.model

/**
 * Byte ordering for numeric wire codec fields.
 *
 * The canonical MeshLink frame envelope stores `length` as little-endian,
 * while most other fields (including `PeerIdentity` as two big-endian ULongs)
 * are big-endian. This enum lets [ch.trancee.meshlink.wire.codec.Field]s declare
 * their byte order independently of their [FieldType].
 *
 * SPEC-ANCHOR: wire-frame
 */
public enum class ByteOrder {
    BIG_ENDIAN,
    LITTLE_ENDIAN,
}
