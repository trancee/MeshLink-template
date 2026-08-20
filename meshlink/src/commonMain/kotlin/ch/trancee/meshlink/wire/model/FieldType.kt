package ch.trancee.meshlink.wire.model

/**
 * Canonical wire-level field types.
 *
 * Each type declares its fixed size in bytes (or `null` for variable-length
 * arrays). Encoding never uses enum ordinal — only the declared [byteCount]
 * and the [ByteOrder] of the enclosing [Field] determine the on-wire layout.
 *
 * SPEC-ANCHOR: wire-frame
 */
public enum class FieldType(
    public val byteCount: Int?,
) {
    UBYTE(1),
    USHORT(2),
    UINT(4),
    ULONG(8),
    BYTE_ARRAY(null),
    PEER_IDENTITY(16),
}
