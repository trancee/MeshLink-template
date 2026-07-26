---
name: optimize-ble-throughput
description: Analyze BLE throughput from PHY to application framing, identify bottlenecks, and recommend platform-specific tuning. Separates PHY rate, Link Layer payload, ATT/L2CAP payload, and application throughput. Use for estimating ceilings, diagnosing bottlenecks, or designing bulk-transfer protocols.
---

# Optimize BLE Throughput

BLE throughput is limited at multiple layers. Analyze from PHY inward.

## Ceiling Estimation

Gather these inputs:
- Platform and role (iOS/Android/embedded, central/peripheral)
- PHY (1M or 2M)
- DLE (enabled or not)
- ATT MTU
- Connection interval
- Peripheral latency
- Packets per connection event
- Operation type (notification, indication, write without response, write with response)
- Direction (one-way or bidirectional)

### Key Formulas

```
# If you know ATT messages per event:
throughput_bytes_per_second ≈ att_messages_per_event * app_bytes_per_att_message / connection_interval_seconds

# If you know LL packets per event:
throughput_bytes_per_second ≈ ll_packets_per_event * useful_app_bytes_per_ll_packet / connection_interval_seconds
```

**Never mix ATT-message counts with LL-packet sizes in the same formula.**

### Payload Sweet Spots

| Scenario | Max App Bytes per Packet |
|----------|--------------------------|
| Default MTU 23 | 20 |
| DLE + MTU >= 247 | 244 (one full LL packet) |
| DLE + MTU >= 498 | 495 (two full LL packets) |

Crossing 244 or 495 by one byte forces another LL packet and reduces efficiency.

### Expected Throughput Ranges

- No DLE — ~54 kB/s (often capped)
- Well-tuned mobile link — 90-100 kB/s
- 2M PHY — ~77% faster than 1M (not 100%)
- Well-tuned embedded-to-embedded — up to ~178 kB/s

## Layer Analysis

### PHY Layer (Not App Throughput)

1M/2M PHY is irrelevant without considering:
- DLE negotiation
- ATT MTU alignment
- Connection interval
- Per-platform limits

### Link Layer

BLE is half-duplex. Every reverse-direction packet steals airtime from the forward direction.

### ATT/L2CAP Layer

Fragmentation happens transparently. A 1KB GATT write splits into multiple LL packets automatically.

### Application Layer

This is what you control. Design protocols around:
- **244-byte chunks** for most MTU configurations
- **495-byte chunks** when DLE+Mtu permits
- Acknowledgment strategy (per-chunk vs windowed)
- Retry behavior

## Workflow Selection

| Topic | File |
|-------|------|
| Estimate/calculate theoretical throughput | `workflows/estimate-throughput.md` |
| Maximize/improve existing transfer speed | `workflows/optimize-link.md` |
| Debug/why getting X kbps/low link | `workflows/audit-slow-link.md` |
| Design chunking/framing/ACK strategy | `workflows/design-transfer-protocol.md` |

## Diagnostic Questions

When debugging, ask:
1. What layer is limiting? (size vs interval vs reverse traffic)
2. What is the negotiated MTU? (often less than requested)
3. What PHY is active? (1M vs 2M availability varies)
4. What is the connection interval? (15ms is often the throughput sweet spot, not minimum)
5. How many packets per event? (controller-dependent)
6. What is peripheral latency? (affects pipeline depth)
7. Are other connections/advertising active? (steals airtime)

## Platform Notes

- **iOS**: 182-byte effective write limit on some versions; notifications may be throttled
- **Android**: DLE enabled by default on modern versions; check with `gatt.requestMtu()`
- **Embedded**: Can tune connection interval aggressively; verify peripheral supports it

## Verify

Ask for measured values (MTU, PHY, interval, packets/event) when diagnosing. Theory only gets you so far.

<!-- story: streamlined for AI consumption -->