# Key Rotation Propagation Deadlines — Rationale

**Status:** Locked — 2026-07-31

> **Implementation details** (proof chain, timer logic, recovery, and diagnostics) live in [SPEC.md §5.4](../../../SPEC.md#54-key-rotation-protocol). This ADR captures the *why*.

---

## Decision

**Track propagation deadlines in `KeyRotationManager` with per-neighbor deadline timers that emit diagnostic events on expiry.**

**Deadlines:**

- Direct neighbors (1 hop): < 1 second
- 2-hop: < 3 seconds (route convergence budget)
- Beyond 2-hop: handled by RouteDigest resync

---

## Why Deadline Timers (Not Active Retry)?

| Alternative | Why Rejected |
|-------------|--------------|
| Active retry on timeout | Amplifies traffic during churn; mesh may be partitioned |
| No deadline tracking | No observability; silent failures |
| Full mesh broadcast on every rotation | O(n²) traffic; unnecessary for stable mesh |

**Design**: Broadcast the hop-encrypted, dual-signed proof, track the deadline,
and retain the proof in the installation-lifetime continuity chain. A missed
broadcast is recoverable through later synchronization or rotation-recovery XX.
Relies on:

- Periodic per-neighbor RouteDigest (`RoutingSettings.routeDigestInterval`)
- Digest mismatch → RouteSynchronization → RouteSnapshot

---

## Why These Deadline Values?

| Budget | Value | Derivation |
|--------|-------|------------|
| Direct (1 hop) | 1000ms | BLE connect (≤300ms) + Noise handshake (≤300ms) + frame tx (≤100ms) + margin |
| 2-hop | 3000ms | Route convergence budget (SPEC.md §13.7); 1s per hop + 1s margin |

---

## Security-Event Rotation Nuance

- Same deadlines apply
- `compromiseGracePeriod = ZERO` → old key rejected immediately
- Propagation still must meet deadlines for mesh consistency
- Diagnostic `propagationDeadlineMet` distinguishes "verified but late" from "never received"
- Routing SeqNo remains independent and never resets
- A forked generation fails closed rather than selecting one proof

---

## Observability

**Diagnostic event** (`KeyRotationEvent`) includes `propagationDeadlineMet: Boolean` — enables alerting on systemic propagation delays.

---

## Related

- [Crypto Design ADR](crypto-design.md#key-rotation-protocol)
- [Routing Design ADR](../routing/routing-design.md#routedigest-on-mismatch-push-full-table)
- [Peer Hint and Identity Races](../discovery/peer-hint-and-identity-races.md)
- [SPEC.md §5.4](../../../SPEC.md#54-key-rotation-protocol)
