# Background operation

**Status:** Locked — 2026-07-31

> Normative platform behavior lives in [SPEC.md §§6 and
> 14](../../../SPEC.md#6-transport-layer). This record defines the best-effort
> guarantee, host/library ownership, restoration, and failure behavior.

## Guarantee

MeshLink uses platform-authorized background BLE facilities on a best-effort
basis. It guarantees state safety and deterministic recovery, not uninterrupted
execution or delivery latency while the OS suspends or terminates the process.

| Situation | Guarantee |
|-----------|-----------|
| Foreground | Full discovery, routing, transfer, and timing requirements |
| Background with live process | Continue within platform BLE/background limits |
| Suspended | OS may maintain work and later coalesce/deliver callbacks |
| OS-terminated with restoration | Restore platform managers and persisted identity/trust; traffic sessions/transfers do not survive |
| User force-stop/force-quit | No execution or relaunch guarantee |
| Device reboot | Restore identity/trust; create fresh BLE, Noise, routing, and hint state |
| Permission/Bluetooth revoked | Fail closed and stop affected radio work |

No API or documentation promises always-connected or guaranteed background
delivery.

## Configuration

Background integration is an explicit immutable setting:

```kotlin
meshLinkSettings {
    enableBackground = true
}
```

It defaults to `false`. When true, `MeshLink.start()` validates platform
prerequisites and fails with a typed configuration/permission error when they
are absent. Changing the value requires stopping the instance and constructing a
new one.

## Android ownership

The host application owns:

- the `connectedDevice` foreground service;
- persistent notification/channel and user-visible stop action;
- runtime permission UX;
- user opt-in; and
- service restart policy.

MeshLink owns BLE scanning, advertising, GATT/L2CAP, PendingIntent scan helpers,
radio leasing, state reconstruction, and version-specific behavior. It never
starts a foreground service or chooses notification text behind the host's back.

Integration covers API 26–30 legacy Bluetooth/location permissions, API 31+
Nearby Devices permissions, foreground-service start restrictions, Doze/screen
off, scan-start rate limits, PendingIntent scans, connected-device service type,
and Android 17 behavior. RPA/MAC/TransportHandle changes never become identity
changes.

## iOS ownership

The host application owns:

- `NSBluetoothAlwaysUsageDescription`;
- `bluetooth-central` and `bluetooth-peripheral` background modes;
- user-facing opt-in/explanation; and
- application-launch restoration forwarding.

MeshLink owns `CBCentralManager`/`CBPeripheralManager`, stable restoration
identifiers, restored objects/services/subscriptions, callback serialization,
and fresh authentication/routing reconstruction.

Background scans filter on the fixed MESH UUID. Dynamic peerHint metadata may be
absent in the overflow area; GATT resolution and Noise remain authoritative.
Discovery may be delayed/coalesced and advertising frequency reduced. User
force-quit prevents reliable relaunch until the app is opened.

## Restoration and ephemeral state

Persisted:

- local PeerIdentity and long-term keys;
- trust records and rotation proof chains;
- routing and transfer identifier high-water marks; and
- storage schema/version data.

Not persisted:

- peerHint and TransportHandle;
- BLE connection attempts;
- Noise traffic keys and pending epochs;
- routes;
- active messages/transfers and scoreboards; or
- diagnostics.

After process restoration, old transfer handles cannot report a new outcome to
the dead process. New application state begins with no active transfers. Trust
continuity permits fresh IK; it does not restore traffic sessions.

## Lifecycle interaction

- `RUNNING` with enableBackground enabled requests background continuation.
- `PAUSED` intentionally suspends new discovery/admission even if background
  facilities exist, while retaining the environment radio lease.
- `STOPPED` releases radio resources and unregisters process-local background
  work.
- Restoration callbacks are serialized before any new scan, advertisement, or
  connection starts.
- Permission loss, Bluetooth disablement, or invalid restoration state fails
  closed at the smallest safe scope and emits a typed redacted diagnostic.

## Peer-hint and renewal timing

MeshLink does not wake a suspended app solely to rotate peerHint or renew Noise.
On resumed permitted activity:

- overdue peerHint rotates before advertising resumes where possible;
- unexpired sessions renew before admitting new application traffic when their
  renewal window has begun; and
- expired sessions discard old keys and require fresh IK.

No background delay extends key expiry or changes public PeerIdentity.

## Testing

The proof matrix covers foreground/background/suspended/locked/process-killed/
force-stopped states on Android and iOS, including:

- missing and revoked permissions;
- Bluetooth disabled/resetting;
- service/plist misconfiguration;
- PendingIntent and Core Bluetooth restoration;
- duplicate restored callbacks;
- RPA/TransportHandle and peerHint changes;
- L2CAP drop and GATT fallback;
- process death during discovery, handshake, renewal, key rotation, routing, and
  transfer; and
- clean reconstruction without stale traffic keys or active transfers.

## Related

- [Peer hints and identity races](../discovery/peer-hint-and-identity-races.md)
- [Connectable advertisement](../discovery/connectable-advertisement.md)
- [Noise session renewal](../crypto/noise-session-renewal.md)
- [Transport bearer and MTU](mtu-negotiation.md)
- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
