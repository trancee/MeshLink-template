# Transfer Layer

> **Specification**: [SPEC.md §9](../../SPEC.md#transfer-layer)  
> **Design rationale**: [Payload Transfer Protocol](../decisions/transfer/payload-transfer-protocol.md), [Transfer Identifier](../decisions/transfer/transfer-identifier.md), [Source/Sink Contract](../decisions/transfer/transfer-source-sink-contract.md)  
> **Machine-readable**: [specs/protocol/state-machines.yaml](../../specs/protocol/state-machines.yaml), [specs/codecs/frames.yaml](../../specs/codecs/frames.yaml), [specs/codecs/models.yaml](../../specs/codecs/models.yaml)

## Platform-Specific Notes

### Android

- `TransferSource.read()` called on IO dispatcher; avoid blocking calls
- `TransferSink.write()` called on IO dispatcher; implement random-access writes efficiently (e.g., `RandomAccessFile` or memory-mapped)
- Large transfers (>100 MB) should use `ParcelFileDescriptor` or `FileChannel` for zero-copy where possible
- Background transfers require `foregroundServiceType="dataSync"` or `"connectedDevice"` service

### iOS

- `TransferSource.read()` called on background queue; implement `read(offset: Int, length: Int) async throws -> Data`
- `TransferSink.write()` called on background queue; use `FileHandle` for random-access writes
- Background transfers require `UIBackgroundModes` including `bluetooth-central` and `bluetooth-peripheral`
- App may be suspended during large transfers; implement `TransferSource`/`TransferSink` to survive process death (persist offset)

## Quick Links

- [SPEC.md §9 — Full transfer spec](../../SPEC.md#transfer-layer)
- [Payload Transfer Protocol ADR](../decisions/transfer/payload-transfer-protocol.md)
- [Transfer Identifier ADR](../decisions/transfer/transfer-identifier.md)
- [Source/Sink Contract ADR](../decisions/transfer/transfer-source-sink-contract.md)
- [State Machines Spec](../../specs/protocol/state-machines.yaml)
- [Codec Frames Spec](../../specs/codecs/frames.yaml)
- [Codec Models Spec](../../specs/codecs/models.yaml)
