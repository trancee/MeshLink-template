# Why the MeshLink Wire Codec

## The decision

MeshLink implements its own schema-driven binary format and pure-Kotlin codec.
The format is inspired by useful FlatBuffers ideas but is not required to remain
byte-compatible with FlatBuffers, use `.fbs` schemas, run `flatc`, or depend on a
FlatBuffers runtime.

The normative contracts live in:

```text
specs/codecs/frames.yaml
specs/codecs/enums.yaml
specs/codecs/models.yaml
```

## Why a custom format

MeshLink needs one codec across JVM, Android, and iOS under tight BLE frame,
allocation, dependency, and malformed-input constraints. General FlatBuffers
runtimes and generated APIs include capabilities MeshLink does not need and do
not provide the desired commonMain/runtime-dependency shape.

A custom format allows the protocol to choose widths, headers, presence rules,
and specialized representations for its actual workloads rather than preserve
generic compatibility that no external consumer requires.

## Canonical frame envelope

Every canonical MeshLink frame starts with:

```text
Frame {
    code: UByte
    version: UByte
    length: UShort
    payload: Byte[length]
}
```

`code` selects the explicit frame contract, `version` is bound into security
context, and `length` is the bounded payload length. GATT/L2CAP bearer framing
wraps these bytes without changing them. Longer names remain in nested records
when their enclosing type does not make the context unambiguous, such as
`version` in an identity binding.

## Ideas retained from FlatBuffers

- Little-endian scalar encoding
- Explicit stable field identifiers
- Forward-compatible unknown-field skipping where allowed
- Offset-based access where it avoids copying
- Zero-copy bounded byte slices where ownership is safe
- Optional-field presence tracking
- Append-only schema evolution
- Decode access without mandatory object materialization

These are design techniques, not a compatibility promise.

## MeshLink-specific choices

The codec may use:

- A compact fixed frame header
- Explicit UByte enum codes instead of declaration ordinal
- Deterministic canonical encoding for signatures and digests
- BLE-sized bounded lengths and offsets
- Presence bitmaps instead of generic vtables
- Direct forward writing
- Specialized scoreboard and transfer-chunk layouts
- No generic reflection, unions, or strings unless a requirement needs them
- Typed failures for every malformed or oversized input
- Strict allocation, nesting, and collection limits

## Contract organization

`frames.yaml` defines frame codes, field IDs, widths, encryption layer, maximum
sizes, and evolution. `enums.yaml` defines storage width, explicit values,
reserved ranges, unknown-value handling, and signature participation.
`models.yaml` defines reusable encoded values.

The custom `ReadBuffer`, `WriteBuffer`, enum codecs, and message codecs are
hand-optimized implementations of those contracts. The YAML contracts are
reviewed source, not generated implementation output.

## Field type codes

`Field.type` uses explicit stable codes, never reflection or class names:

```text
0x00 EMPTY   0x01 UBYTE   0x02 USHORT  0x03 UINT
0x04 ULONG   0x05 BYTE    0x06 SHORT   0x07 INT
0x08 LONG    0x09 FLOAT   0x0A DOUBLE  0x0B BOOLEAN
0x0C BYTES   0x0D TEXT    0x0E ENUM    0x0F STRUCT
0x10 LIST
```

Contracts declare exact type/width. Narrowing, widening, unknown types, and
unbounded bytes/lists reject before allocation. TEXT is excluded from security
canonical fields unless explicitly allowed. STRUCT and LIST follow bounded
nested contracts.

## Canonical field records

Canonical signatures and route digests use contextual field records:

```text
Field {
    id: UShort
    type: UByte
    length: UShort
    value: Byte[length]
}
```

The contextual names avoid redundant `field` prefixes. Records sort by `id`,
use declared type/width, omit absent optional values, and contain no padding,
bearer framing, AEAD tag, or signature field. Canonical bytes are deterministic
across JVM, Android, and iOS even if optimized runtime layouts differ.

## Unknown fields and enum values

Unknown frame codes reject before payload allocation. Unknown required fields,
duplicate singleton fields, missing required fields, reserved values, invalid
lengths, and offset overflow reject before protocol-state mutation. Unknown
optional fields may be skipped only when their contract declares a bounded length
and skip behavior. Unknown enum values reject for security/control decisions;
only explicitly informational fields may preserve an internal unknown value.
Repeatable fields are allowed only when their frame contract marks them
repeatable.

## Version policy

V0.1 uses one explicit `version` domain for protocol behavior and codec
interpretation. Unsupported versions fail closed before payload decoding; no
automatic downgrade negotiation exists. Future compatibility windows require an
explicit version contract, Noise binding, security review, and cross-version
golden tests.

## Text policy

No v0.1 wire contract uses `TEXT`. Protocol labels use explicit enums; keys,
hashes, identifiers, and opaque application data use fixed bytes or bounded
`BYTES`. If text is added later, it will use validated UTF-8 byte lengths,
explicit field limits, no codec-side Unicode normalization, and canonical
signing over the original validated bytes. This avoids platform normalization,
allocation, and diagnostic-leak differences.

