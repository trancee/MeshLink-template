package ch.trancee.meshlink.wire.codec

import ch.trancee.meshlink.model.FrameType
import ch.trancee.meshlink.wire.model.WireFrame

private const val CODE_SIZE: Int = 1
private const val VERSION_SIZE: Int = 1
private const val LENGTH_SIZE: Int = 2
private const val HEADER_SIZE: Int = CODE_SIZE + VERSION_SIZE + LENGTH_SIZE
private const val MAX_PAYLOAD_SIZE: Int = 65535

/**
 * Canonical MeshLink wire codec frame envelope codec.
 *
 * The envelope layout (matching `specs/codecs/frames.yaml` → `frame.envelope`):
 * ```
 *  offset 0:  code    (1 byte, unsigned)    — [FrameType] wire code
 *  offset 1:  version (1 byte, unsigned)    — protocol version
 *  offset 2-3: length  (2 bytes, little-endian unsigned) — payload length
 *  offset 4..: payload (≤ [MAX_PAYLOAD_SIZE] bytes)
 * ```
 *
 * Encoding never uses the enum ordinal — only [FrameType.code].
 * Decoding via [decode] rejects unknown frame codes, matching the
 * `unknown: reject` policy in `specs/codecs/enums.yaml`.
 *
 * SPEC-ANCHOR: wire-frame
 */
public object FrameCodec {
    /** Maximum payload size expressible in the 2-byte length field. */
    public const val MAX_PAYLOAD: Int = MAX_PAYLOAD_SIZE

    /** Encode a frame envelope: code + version + length-LE + payload. */
    public fun encode(type: FrameType, version: UByte, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_SIZE) {
            "Frame payload exceeds maximum size of $MAX_PAYLOAD_SIZE bytes"
        }
        val writer = FrameWriter(HEADER_SIZE + payload.size)
        writer.writeUByte(type.code)
        writer.writeUByte(version)
        writer.writeUShortLE(payload.size.toUShort())
        writer.writeBytes(payload)
        return writer.toByteArray()
    }

    /**
     * Decode a frame envelope.
     *
     * @throws IllegalArgumentException if the buffer is too short for the header,
     *   if the payload length exceeds the remaining bytes, or if the frame code
     *   is not a known [FrameType].
     */
    public fun decode(bytes: ByteArray): WireFrame {
        val reader = FrameReader(bytes)
        val type = FrameType.fromCode(reader.readUByte())
        val version = reader.readUByte()
        val length = reader.readUShortLE().toInt()
        val payload = reader.readBytes(length)
        return WireFrame(type, version, payload)
    }
}
