# CBOR tag definitions (RFC 8949 §3.4)

A tag (major type 6) is an integer tag number followed by exactly one nested data item, the **tag content**. Tags are interpreted at the generic data-model level, not at (de)serialization time — a decoder that doesn't recognize a tag number may still pass through the tag number and content unchanged.

## Tag 0 — Standard date/time string

Tag content: a text string in RFC 3339 `date-time` format. A different content type, or a string that doesn't match the format, is invalid.

## Tag 1 — Epoch-based date/time

Tag content: an unsigned/negative integer or a float, counting seconds from `1970-01-01T00:00Z`. Non-negative values follow POSIX time. Fractional seconds require a float (binary64 for useful precision). Other content types are invalid.

## Tags 2 / 3 — Bignums

Tag content: a byte string, interpreted as an unsigned integer `n` in network byte order. Tag 2's value is `n`; tag 3's value is `-1 - n`. Contained items of other types are invalid.

- Preferred serialization drops leading zero bytes (so `n = 0` is the empty byte string), and never uses a bignum tag for a value that fits in major type 0/1 — encode as a plain integer instead.
- Decoders must still accept bignums with leading zeroes.

Example: `2^64` → `C2 49 010000000000000000` (tag 2, byte string len 9, value bytes).

## Tags 4 / 5 — Decimal fractions and bigfloats

Tag content: a 2-element array `[e, m]` — exponent then mantissa. Contained items with any other structure are invalid.

- Tag 4 (decimal fraction): value = `m * 10^e`.
- Tag 5 (bigfloat): value = `m * 2^e`.
- `e` must be major type 0 or 1 (a plain integer); `m` may itself be a bignum (tags 2/3).
- Neither form can represent Infinity, -Infinity, or NaN — use a binary16 float instead if those are needed.

Example: `273.15` → `C4 82 21 19 6ab3` (tag 4, array len 2, exponent −2, mantissa 27315).
Example: `1.5` → `C5 82 20 03` (tag 5, array len 2, exponent −1, mantissa 3).

## Content-hint tags (§3.4.5)

| Tag | Content | Meaning |
|---|---|---|
| 21 | any | expected later conversion to base64url |
| 22 | any | expected later conversion to base64 |
| 23 | any | expected later conversion to base16 |
| 24 | byte string | the bytes are themselves an encoded CBOR data item |
| 32 | text string | URI (RFC 3986) |
| 33 | text string | base64url-encoded data |
| 34 | text string | base64-encoded data |
| 36 | text string | MIME message (RFC 2045) |

Tags 33/34 carry base-encoded *text*; this differs from tags 21/22, which are hints that arbitrary content should later be base-encoded when converted to a text-only format such as JSON.

## Tag 55799 — Self-described CBOR

Content: any data item, unchanged in meaning. Its only purpose is a distinguishing byte-sequence marker (head `0xD9D9F7`) so a sniffer can recognize a stream as CBOR — e.g., to distinguish it from JSON — before a full decode. It imparts no semantics of its own.

## Reserved all-ones tags

Tag numbers `65535`, `4294967295`, and `18446744073709551615` (all-ones in 16/32/64 bits) are reserved by IANA as sentinel values for implementations that want a single integer to mean "no tag" or "a specific tag." They are not meant to appear in real CBOR data; treat their appearance as suspect.

## Extending with new tags

Unknown tag numbers are always well-formed (decoders may treat unrecognized tag content by generic data-model rules) but their *validity* depends on the tag's own specification. When implementing a decoder, decide once whether unknown tags are passed through as `(tag_number, content)` pairs or rejected — CBOR does not mandate either policy.
