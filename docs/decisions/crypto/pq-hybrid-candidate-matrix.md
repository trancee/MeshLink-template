# PQ-Hybrid Candidate Matrix and Wire-Shape Feasibility

Compares PQ-hybrid candidate strategies for MeshLink key establishment, focusing on wire shape, platform constraints, and prototype selection.

## Candidate Set

### Conservative — X25519 + ML-KEM encapsulation

Keep the current classical handshake as the anchor. Add a PQ KEM contribution and derive final key material from the combined classical and PQ inputs.

### Aggressive — PQ-first handshake path with classical bind

Introduce a PQ-dominant message flow. Bind the classical contribution for compatibility and migration. Accept larger payloads and more negotiation complexity.

## Wire-Shape Options

| Option | Description | Best for | Risk |
|---|---|---|---|
| A. Inline hybrid payload | Embed PQ material directly in handshake control payloads | Simple sequencing | Highest frame expansion, BLE pressure |
| B. Staged extension frames | Keep baseline frame shape; exchange PQ in explicit extension frames | Parsing/fallback isolation | Extra messages |
| C. Negotiated compact profile + continuation chunks | Negotiate compact profile first; move larger PQ into bounded continuation chunks | BLE-pressure control | Highest state-machine complexity |

## Feasibility Constraints

Any shortlisted candidate should stay inside these bounds:

- Bounded handshake message-count growth over baseline
- Measurable and attributable per-frame expansion
- Deterministic timeout behavior under the current harness
- Machine-readable visibility into retry and fallback behavior

## Comparison Matrix

| Candidate | Strategy | Wire-shape fit | BLE risk | Provider risk | Implementation risk |
|---|---|---|---|---|---|
| `C1` | Conservative | A or B | Medium | Medium | Medium |
| `C2` | Conservative | B | Low-Medium | Medium | Medium |
| `A1` | Aggressive | A | High | High | High |
| `A2` | Aggressive | C | Medium-High | High | Very high |

## Recommended Shortlist

### Primary: `C2` — conservative + staged extension frames

Strongest observability and fallback separation; lower immediate BLE pressure than inline aggressive payloads; good realism without overloading the first spike.

### Secondary: `C1` — conservative + inline/staged contrast

Gives a useful comparison point for payload-size sensitivity; tests whether inline carriage is practical.

### Deferred: aggressive candidates

Require more provider maturity and state-machine work than the first spike should absorb.

## S03 Evidence Summary

| Candidate | Latency delta | Payload delta | Negotiated mode | Fallback | Downgrade | Failures |
|---|---:|---:|---|---|---|---:|
| `C2` (`c2_staged_extension`) | +46 ms | +184 bytes | hybrid | none | none | 0 |
| `C1` (`c1_inline_staged`) | +63 ms | +232 bytes | hybrid | none | none | 0 |

`C2` remains the stronger follow-up candidate; `C1` remains the comparison control.
