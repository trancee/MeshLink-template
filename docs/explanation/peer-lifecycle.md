# The Peer Lifecycle Model

## The problem

In a BLE mesh, peers appear and disappear constantly. Phones move out of range,
operating systems suspend BLE work, and connections drop under interference. The
mesh has to smooth that churn into something a host app can reason about.

The naive approach is to forget a peer as soon as a BLE link drops. That causes:

- route flapping on momentary interference
- transfer sessions being abandoned unnecessarily
- noisy found/lost churn when a peer disappears briefly and returns

## The three-state model

```mermaid
stateDiagram-v2
    direction LR
    state "CONNECTED" as Connected
    state "DISCONNECTED" as Disconnected
    state "GONE" as Gone

    [*] --> Connected: peer discovered
    Connected --> Disconnected: link lost
    Disconnected --> Connected: BLE reconnects
    Disconnected --> Gone: grace period expires (15s–45s depending on power tier)
    Gone --> [*]

    note right of Disconnected
        Grace tracking keeps volatile
        peer, route, and transfer state
        alive briefly before cleanup.
    end note
```

### Connected

- Active BLE link
- Hello messages exchanged for neighbor discovery
- Transfers may be in progress
- Route entries remain live
- The host app sees `PeerEvent.Found(..., CONNECTED)` or
  `PeerEvent.StateChanged(..., CONNECTED)`

### Disconnected

- BLE link was lost
- A grace period is active because the peer may return quickly
- Routes can degrade before they are fully retracted
- Transfers can pause instead of being abandoned immediately
- The host app sees `PeerEvent.StateChanged(..., DISCONNECTED)` rather than an
  immediate `Lost`

### Gone

- The grace window expired without reconnection
- MeshLink cleans up ephemeral state such as presence, routes, and pending
  transfer work tied to the peer
- Pinned trust state remains, so a future reconnection with the same identity is
  still recognized
- `PeerEvent.Lost` is emitted to the host app

## How the grace period works

When a peer disconnects unexpectedly, MeshLink starts a fixed grace timer
whose duration depends on the power tier (HIGH=15s, MEDIUM=30s, LOW=45s).
If the peer reconnects before the timer expires, it moves back to CONNECTED.
If the timer fires without reconnection, the peer moves to GONE and
eviction of ephemeral state begins.

This keeps the public API unchanged. The explicit withdrawal remains private
control-plane behavior inside the runtime.

## MeshStateManager

`MeshStateManager` is the internal cleanup loop that drives this lifecycle.
Conceptually, it starts a fixed grace timer per disconnected peer and evicts
the peer when the timer fires without reconnection.

The important public point is that host apps do not need to implement their own
peer-loss timer just to smooth normal transport churn.

## Why "Gone" is not a public state

The public API exposes only:

```kotlin
enum class PeerConnectionState { CONNECTED, DISCONNECTED }
```

There is no public `GONE` value. When a peer reaches the internal gone state:

- MeshLink emits `PeerEvent.Lost`
- the peer is removed from active runtime state
- the app should treat it as unavailable until it is found again

Exposing `GONE` publicly would suggest there is still something actionable about
that peer. There is not. Once the peer is gone, the next meaningful public
signal is a new `Found`.

## The seqNo interaction

When a peer reconnects, the route has to come back with fresher routing state.
If the reconnect path reuses stale sequence information, differential routing
can suppress propagation to neighbors even though the route is valid again.

That is why reconnect logic needs a fresh seqno progression when it re-installs
reachability. Without it, the local node can look healed while neighboring
nodes keep stale route knowledge.

## Impact on the consuming app

From the app's perspective, the right model is still simple:

```kotlin
meshLink.peerEvents.collect { event ->
    when (event) {
        is PeerEvent.Found -> addPeerToUi(event.peerId)
        is PeerEvent.StateChanged -> updatePeerState(event.peerId, event.state)
        is PeerEvent.Lost -> removePeerFromUi(event.peerId)
    }
}
```

The app does not need to manage grace periods itself. MeshLink handles the
transport churn internally and surfaces cleaner `Found`, `StateChanged`, and
`Lost` transitions.
