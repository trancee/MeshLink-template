# Future Work

> Source: [SPEC.md §15](../../SPEC.md#15-future-work)

- **PQ-hybrid key establishment**: Post-quantum readiness via conservative C2 candidate (see `docs/decisions/crypto/pq-hybrid-candidate-matrix.md`)
- **Noise IK for E2E layer**: Replace IX with IK for stronger mutual authentication at cost of one extra round trip
- **Throughput-based link metrics**: Replace RSSI proxy with actual measured throughput for path selection
- **Payload compression**: Optional Zstandard/Brotli for large transfers over small MTU
- **Adaptive grace periods**: Adjust based on peer stability and session uptime
- **Group messaging**: Extend Noise for multicast/broadcast (MLS-inspired, see RFC 9420)
