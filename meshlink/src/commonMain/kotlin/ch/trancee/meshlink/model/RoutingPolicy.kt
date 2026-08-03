package ch.trancee.meshlink.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Routing policy constants for message TTL and hop behavior.
 *
 * Public so host apps can introspect routing limits (e.g. for diagnostics or UX explanations of
 * message lifetime). See SPEC.md §8 and docs/decisions/routing/routing-design.md.
 */
public object RoutingPolicy {
    /** Maximum hop count a message can traverse before being dropped. */
    public const val MAX_HOPS: Int = 16

    /**
     * TTL derived from message priority — governs how long a message stays routable.
     *
     * SPEC-ANCHOR: ttl-by-priority
     */
    public fun ttl(priority: Priority): Duration =
        when (priority) {
            Priority.HIGH -> 10.minutes
            Priority.NORMAL -> 5.minutes
            Priority.LOW -> 1.minutes
        }
}
