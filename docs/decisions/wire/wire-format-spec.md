# Wire Format Specification

## Status: Proposed

## Overview

FlatBuffers-based wire protocol for MeshLink. All schemas live in `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/wire/`.

**Important**: MeshLink uses a **custom pure-Kotlin FlatBuffers implementation** (not `flatc` codegen). The implementation is optimized for KMP, zero-copy decoding, and minimal binary size. See `docs/explanation/why-pure-kotlin-flatbuffers.md` for rationale.

## Schema Design Principles

1. **All fields required** — No optional fields to simplify parsing
2. **Version in message** — Each message carries protocol version
3. **Forward compatible** — Unknown fields silently skipped
4. **Compact encoding** — Minimize overhead for BLE transport

## FlatBuffers Schemas

The schemas below define the logical structure. The actual encoding/decoding is implemented in pure Kotlin (no `flatc` generated code).

### Base Types

```flatbuffers
// src/commonMain/proto/meshlink.fbs

table RoutingFrame {
  // Destination for this message
  destination: uint8Vector(16);  // 16-byte PeerIdentity
  
  // Inner payload (encrypted or handshake)
  payload: uint8Vector(0);
  
  // Hop limit (0 = direct, 1+ = max hops)
  hop_limit: uint8 = 0;
}

table KeyRotationAnnouncement {
  // The NEW identity key (32 bytes)
  identity_key: uint8Vector(32);
  
  // Seqno (always 1 for rotation)
  seq_no: uint32;
  
  // Ed25519 signature (64 bytes)
  signature: uint8Vector(64);
  
  // Reason code
  reason: uint8;
}

table RouteUpdate {
  // Destination peer ID (16 bytes)
  destination: uint8Vector(16);
  
  // Next hop toward destination (16 bytes)
  next_hop: uint8Vector(16);
  
  // Sequence number
  seq_no: uint32;
  
  // Metric (RSSI + flags)
  metric: uint32;
  
  // Flags
  flags: uint8;
  
  // AEAD ciphertext = encrypted_payload || 16-byte Poly1305 tag
  // Nonce derived from Noise session counter (not transmitted)
  ciphertext: uint8Vector(0);
}

table RouteWithdrawal {
  // Destination peer ID (16 bytes)
  destination: uint8Vector(16);
  
  // Sequence number
  seq_no: uint32;
  
  // AEAD ciphertext = encrypted_payload || 16-byte Poly1305 tag
  // Nonce derived from Noise session counter (not transmitted)
  ciphertext: uint8Vector(0);
}

table RouteDigest {
  // Peer ID (16 bytes)
  peer_id: uint8Vector(16);
  
  // Digest of full route table (FNV-1a 32-bit)
  digest: uint32;
}
```

### Message Types

All wire messages use a union:

```flatbuffers
// Message type enum
enum MessageType: byte {
  // Handshake and routing
  MESH_ENVELOPE = 1,
  ROUTE_UPDATE = 2,
  ROUTE_WITHDRAWAL = 3,
  ROUTE_DIGEST = 4,
  
  // Transfer
  TRANSFER_CHUNK = 5,
  TRANSFER_ACK = 6,
  TRANSFER_CANCEL = 7,
  
  // Key management
  KEY_ROTATION_ANNOUNCEMENT = 8
}

// Union for payload
union WirePayload = RoutingFrame | RouteUpdate | RouteWithdrawal | RouteDigest | 
                    TransferChunk | TransferAck | TransferCancel | 
                    KeyRotationAnnouncement;

table WireFrame {
  // Protocol version
  version: uint8 = 1;
  
  // Message type
  type: MessageType;
  
  // Payload (oneof)
  payload: WirePayload;
}
```

### Transfer Messages

