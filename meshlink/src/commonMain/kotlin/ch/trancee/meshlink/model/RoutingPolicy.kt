package ch.trancee.meshlink.model

import kotlinx.datetime.Duration
import kotlinx.datetime.minutes

/** Routing policy constants for message TTL and hop behavior. */
object RoutingPolicy {
    /** Maximum hop count a message can traverse before being dropped. */
    const val MaxHops: Int = 32

    /** TTL derived from message priority — governs how long a message stays routable. */
    fun ttlFor(priority: Priority): Duration =
        when (priority) {
            Priority.HIGH -> Duration.minutes(10)
            Priority.NORMAL -> Duration.minutes(5)
            Priority.LOW -> Duration.minutes(1)
        }
}

/** Priority level that affects routing behavior and TTL. */
@Serializable
enum class Priority {
    HIGH,
    NORMAL,
    LOW,
}
