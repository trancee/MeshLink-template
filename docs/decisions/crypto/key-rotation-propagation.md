# Key Rotation Propagation Deadline

**Status:** Locked — 2025-07-28

## Context

The crypto-design ADR specifies key rotation propagation deadlines:

- Direct neighbors: < 1 second
- 2-hop: < 3 seconds (route convergence budget)
- Beyond 2-hop: handled by digest resync

This ADR specifies the implementation mechanism for tracking and enforcing these deadlines.

## Decision

**Track propagation deadlines in `KeyRotationManager` with a per-neighbor deadline timer that emits a diagnostic event on expiry.**

### Design

```kotlin
// meshlink/src/commonMain/kotlin/ch/trancee/meshlink/trust/KeyRotationManager.kt

class KeyRotationManager(
    private val trustStore: TrustStore,
    private val routingTable: RoutingTable,
    private val transport: TransportLayer,
    private val diagnostics: DiagnosticEmitter,
) {
    
    // Per-peer deadline tracking
    private val propagationDeadlines = mutableMapOf<PeerIdentity, Job>()
    private val directNeighborDeadlineMs = 1000L
    private val twoHopDeadlineMs = 3000L
    
    /**
     * Called when local key rotation is initiated.
     * Starts deadline timers for all direct neighbors.
     */
    fun onLocalKeyRotation(announcement: KeyRotationAnnouncement) {
        val directNeighbors = routingTable.getDirectNeighbors()
        
        for (neighbor in directNeighbors) {
            val job = CoroutineScope(SupervisorJob()).launch {
                delay(directNeighborDeadlineMs)
                onPropagationDeadlineMissed(neighbor, announcement, directNeighborDeadlineMs)
            }
            propagationDeadlines[neighbor] = job
        }
        
        // Broadcast announcement to direct neighbors immediately
        broadcastToDirectNeighbors(announcement)
    }
    
    /**
     * Called when receiving a key rotation announcement from a peer.
     * If we are a direct neighbor, forward to our other neighbors (2-hop).
     * If 2-hop, forward to our neighbors (3-hop, handled by digest resync).
     */
    fun onReceivedKeyRotation(
        fromPeer: PeerIdentity,
        announcement: KeyRotationAnnouncement,
        hopCount: Int = 1,
    ) {
        // Verify and adopt the new key
        val verified = trustStore.verifyAndAdoptRotation(fromPeer, announcement)
        
        if (!verified) {
            diagnostics.emit(KeyRotationEvent(
                peerIdentity = fromPeer,
                reason = announcement.reason,
                oldKeyVerified = false,
                sequenceNumberReset = false,
                propagationDeadlineMet = false,
            ))
            return
        }
        
        // Cancel our deadline timer for this peer (they received it)
        propagationDeadlines.remove(fromPeer)?.cancel()
        
        // Emit success diagnostic
        diagnostics.emit(KeyRotationEvent(
            peerIdentity = fromPeer,
            reason = announcement.reason,
            oldKeyVerified = true,
            sequenceNumberReset = true,
            propagationDeadlineMet = true,
        ))
        
        // Forward to next hop if within budget
        if (hopCount == 1) {
            val twoHopNeighbors = routingTable.getTwoHopNeighbors(fromPeer)
            for (neighbor in twoHopNeighbors) {
                val job = CoroutineScope(SupervisorJob()).launch {
                    delay(twoHopDeadlineMs - directNeighborDeadlineMs) // Remaining budget
                    onPropagationDeadlineMissed(neighbor, announcement, twoHopDeadlineMs)
                }
                propagationDeadlines[neighbor] = job
            }
            broadcastToTwoHopNeighbors(announcement, fromPeer)
        }
    }
    
    private fun onPropagationDeadlineMissed(
        peer: PeerIdentity,
        announcement: KeyRotationAnnouncement,
        deadlineMs: Long,
    ) {
        // Emit diagnostic with propagationDeadlineMet = false
        diagnostics.emit(KeyRotationEvent(
            peerIdentity = peer,
            reason = announcement.reason,
            oldKeyVerified = false,  // Unknown — deadline missed
            sequenceNumberReset = false,
            propagationDeadlineMet = false,
        ))
        
        // Trigger full table resync will handle it via RouteDigest mismatch
        // No active retry — we rely on periodic full sync
    }
    
    private fun broadcastToDirectNeighbors(announcement: KeyRotationAnnouncement) { ... }
    private fun broadcastToTwoHopNeighbors(announcement: KeyRotationAnnouncement, exclude: PeerIdentity) { ... }
}
```

### Diagnostic Event

Per `specs/diagnostic-events.yaml`:

```yaml
- name: KeyRotationEvent
  category: key_rotation
  description: "Key rotation announcement processed by a peer."
  fields:
    - name: peerIdentity
      type: PeerIdentity
    - name: oldKeyVerified
      type: Boolean
    - name: sequenceNumberReset
      type: Boolean
    - name: propagationDeadlineMet
      type: Boolean
      description: "True when the key rotation announcement reached all direct neighbors within the deadline"
    - name: reason
      type: KeyRotationReason
```

### Implementation Notes

1. **No active retry on deadline miss** — The design relies on:
   - Periodic full table sync (every 5 min per `RoutingSettings.fullTableSyncInterval`)
   - RouteDigest mismatch triggering full resync (per routing-design ADR §3)
   - This avoids amplifying traffic during churn

2. **Deadline budgets**:
   - Direct (1 hop): 1000ms — covers BLE connection + handshake + frame transmission
   - 2-hop: 3000ms — route convergence budget (matches SPEC.md §13.7)

3. **Security event rotation** (`SECURITY_EVENT` reason):
   - Same deadlines apply
   - `compromiseGracePeriod = ZERO` means old key rejected immediately
   - Propagation still must meet deadlines for mesh consistency

4. **Testing**:
   - Unit test: `KeyRotationManagerTest.propagationDeadlineMetWithinBudget`
   - Integration: Virtual mesh with simulated latency > deadline → verify diagnostic

## Related

- [Crypto Design ADR](crypto-design.md#4-key-rotation-protocol)
- [Routing Design ADR](../routing/routing-design.md#3-routedigest-on-mismatch-push-full-table)
- [Diagnostic Events Spec](../../../specs/diagnostic-events.yaml)
- [SPEC.md §13.7](../../../SPEC.md#13-testing--verification) — Routing convergence ≤3s
