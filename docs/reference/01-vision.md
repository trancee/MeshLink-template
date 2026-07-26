# Vision & Product Pillars

> Source: [SPEC.md §1](../../SPEC.md#1-vision--product-pillars)

## 1.1 Problem Statement

- Mobile devices need to communicate securely without internet, backend servers, or user accounts
- BLE mesh networking requires handling peer discovery, trust establishment, routing, and reliable transfer
- Both Android and iOS platforms must offer identical public API behavior

## 1.2 Product Pillars

1. **Zero-infrastructure trust** — Trust On First Use (TOFU) model; first mutually-authenticated handshake pins peer identity keys; subsequent mismatches require explicit reset/revocation
2. **Two-layer encryption** — Hop-by-hop link encryption (relays can forward without reading) layered under end-to-end encryption (origin/destination only)
3. **Proactive multi-hop routing** — Distance-vector-style routing control plane maintaining live route tables; host app never selects intermediate hops manually
4. **Reliable large-payload transfer** — Chunked transfer with selective acknowledgment (SACK), retransmission, and reassembly over small-frame BLE radio
5. **Power-aware operation** — Discrete power tiers governing scan duty cycle, advertisement interval, connection interval, concurrent connections, and transfer chunk size
6. **Deterministic cross-platform parity** — Identical lifecycle states, sealed error hierarchies, and diagnostic codes across Android and iOS

## 1.3 Non-Functional Requirements

| Requirement | Constraint |
|-------------|------------|
| Offline operation | Zero connectivity required once permissions granted |
| Persisted state | Only trust pin (identity material + first/verified instants); no plaintext or full identifiers cached |
| Pending state | In-memory only; does not survive process restart |
| Delivery outcomes | Explicit: success, in-progress, retrying, route-waiting, unreachable, trust-failure, timeout, unrecoverable-failure (maps from `TransferState`: COMPLETED→success, IN_PROGRESS→in-progress, RETRYING→retrying, WAITING_FOR_ROUTE→route-waiting, TIMED_OUT→timeout, FAILED→unrecoverable-failure or trust-failure; `unreachable` is a routing-layer outcome, not a transfer state) |
| Wire compatibility | Backward-compatible evolution; breaking changes require major version bump + migration |
| Performance budgets | See [Build & Quality Constraints](12-build-quality.md) |
| Runtime dependency | Maximum one Maven artifact at runtime: `kotlinx-coroutines-core`. Crypto primitives are either platform APIs (Android Security Framework, iOS Security framework) or pure-Kotlin fallbacks — this is an implementation distinction, not a runtime dependency. |
| Test coverage | 100% line/branch coverage for `:meshlink`; `commonMain` + `androidHostTest` + `iosMain`; crypto validated against Wycheproof vectors |
