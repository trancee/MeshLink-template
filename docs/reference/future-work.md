# Future Work

> **Specification**: [SPEC.md §15](../../SPEC.md#15-future-work)  
> **Design rationale**: [PQ-Hybrid Candidate Matrix](../decisions/crypto/pq-hybrid-candidate-matrix.md)

## Planned Enhancements

### 1. PQ-Hybrid Key Establishment

**Candidate**: Conservative hybrid (C2) — classical X25519 + ML-KEM-768 with staged extension frames.

**Why not aggressive**: Requires provider maturity and state-machine work beyond first spike.

### 2. Throughput-Based Link Metrics

Replace RSSI proxy with measured throughput for routing decisions (post-connection refinement).

### 3. Payload Compression

Optional zlib/Brotli/Zstd for large payloads (RFC 1950/1951/1952, RFC 7932, RFC 8878).

### 4. Group Messaging

MLS (RFC 9420) integration for multi-recipient E2E encryption.

---

## Quick Links

- [SPEC.md §15 — Full future work spec](../../SPEC.md#15-future-work)
- [PQ-Hybrid Candidate Matrix ADR](../decisions/crypto/pq-hybrid-candidate-matrix.md)
