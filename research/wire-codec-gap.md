# Research: Wire Codec Scaffold Gap

**Ticket:** #23 — Audit wire codec scaffold gap
**Parent:** #15 — Wayfinder Map
**Date:** 2026-08-19
**Status:** Resolved

## Summary

The wire codec spec is fully defined across three YAML contracts (`enums.yaml`,
`models.yaml`, `frames.yaml`) with 14 wire frames, 20 enums, and 15 encodable
data models. However, `meshlink/src/commonMain/kotlin/ch/trancee/meshlink/wire/`
is **completely empty** — zero Kotlin implementation files. The only codec-related
scaffolding outside `wire/` consists of enum declarations in `model/Enums.kt`
and `model/TransferKind.kt` that define explicit wire codes but perform **no
encoding, decoding, or bounded I/O**. No `WireReader`, `WireWriter`, frame parser,
frame encoder, or bounded reader/writer exists anywhere in the codebase.

## 1. Verify `wire/` is empty

**Confirmed.** The directory
`meshlink/src/commonMain/kotlin/ch/trancee/meshlink/wire/` exists but contains
**no `.kt` files**. No `commonTest/.../wire/` test directory exists either.

A glob for `*.kt` under that path returns nothing. A structural search for
`WireReader`, `WireWriter`, `FrameDecoder`, `FrameEncoder`, `BoundedReader`,
`BoundedWriter`, `readFrame`, `writeFrame`, and `MeshLinkWireCodec` across all
of `meshlink/src` returns **zero matches**.

## 2. Codec spec vs. implementation gap

### `specs/codecs/enums.yaml` — 20 enums, zero codecs

Defines 20 enums with explicit wire codes. Key wire-relevant enums:

| Enum | Type | Wire codes |
|---|---|---|
| `FrameType` | public | 0x00–0x42 (15 frames, grouped by protocol area) |
| `TransferKind` | public | 0x00 MESSAGE, 0x01 PAYLOAD |
| `PayloadDecision` | internal | 0x00 ACCEPTED, 0x01 REJECTED |
| `TransferResultCode` | internal | 0x00–0x04 (with trailing payload variants) |
| `KeyType`, `KeyRotationReason`, `HandshakePattern`, `Priority`, `NoiseLayer`, `NoiseSessionState`, `NoiseRole`, `DecryptedFailureReason`, `TransportFallbackReason`, `Bearer`, `L2capState`, `RegulatoryRegion`, `PeerState`, `PeerTrust`, `VerificationLevel`, `TransferState`, `DiagnosticSeverity`, `KeyRotationState`, `PeerLifecycle` | — | — |

Spec note: "Enum declaration ordinal is never a wire value" and
"unknown: reject" — i.e., unknown codes must be rejected, not silently mapped.

### `specs/codecs/models.yaml` — 15 encodable models, zero codecs

Defines 15 data models with explicit field layouts and constraints:

- `PeerIdentity` — 16-byte value class, Pair<ULong, ULong> backing
- `SeqNo` — 32-bit unsigned, wrap-around comparison (RFC 8966 §3.7)
- `Scoreboard` / `MutableScoreboard` — dynamic bitfield for SACK
- `RouteCandidate` — routing candidate with cost, hopCount, linkQuality
- `TransferSession` — chunked transfer driver
- `TransferId` — 32-bit origin-scoped identifier
- `TransferResult` — sealed interface (Completed, Cancelled, Expired, UnrecoverableFailure, TrustFailure)
- `LinkQuality` — smoothed RSSI
- `L2capHealth` — L2CAP circuit-breaker state
- `IdentityKey` — 32-byte Ed25519 key
- `HandshakeKey` — 32-byte X25519 key
- `AppHash` — 128-bit application isolation hash
- `RouteStatement` — destination-signed routing statement

Models with explicit `toBytes`/`fromBytes` wire serialization in the spec (e.g.,
`PeerIdentity`, `SeqNo`, `TransferId`, `AppHash`) are implemented as model classes
in `model/` and are used by existing code — but **no wire codec layer serializes
them into frames**.

### `specs/codecs/frames.yaml` — 14 wire frames, zero frame encoders/decoders

Defines the `frame` envelope (code: UByte, version: UByte, length: UShort LE,
payload: ByteArray) and 14 concrete wire frame layouts:

