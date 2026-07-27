package ch.trancee.meshlink.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Routing policy constants for message TTL and hop behavior. */
public object RoutingPolicy {
    /** Maximum hop count a message can traverse before being dropped. */
    public const val MAX_HOPS: Int = 32

    /**
     * TTL derived from message priority — governs how long a message stays routable.
     *
     * SPEC-ANCHOR: ttl-by-priority
     */
    public fun ttlFor(priority: Priority): Duration =
        when (priority) {
            Priority.HIGH -> 10.minutes
            Priority.NORMAL -> 5.minutes
            Priority.LOW -> 1.minutes
        }
}
