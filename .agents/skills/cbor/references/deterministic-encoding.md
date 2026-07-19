# Preferred and deterministic CBOR encoding (RFC 8949 §4)

## Preferred serialization

For any value, prefer the shortest well-formed encoding:

- Integers, string/array/map lengths, and tag numbers: use the smallest additional-information form that fits the argument (see the head table in SKILL.md) — never pad with a longer form.
- Floats: use the shortest of binary16 / binary32 / binary64 that exactly reproduces the value. E.g. `5.5` → `0xf94580` (binary16); `5555.5` → `0xfa45ad9c00` (binary32, since binary16 can't hold it exactly).
- For NaN, a shorter encoding is preferred whenever zero-padding its significand to the right reconstructs the original NaN bit pattern — `0xf97e00` is enough for most applications.
- Use definite length whenever the length is known before encoding starts; only use indefinite length for genuine streaming.

A "preferred encoder" that always follows these rules interoperates with constrained decoders that only implement small arguments. A generic decoder must still accept non-preferred (variant) encodings — full decoders are "variation-tolerant" by definition.

## Core deterministic encoding requirements (§4.2.1)

Used when a protocol needs byte-for-byte reproducible output (e.g., before hashing or signing). All of the following must hold:

1. **Preferred serialization everywhere** — shortest argument and shortest float for every item, applied recursively. Concretely:
   - `0..23` / `-1..-24` → same byte as the major type (no extra bytes).
   - `24..255` / `-25..-256` → one extra `uint8`.
   - `256..65535` / `-257..-65536` → one extra `uint16`.
   - `65536..4294967295` / `-65537..-4294967296` → one extra `uint32`.
   - Otherwise → `uint64`.
2. **No indefinite-length items** — re-encode every array, map, byte string, and text string with a definite length.
3. **Map keys sorted** by the bytewise lexicographic order of each key's own deterministic encoding (not by the key's logical value). Because CBOR items are self-delimiting, no encoded key is ever a prefix of another, so this order is always well-defined. Worked example of correctly sorted keys: `10` (`0x0a`) < `100` (`0x1864`) < `-1` (`0x20`) < `"z"` (`0x617a`) < `"aa"` (`0x626161`) < `[100]` (`0x811864`) < `[-1]` (`0x8120`) < `false` (`0xf4`).

## Extra considerations for tags and floats (§4.2.2)

- If a protocol lets a bare value and a tagged value mean the same thing (e.g., raw seconds vs. tag 1), the deterministic profile must pick exactly one form — never allow both.
- Decide once whether integers ≥ 2^64 in absolute value use bignum tags (2/3) while smaller integers stay as major type 0/1 (the preferred-serialization choice) or whether bignums are used uniformly.
- If integers and floats are considered interchangeable by the protocol's data model, pick one explicit rule for encoding integral values, e.g.:
  1. integral values that fit in 64 bits → major type 0/1, everything else → shortest float, or
  2. always use the shortest float that exactly represents the value, even for integral values, or
  3. always use 64-bit float.
  Rule 2 avoids straddling integer/float boundaries and still uses preferred float serialization; RFC 8949 suggests it's often the best default.
- Pick a single representation for NaN (typically `0xf97e00`) unless NaN payloads must round-trip.
- Decide whether negative zero is allowed or normalized to positive zero, and whether subnormal floats are flushed to zero.
- Different tags/forms (decimal fraction, bigfloat, bignum, plain integer) can represent the same numeric value; the protocol must state which one is canonical when more than one applies.

## Length-first map key ordering (§4.2.3, a.k.a. "Canonical CBOR" from RFC 7049)

Some older protocols instead sort map keys by encoded **length first**, then bytewise within equal lengths — the ordering RFC 7049 called "Canonical CBOR." Use this only when compatibility with that older definition is required; otherwise prefer the core (bytewise-only) ordering above, since RFC 8949 supersedes it.
