# MeshLink — Greenfield Project Summary

**Vision:** A library-first SDK enabling encrypted, serverless, fully offline peer-to-peer messaging between mobile devices over a short-range radio mesh — no internet, no backend, no user accounts. Two independent mobile platforms must be fully interoperable and behaviorally identical from a developer's perspective.

## Product Pillars

1. **Zero-infrastructure trust** — Peers establish trust via *Trust On First Use (TOFU)*: first successful mutually-authenticated handshake pins the peer's identity keys locally; any later identity mismatch is treated as untrusted until explicitly reset/revoked (no silent re-trust).
2. **Two-layer encryption** — Hop-by-hop link encryption between adjacent mesh nodes (so relays can forward traffic without reading it) layered under end-to-end encryption visible only to origin and final destination. This maps naturally onto a modern Noise-style handshake pattern (mutual, forward-secret, static-key-based) — see the **Noise Protocol Framework** concept, and draws on primitives standardized in:
   - **RFC 7748** (X25519/X448 elliptic-curve Diffie-Hellman)
   - **RFC 8032** (EdDSA/Ed25519 signatures)
   - **RFC 7539 / RFC 8439** (ChaCha20-Poly1305 AEAD)
   - **RFC 5869** (HKDF key derivation)
   - **RFC 2104** (HMAC)
   - **RFC 6234** (SHA-2 family)
   - **RFC 9147** (DTLS 1.3, useful reference for replay-window/anti-replay design)
   - **RFC 9420 (MLS)** and **RFC 7435** (opportunistic security) as reference points for group-security and best-effort-encryption design philosophy.
3. **Proactive multi-hop routing** — A distance-vector-style routing control plane maintains live route tables so the host application never selects intermediate hops manually. This is conceptually aligned with loop-free, feasibility-condition distance-vector protocols — most directly **RFC 8966 (the Babel Routing Protocol)** — with adjacent-family reference material worth studying: RIP (RFC 1058/2453), triggered-RIP (RFC 1075), OSPF (RFC 2328), AODV (RFC 3561), OLSR (RFC 3626), BGP (RFC 4271), RPL (RFC 6550/6997), and delay/disruption-tolerant networking concepts (RFC 4838 DTN architecture, RFC 5050/9171 Bundle Protocol) for intermittent-connectivity design ideas.
4. **Reliable large-payload transfer over a small-frame radio** — Payloads larger than a single link-layer frame are chunked, selectively acknowledged, retransmitted, and reassembled, with a scoreboard-style missing-range tracker so partial acknowledgement never forces re-sending already-received data. Reference standards: **RFC 2018** (TCP selective acknowledgment options) and **RFC 7233** (HTTP range requests) as prior art for partial/resumable transfer semantics.
5. **Power-aware operation** — A small set of discrete power tiers governs scan duty cycle, advertisement interval, connection interval, concurrent-connection budget, and transfer chunk size, with explicit, quantified behavior per tier (observable to the host app) rather than an opaque "battery saver" black box.
6. **Deterministic cross-platform parity** — Both mobile platforms must expose identical public capabilities: same lifecycle states, same sealed error/exception category hierarchy, and one shared, fixed-size diagnostic code catalog with consistent severity tiers and payload shapes. Shared business logic (trust, routing, transfer, security, diagnostics) lives in one common core; only radio/platform glue is platform-specific.

## Wire & Discovery Design

- A fixed, minimal discovery advertisement (short-range broadcast radio, single-packet, no follow-up query) carries: protocol version, platform, current power tier, an application/mesh isolation hash, L2CAP PSM for direct connection, and a truncated public-key hash used as a discovery hint (not the canonical trust identity).
- A reserved address/UUID space is set aside up front for experimental or fallback transports, explicitly marked non-normative until formally promoted.
- Routing/control-plane messages use a compact, versioned binary envelope family (e.g. neighbor-hello, "I heard you" liveness, route update, route retraction, sequence-number request) — a self-describing binary schema (consider **RFC 8949**, CBOR, as one deterministic-binary-encoding reference) is a reasonable encoding target, with strict backward-compatibility rules once any wire shape ships. **Decided:** the actual wire codec is a FlatBuffers-compatible format implemented in pure Kotlin, not CBOR — see [Why pure-Kotlin FlatBuffers](docs/explanation/why-pure-kotlin-flatbuffers.md) for the rationale. **Decided:** the `Hello`/`"I heard you"` (IHU) liveness frames listed above are removed, not implemented — route freshness is destination-self-reported seqno instead; see [Destination-sourced route freshness, IHU cost signal removal, and digest-triggered resync](docs/decisions/routing/destination-sourced-seqno-ihu-removal-digest-resync-design.md) for the rationale. **Decided:** GATT is the always-available bearer carrying all control-plane traffic, and L2CAP CoC (via the PSM already listed above) is the preferred bearer for data-plane traffic only, falling back to GATT when CoC is unavailable or fails; see [GATT as the always-available control plane, L2CAP CoC as the preferred data plane](docs/decisions/transport/gatt-l2cap-transport-selection.md) for the rationale. This section otherwise reflects the original suggested design space, not a binding decision.
- Optional payload compression is a reasonable, well-precedented add-on — see **RFC 1950/1951/1952** (zlib/deflate/gzip) and modern alternatives **RFC 7932** (Brotli) and **RFC 8878** (Zstandard).

## Non-Functional Requirements Worth Preserving

- Fully offline operation for every core capability once permissions are granted.
- Minimal persisted state: only what's required to re-verify pinned trust (identity material + first-seen/last-verified timestamps) — no diagnostics, no plaintext, no full identifiers persisted by the SDK itself.
- In-memory-only pending-retry/transfer state that does not survive a process restart; bounded, jittered exponential backoff while no valid route exists, governed by an explicit delivery deadline.
- Explicit, enumerated delivery/diagnostic outcomes (success, in-progress, retrying, unreachable, trust-failure, timeout, unrecoverable-failure) rather than ambiguous silent failure.
- Backward-compatible wire evolution: any breaking wire change requires an explicit version bump, migration path, and compatibility-fixture validation.
- Quantified, benchmark-enforced performance budgets covering throughput, p95 latency, steady-state memory, cold-start time, routing-convergence time, and codec speed — regressions beyond a fixed threshold from the last recorded baseline block release.
- A minimal, explicitly justified runtime dependency footprint for the shipped library artifact; anything beyond a tightly scoped baseline requires formal governance sign-off.
- 100% coverage expectation for security/routing/transfer/lifecycle logic, with cryptographic primitives validated against a standard test-vector corpus (e.g. Wycheproof-style vectors) and multi-node scenarios exercised through a canonical virtual/simulated network harness rather than physical devices only.

## Suggested Rebuild Approach

Start from the **specification and data-model layer first** (peer identity, trust record, route entry, message envelope, transfer session, diagnostic event, power policy) as the stable contract, then layer in: (1) discovery/advertisement contract, (2) hop-by-hop + end-to-end security contract, (3) routing control-plane contract, (4) chunked-transfer contract, (5) power-policy contract, (6) the shared public developer-facing API/lifecycle surface — validating each layer against its own RFC-grounded reference algorithm before wiring platform-specific radio glue underneath.
