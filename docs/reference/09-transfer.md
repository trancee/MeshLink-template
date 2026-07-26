# Transfer Layer

> Source: [SPEC.md §9](../../SPEC.md#9-transfer-layer)

## 9.1 Transfer Session

The `TransferSession` model is defined in [§3.4](03-data-models.md#34-message-header-model). Key fields:

- `chunkSize`: Selected by local power mode, bounded by peer MTU
- `scoreboard`: Dynamic bitfield (`ByteArray`) of length `ceil(totalChunks / 8)` bytes; bit N = 1 means chunk N received
- `totalBytes`/`bytesReceived`: Progress tracking in bytes (not chunks)

[Decision: docs/decisions/model/data-model.md]

## 9.2 Selective Acknowledgment

- **Dynamic bitfield encoding**: Bitfield length = `ceil(totalChunks / 8)` bytes, derived from `totalChunks` known via TransferSession. Bit N = 1 means chunk N is received (standard SACK convention).
- **Variable overhead**: Small transfers (10 chunks) use 1 byte; large transfers (1000 chunks) use 125 bytes
- Partial ACK never forces re-send of already-received chunks
- Scoreboard clears on session completion or explicit failure

## 9.3 Cut-Through Relay

- Pipeline forwarding without full reassembly
- Relays decrypt (hop layer) → re-encrypt (next hop) → forward
- Relay buffers maintained for local retransmission handling

## 9.4 TransferAck Wire Format

```text
TransferAck {
  sessionId: UInt64 (8 bytes)
  bitfield: UInt8Vector (ceil(totalChunks / 8) bytes; bit N = 1 means chunk N received; receiver knows totalChunks from session)
}
```

The bitfield length is derived from `totalChunks` in the `TransferSession`, so no extra length field is needed in the SACK message.

If the `TransferSession` is not found (expired or already completed), the receiver MUST reject the `TransferAck` with `TransferError.SessionNotFound`.

[Decision: docs/decisions/wire/wire-format-spec.md]
