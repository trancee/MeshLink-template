package ch.trancee.meshlink.wire.model

/**
 * Declarative description of a single field inside a wire frame body.
 *
 * `Field` values are *described*, never used for runtime encoding — the bounded
 * [ch.trancee.meshlink.wire.codec.FrameReader] and
 * [ch.trancee.meshlink.wire.codec.FrameWriter] perform actual byte-level I/O.
 * This class mirrors the `fields:` entries in `specs/codecs/frames.yaml`.
 *
 * @property name Human-readable field name matching the spec.
 * @property type The canonical wire type.
 * @property byteCount Explicit byte count for fixed-size fields; defaults to
 *   [FieldType.byteCount]. Pass an explicit value to override (e.g. a fixed-length
 *   `BYTE_ARRAY` whose size is not derivable from the type).
 * @property byteOrder Byte order for multi-byte numeric fields; defaults to
 *   [ByteOrder.BIG_ENDIAN].
 * @property presence Condition under which the field is present, or `null` when
 *   always present (matching the spec `presence:` key).
 */
public data class Field(
    public val name: String,
    public val type: FieldType,
    public val byteCount: Int? = type.byteCount,
    public val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
    public val presence: String? = null,
)
