# L2CAP PSM Collision Handling (iOS)

**Status:** Locked — 2026-07-27

## Context

The PSM allocation ADR (`l2cap-psm-allocation.md`) specifies that iOS uses `publishL2CAPChannel(PSM:)` with a preferred PSM and retries on collision by incrementing the preferred PSM. However, it doesn't specify the maximum number of retries or the fallback behavior when the entire dynamic range is exhausted.

## Decision

### Collision Retry Strategy

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Max retries | **8** attempts (PSM 0x0080–0x0087) | Dynamic range has 128 values (0x0080–0x00FF); 8 attempts gives 6.25% of the range before bailing |
| Retry delay | **100ms** fixed | PSM collisions are rare; no need for exponential backoff |
| Exhaustion behavior | **Fall back to GATT-only** | If all 8 PSMs in the starting range collide, the peer advertised that it doesn't support CoC in its next advertisement, or the range is truly exhausted |

### Why Not Exhaust the Full Range (0x0080–0x00FF)?

Exhausting all 128 PSMs could take up to 12.8 seconds (8 attempts × 100ms × 16 ranges), which is unacceptable for connection setup. The 8-attempt limit provides a good balance between finding a PSM and connection latency.

### PSM Reuse Across Sessions

| Behavior | Policy |
|----------|--------|
| Same device, consecutive sessions | **Do NOT reuse PSM.** The OS may or may not assign the same PSM to a new `publishL2CAPChannel` call even with the same preferred PSM — this is platform behavior, not guaranteed |
| Different devices | N/A — each device manages its own PSM |
| After app reinstall | **New random PSM.** A reinstall generates a fresh PSM; the old one is invalidated |

### PSM in Discovery Advertisement

If L2CAP CoC is unavailable (all 8 PSM attempts failed, or the platform doesn't support CoC), the advertised PSM byte is set to **0x00** (no CoC support). The peer then falls back to GATT-only data transfer.

### Android PSM Collision

Android's `listenUsingL2capChannel()` assigns a PSM from the OS dynamic range. It throws `IOException` on failure. The code should retry up to **3 times** with a fresh `listenUsingL2capChannel()` call before falling back to GATT-only.

| Platform | Max Attempts | Behavior on Exhaustion |
|----------|-------------|----------------------|
| Android | 3 retries | Fall back to GATT-only |
| iOS | 8 retries (0x0080–0x0087) | Fall back to GATT-only |

## Diagnostics

```yaml
- name: PsmCollisionEvent
  fields:
    - attempts: Int          # How many collisions occurred
    - finalAction: String    # "assigned" | "gatt_fallback"
    - assignedPsM: Int?      # The PSM that was finally assigned (null if fallback)
```

## Related

- [L2CAP PSM Allocation ADR](l2cap-psm-allocation.md) — parent decision
- [GATT/L2CAP Transport Selection](gatt-l2cap-transport-selection.md)
- [Wire Frames](../../../specs/wire_frames.yaml) — discovery advertisement PSM field
