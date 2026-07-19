---
name: cbor
description: "CBOR (RFC 8949) binary format reference. Use when encoding or decoding CBOR byte sequences by hand, implementing a CBOR encoder/decoder, debugging or hex-dumping a CBOR data item, or working with CBOR tags, indefinite-length items, or canonical/deterministic CBOR."
---

# CBOR — Concise Binary Object Representation

Source: [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949.html) (obsoletes RFC 7049; same wire format). All multi-byte values are big-endian.

## Data model

A CBOR data item is one of: unsigned integer, negative integer, byte string, text string (UTF-8), array, map, tag (a tag number plus one nested data item), simple value, or floating-point number. Integers and floats are distinct types even when numerically equal.

## The head: major type + argument

Every data item starts with one **initial byte**: the high 3 bits are the **major type** (0–7), the low 5 bits are the **additional information**. Together with any bytes it pulls in, the initial byte plus those bytes form the item's **head**. The additional information decides how to read the **argument**:

| Additional info | Argument |
|---|---|
| 0–23 | the argument *is* this value |
| 24 / 25 / 26 / 27 | argument is the next 1 / 2 / 4 / 8 bytes (big-endian) |
| 28–30 | reserved — not well-formed |
| 31 | no argument; indefinite length (major types 2–5) or "break"/no data item (major type 7) |

## Major types

| Type | Meaning | Argument N means |
|---|---|---|
| 0 | unsigned integer | the value N |
| 1 | negative integer | the value −1−N |
| 2 | byte string | N bytes of content follow |
| 3 | text string (UTF‑8) | N bytes of content follow |
| 4 | array | N data items follow |
| 5 | map | N key/value **pairs** follow (2N items, key then value, alternating) |
| 6 | tag | argument is the tag number; exactly one data item (the tag content) follows |
| 7 | float / simple value / break | see below |

A map with an odd number of items, or a "break" outside an indefinite-length item, is not well-formed. Duplicate map keys are well-formed but invalid (Section 5.6 of the RFC).

## Indefinite lengths

Major types 2–5 may use additional-info 31 instead of a length:

- **Arrays/maps**: item stream follows until a **break** byte `0xFF` (major type 7, additional info 31) closes it. Nesting is fine — each `0xFF` closes exactly one open indefinite item.
- **Byte/text strings**: followed by zero or more *definite-length* chunks of the same major type, then `0xFF`. The value is the concatenation of the chunks. Nesting indefinite strings inside indefinite strings is not allowed.

Example: `[1, [2, 3], [4, 5]]` as definite-length is `83 01 82 02 03 82 04 05`; the same value with the outer array indefinite is `9F 01 82 02 03 82 04 05 FF`.

## Major type 7: floats and simple values

Additional info selects the sub-kind:

| Info | Meaning |
|---|---|
| 0–19 | simple value 0–19 (unassigned) |
| 20 / 21 / 22 / 23 | `false` / `true` / `null` / `undefined` |
| 24 | simple value 32–255 in the next byte (values 0–31 here are not well-formed) |
| 25 / 26 / 27 | IEEE 754 half / single / double precision float, in the following 2/4/8 bytes |
| 28–30 | reserved |
| 31 | "break" stop code (Indefinite lengths, above) |

## Tags (major type 6)

A tag wraps exactly one nested data item to add semantics; tags nest freely. Decoders may always fall back to exposing tag number + content unchanged. Common tag numbers:

| Tag | Content type | Meaning |
|---|---|---|
| 0 | text string | RFC 3339 date/time string |
| 1 | int or float | Unix epoch seconds |
| 2 / 3 | byte string | unsigned / negative bignum |
| 4 / 5 | array `[e, m]` | decimal fraction / bigfloat: `m * base^e` |
| 24 | byte string | embedded encoded CBOR data item |
| 32 / 33 / 34 | text string | URI / base64url / base64 |
| 55799 | any | self-described CBOR marker (head `0xD9D9F7`) |

For full definitions, equivalence rules, and encoding examples for these and other tags, see [references/tags.md](references/tags.md).

## Preferred and deterministic encoding

**Preferred serialization**: always use the shortest argument encoding and the shortest float width that exactly preserves the value; prefer definite length when the length is known up front. Most encoders should default to this.

If a protocol requires byte-for-byte reproducible ("canonical") output — e.g., for hashing or signing — apply the **core deterministic encoding requirements**: preferred serialization everywhere, no indefinite-length items, and map keys sorted by the bytewise order of their own deterministic encodings. See [references/deterministic-encoding.md](references/deterministic-encoding.md) before encoding anything that must be deterministic.

## Worked example

`0xa2016161026162` decodes as a map of 2 pairs:

```
a2         -- map, 2 pairs
   01      --   key 1: unsigned int 1
   61 61   --   value 1: text string len 1, "a"
   02      --   key 2: unsigned int 2
   61 62   --   value 2: text string len 1, "b"
```
→ `{1: "a", 2: "b"}`