```flatbuffers
table TransferChunk {
  // Session ID (8 bytes, 64-bit)
  session_id: uint8Vector(8);
  
  // Byte offset in overall payload
  offset: uint32;
  
  // Length of this chunk
  length: uint16;
  
  // Data bytes
  data: uint8Vector(0);
}

table TransferAck {
  // Session ID (8 bytes, 64-bit)
  session_id: uint8Vector(8);
  
  // Dynamic bitfield: ceil(totalChunks / 8) bytes
  // Bit N = 1 if chunk N is received (standard SACK convention)
  // Receiver knows totalChunks from TransferSession
  bitfield: uint8Vector(0);
}

table TransferCancel {
  // Session ID (8 bytes, 64-bit — matches TransferChunk and TransferAck)
  session_id: uint8Vector(8);
  
  // Reason code
  reason: uint8;
  
  // Optional error message
  error: string;
}
```

### Handshake Payload

```flatbuffers
table HandshakePayload {
  // Full public key for NX verification (64 bytes: Ed25519 || X25519)
  ed25519_public_key: uint8Vector(32);
  x25519_public_key: uint8Vector(32);
  
  // Replay nonce
  nonce: uint32;
  
  // Encrypted content or E2E handshake data
  content: uint8Vector(0);
}
```

## Kotlin Implementation

### Sequence Number Handling

All sequence number comparisons use the `SeqNo` wrapper type (defined in §3.1 Core Data Models) to ensure correct wrap-around handling per RFC 8966 §3.7:

```kotlin
/**
 * SeqNo comparisons MUST use signed interpretation.
 * (candidate - current).toInt() represents the signed difference.
 * Result > 0 means candidate is newer (handles wrap at 2^32).
 */
fun SeqNo.isNewerThan(other: SeqNo): Boolean = (value - other.value).toInt() > 0
```

### Buffer Utilities

```kotlin
// commonMain/kotlin/ch/trancee/meshlink/wire/ReadBuffer.kt
class ReadBuffer(private val data: ByteArray) {
  private var position: Int = 0
  
  fun readUInt8(): UByte
  fun readUInt16(): UShort
  fun readUInt32(): UInt
  fun readBytes(length: Int): ByteArray
  fun readString(): String
}

// commonMain/kotlin/ch/trancee/meshlink/wire/WriteBuffer.kt
class WriteBuffer {
  private val buffer = ByteArrayOutputStream()
  
  fun writeUInt8(value: UByte)
  fun writeUInt16(value: UShort)
  fun writeUInt32(value: UInt)
  fun writeBytes(bytes: ByteArray)
  fun writeString(str: String)
  
  fun toByteArray(): ByteArray = buffer.toByteArray()
}
```

### Wire Codec

```kotlin
// commonMain/kotlin/ch/trancee/meshlink/wire/WireCodec.kt
object WireCodec {
  fun encode(frame: WireFrame): ByteArray
  fun decode(data: ByteArray): WireFrame
  
  // Specific encoders
  fun encodeRoutingFrame(frame: RoutingFrame): ByteArray
  fun decodeRoutingFrame(data: ByteArray): RoutingFrame
  
  fun encodeRouteUpdate(update: RouteUpdate): ByteArray
  fun decodeRouteUpdate(data: ByteArray): RouteUpdate
  
  fun encodeTransferChunk(chunk: TransferChunk): ByteArray
  fun decodeTransferChunk(data: ByteArray): TransferChunk
}
```

## Version Compatibility

Since no MeshLink release has shipped:

- **Version 1** is the starting point
- All fields are forward compatible (skip unknown fields)
- No legacy schemas to support yet

Future wire changes:

- Add new message types (never remove)
- Add new fields with defaults
- Major version bump for breaking changes

## Testing Requirements

- `WireCodecTest`: verify encode/decode round-trips
- `ForwardCompatibilityTest`: verify unknown fields skipped
- `NullSafetyTest`: verify no null crashes on malformed input
- `SizeTest`: verify messages fit in BLE MTU
- `PerfTest`: verify <1 μs encode/decode per benchmark

## Related

- `docs/explanation/why-pure-kotlin-flatbuffers.md`
- `docs/decisions/data-model/core-types.md`
- RFC 8966 (Babel wire format concepts)
