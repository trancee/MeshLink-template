---
name: babel-rfc8966
description: "Babel (RFC 8966) routing protocol reference. Covers loop-avoiding distance-vector, feasibility condition, sequenced routes, seqno requests for starvation recovery, wire format (UDP 6696, TLV types), and cost algorithms (ETX, hysteresis). Use when implementing Babel routing, debugging route convergence, or working with TLV encoding."
---

# Babel (RFC 8966) Routing Protocol

Loop-avoiding distance-vector protocol for mesh networks. IETF Standards Track (Jan 2021).

## When to Use

- Implementing Babel routing in MeshLink
- Debugging route convergence
- Working with seqno requests
- Designing BLE mesh protocols

## Essential Principles

- **Feasibility condition**: An update (s', m') is feasible when `s' > FD.seqno` OR `(s' == FD.seqno AND m' < FD.metric)` where FD is the feasibility distance (minimum distance ever advertised)
- **Sequenced routes**: 16-bit seqno incremented only by source; send seqno request for starvation recovery
- **Never spontaneous increment**: Increasing seqnos makes other nodes less likely to have feasible alternates
- **Per-source feasibility**: Routes distinguished by (prefix, plen, router-id), not just prefix
- **Strict metric monotonicity**: M(c, m) > m; recommended: M(c, m) = c + m
- **Wire format**: UDP port 6696, Magic=42, Version=2, 11 TLV types

## References

| Topic | Reference |
|-------|-----------|
| Bellman-Ford and feasibility (D(A), NH(A), FD) | `references/concepts-and-feasibility.md` |
| Seqno arithmetic, starvation recovery, overlapping prefixes | `references/concepts-and-feasibility.md` |
| Hello/IHU neighbour acquisition, cost computation | `references/protocol-operation.md` |
| Route selection, triggered updates, split horizon | `references/protocol-operation.md` |
| Wire format (packet, AE encodings, TLV types) | `references/wire-format.md` |
| ETX cost, hysteresis, recommended timers | `references/costs-parameters-security.md` |

## MeshLink Deviations

See `docs/decisions/routing/destination-sourced-seqno-and-ihu-digest-research.md` for documented deviations:

- Reconnect-driven seqno bump (not on-demand request)
- No hop-count field in SeqNoRequest wire frame
- RouteDigest (table hash) has no RFC precedent — MeshLink-original design

<!-- story: simplified for AI consumption -->