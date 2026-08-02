# Payload identity and naming

**Status:** Locked — 2026-07-31

## Payload identity

Every finite payload is identified by:

```text
(origin, kind, id)
```

- `origin` is the PeerIdentity of the installation that created the payload.
- `kind` is MESSAGE or TRANSFER.
- `id` is the MessageId or TransferId interpreted by kind.

This identity survives relay forwarding, route change, hop reconnection,
bearer migration, retransmission, and key/session renewal. It is the sole key
for duplicate suppression, transfer state, delivery status, and receiver
completion tombstones.

## Public names

The enclosing type supplies the ID namespace:

```kotlin
class Message {
    val id: MessageId
    val origin: PeerIdentity
    val priority: Priority
    val completedAt: Instant
    val size: Int
    fun payload(): ByteArray
}

class MessageHandle {
    val id: MessageId
}

class TransferHandle {
    val id: TransferId
}
```

Wire payload frames carry `kind` and `id`; the authenticated origin comes from
the manifest/E2E context. Diagnostics may use explicit `messageId` or
`transferId` field names when no enclosing type supplies the namespace.

## Origin versus source

These terms are intentionally different:

```text
A → B → C

At C:
    origin = A              // created the payload
    source = B              // immediate authenticated frame sender
```

`completedAt` is the local instant complete authentication/reassembly made the
message available to the application; it is not sender-provided time.

`origin` is stable application identity. `source` is a local routing/transport
observation and may change when the route changes. Public `Message` uses
`origin`; it does not expose a relay as the message author.

Internal routing models may use `source` to track who supplied a route or frame.
A relay must never rewrite payload origin during forwarding.

## Security and validation

- Origin in a manifest is untrusted until E2E authentication validates it.
- A frame whose kind/id disagrees with its accepted manifest fails closed.
- A transfer ID or message ID alone is not authorization.
- Duplicate identity with identical content is idempotent.
- Duplicate identity with conflicting manifest/chunk content fails closed.
- Applications never manage relay IDs, transport handles, keys, or session IDs.

## Related

- [Payload transfer protocol](payload-transfer-protocol.md)
- [Transfer identifier](transfer-identifier.md)
- [Public API and lifecycle](../api/public-api-and-lifecycle.md)
- [Identity binding and fail-closed behavior](../crypto/identity-binding-and-fail-closed.md)