| Frame | Code | Key fields |
|---|---|---|
| `MESH_ENVELOPE` | 0x00 | destination (PeerIdentity 16B), payload (E2E encrypted), hopLimit |
| `ROUTE_ADVERTISEMENT` | 0x01 | statement (RouteStatement), routeCost (UInt 4B), hopCount (UByte) |
| `ROUTE_WITHDRAWAL` | 0x02 | destination (16B), sequenceNumber (UInt 4B) |
| `ROUTE_DIGEST` | 0x03 | revision (UInt 4B), digest (8B) |
| `ROUTE_SEQUENCE_ADVANCEMENT` | 0x04 | requester, destination (16B each), sequenceNumber, requestId, hopLimit |
| `ROUTE_SYNCHRONIZATION` | 0x05 | revision (UInt 4B) |
| `ROUTE_SNAPSHOT` | 0x06 | revision (UInt 4B) |
| `PAYLOAD_MANIFEST` | 0x20 | id, kind, origin/destination (16B each), priority, timeToLive, totalLength, chunkSize, chunkCount |
| `PAYLOAD_DECISION` | 0x21 | id, kind, decision, reason (+ trailing payload for REJECTED) |
| `PAYLOAD_CHUNK` | 0x22 | id, kind, index, payload |
| `PAYLOAD_ACKNOWLEDGEMENT` | 0x23 | id, kind, start (UInt 4B), bitmap (fixed 32B) |
| `PAYLOAD_CANCELLATION` | 0x24 | id, kind, reason (+ trailing payload per TransferResultCode) |
| `KEY_ROTATION` | 0x40 | version, appHash (16B), identity (16B), old/newGeneration, newIdentityKey, newHandshakeKey, reason, dual signatures (64B each) |
| `EPOCH_COMMIT` | 0x41 | newEpoch, finalOldOutboundCounter, handshakeHash (32B) |
| `EPOCH_ACKNOWLEDGEMENT` | 0x42 | newEpoch, finalOldOutboundCounter, handshakeHash (32B) |

Plus: `route_statement` layout, `gatt_metadata` layout, `gatt_fragment` layout
(Little-Endian index + totalLength + payload, pre-auth max 4096 bytes),
`l2cap_frame` layout (UShort LE length prefix + frameBytes), and
`discovery_advertisement` layout (packed header byte, mesh_hash, capability_flags,
peer_hint).

**None of these frame encoders/decoders exist in code.**

## 3. `wire-compat` test resource README

`meshlink/src/commonTest/resources/wire-compat/README.md` accurately describes
the scaffold state:

> Hex test vectors for byte-for-byte wire codec verification across all KMP
> targets. [...] This directory is populated as wire codec frame encodings land.
> Until then, host JVM tests cover codec logic.

The directory currently contains only `README.md` — no hex vector fixtures.
This accurately reflects that no frame encodings have landed yet.

## 4. Traceability map status

`specs/traceability/specification-map.yaml` correctly tracks the gap:

- Line 283: `"Custom MeshLink Wire Codec (frames, enums, models, bounded reader/writer)"`
  is listed under `implementation_status.not_implemented`
- There is **no** `wire_codec` entry under `test_files_by_layer` (the layers
  listed are: `data_model`, `enums`, `routing`, `transfer`, `power`,
  `diagnostics`, `transfer_result`, `version`)
- The `data-model` cross-reference points to `model/` implementation files
  (PeerIdentity, SeqNo, Scoreboard, etc.) and `specs/codecs/models.yaml`, but
  these are the **model classes**, not the wire codec encoder/decoder layer.

**The traceability map is correct and up-to-date.**

## 5. Vertical slice 4 requirements (from `specs/tests/scaffold-alignment-plan.md`)

> ## Vertical slice 4 — codec foundation
>
> 1. Add canonical Frame/Field/FieldType contracts and explicit code validation
>    → verify: codec unit tests
> 2. Implement bounded reader/writer and enum codecs → verify: golden/malformed
>    vector tests
> 3. Add cross-platform byte-equality fixtures → verify: JVM tests

**None of the slice 4 deliverables exist.** There are no Frame/Field/FieldType
contract classes, no bounded reader/writer, no enum codec, no golden vectors,
and no byte-equality fixtures. The wire/ directory is empty, ready to receive
this implementation.

## 6. Partial codec files outside `wire/`

The only codec-related scaffolding outside `wire/` consists of:

| File | What it has | What it lacks |
|---|---|---|
| `model/Enums.kt` | `FrameType` public enum (15 entries with explicit `UByte` codes) + `FrameCode` private constants object. `// SPEC-ANCHOR: enums` | No `fromCode`/`toCode` lookup, no frame encoding/decoding |
| `model/TransferKind.kt` | `TransferKind` enum (MESSAGE=0x00, PAYLOAD=0x01) with `.code` | No codec serialization |
| `diagnostics/DiagnosticEvent.kt` | `frameType: UByte` field on `RouteDecryptFailureEvent` | No wire codec integration |
| `EnumCoverageTest.kt` | Lists `FrameType.entries` in coverage checklist | Verifies enum presence, not wire codec behavior |
| `EnumCoverageTest.kt` | Counts 25 enums total | — |

These are **enum declarations and test scaffolding** — they define the wire
codes but perform no encoding, decoding, length validation, or bounded I/O.
They represent the boundary where the codec spec's enum layer begins and the
implementation ends.

## Gap Summary

```text
specs/codecs/{enums,models,frames}.yaml  →  fully specified (536 lines + 518 lines + 440 lines)
meshlink/src/.../wire/                    →  EMPTY (0 .kt files)
model/Enums.kt, model/TransferKind.kt     →  enum codes only (no codec)
wire-compat/README.md                     →  accurate (says "until encodings land")
traceability map                         →  correctly marks "Custom MeshLink Wire Codec" as not_implemented
scaffold-alignment slice 4               →  unstarted
```

**The gap:** A complete, normative wire codec specification exists, but the
entire implementation layer (bounded reader/writer, frame encoders/decoders,
enum codecs, byte-equality fixtures) is absent. The spec is ready to be
implemented; it has not been started.