## Numeric representation

All multi-byte integers and IEEE-754 bit patterns use little-endian fixed-width
encoding. Booleans accept only `0x00` and `0x01`; enum values use explicit
contract codes; no varints, platform alignment, or implicit numeric conversion
exists in v0.1. Signed values preserve two's-complement bit patterns. Decoders
range-check before conversion to Kotlin values, producing identical byte vectors

### Layer separation: wire codec vs. model serialization

The little-endian encoding above applies to **frame envelope fields and all on-wire scalar fields** defined in `frames.yaml`. It is distinct from the **model-layer serialization** used by value classes for storage and cross-platform comparison (e.g., `PeerIdentity.toByteArray()`, `SeqNo.toByteArray()`), which use big-endian via `BigEndianConversions.kt`.

This separation is intentional:

- **Wire codec (this document)**: little-endian — matches the platform endianness of all target architectures (x86, ARMv7, ARM64, RISC-V) and avoids byte-swap overhead in hot paths.
- **Model serialization**: big-endian — provides human-readable hex output, deterministic ordering, and is not constrained by BLE transport performance.

When a model value is encoded into a wire frame (e.g., a 16-byte `PeerIdentity` inside a `MESH_ENVELOPE` payload), the `FrameWriter` reads the model bytes directly without byte-swapping; the field's `type` in `frames.yaml` determines its wire encoding, and fixed-width byte types (`Byte[N]`) inherit the model layer's byte order. Only explicitly typed scalar fields (`UShort`, `UInt`, `ULong`) undergo little-endian encoding.

## Nested data and allocation limits

V0.1 bounds decoder work at eight nested levels, 256 list elements, 64 fields
per record, 65,535 encoded bytes per frame, and 4 KiB before authentication.
Decoders validate counts, lengths, depth, and arithmetic before allocation;
attacker-controlled counts never directly create unbounded memory. Individual
contracts may be stricter. Violations fail closed before protocol-state
mutation.

## Compatibility

Once a wire shape ships, evolution is append-only with explicit IDs:

- Frame codes, field IDs, and enum codes are never reused.
- Existing field types, widths, and defaults never change.
- Optional fields may append under unused IDs.
- Required additions require a protocol version change.
- Removed fields become permanently reserved.
- Unknown optional fields skip only under bounded declared rules; unknown required
  fields reject.
- Incompatible versions use new protocol versions.
- Canonical signature/digest inclusion is specified for every extension.
- Every evolution adds old/new golden vectors and cross-version tests.

Existing field IDs are never reinterpreted.

Breaking changes require a major protocol/library version and migration plan.

## What proves it works

Codec verification has five layers:

1. Byte-exact golden vectors for every frame, enum, and canonical field layout.
2. Round-trip tests preserving valid values.
3. Malformed-input tests for truncation, lengths, duplicates, unknown required
   fields, reserved codes, overflow, invalid booleans/enums, and nesting limits.
4. Evolution tests for old readers/new optional fields and new readers/old frames.
5. Cross-platform byte equality on JVM, Android, iOS, and Native.

Property/fuzz tests additionally require no crashes, unbounded allocation, state
mutation before validation, or private-key/payload leakage.

- Round-trip and zero-copy access tests
- Truncated, oversized, invalid-offset, duplicate-field, and unknown-enum tests
- Cross-version old-reader/new-writer and new-reader/old-writer fixtures
- Deterministic signing/digest canonicalization tests
- Fuzz/property tests with bounded allocation assertions
- Encode/decode benchmarks against the project budget
- 100% line and branch coverage for the shipped codec

## Field identifiers

Each record allocates explicit IDs independently: `0x00`–`0x1F` are stable core
fields, `0x20`–`0x3F` are additive optional fields, and `0x40`–`0x7F` are
reserved. `0xFFFF` is invalid. Removed IDs remain permanently reserved; field
order never determines encoding; canonical records sort by ID; and a field's
type/width never changes under the same ID. Unknown optional IDs skip only with
bounded length; unknown required IDs reject.

## Allocation model

Validated bounded byte slices may be read without copying while ownership remains
inside a connection/codec context. Bytes crossing into public API or
application-owned storage are defensively copied. Writers may use caller-provided
or pooled buffers, but no allocation is based on unvalidated lengths. Reflection
and generic object-graph allocation are prohibited. Pre-auth and post-auth
allocation budgets remain separate; sensitive scratch buffers are cleared
best-effort after use. Benchmarks measure allocation count as well as throughput.

## Trade-off

MeshLink owns the format, parser, evolution tools, security review, and test
matrix. That maintenance cost is accepted in exchange for exact control over BLE
wire overhead, KMP portability, deterministic encoding, and runtime dependency
limits.

## When to revisit

Revisit only if another KMP format can satisfy the same wire size, deterministic
canonicalization, zero-copy access, malformed-input bounds, dependency policy,
and cross-platform performance without breaking deployed MeshLink frames.
