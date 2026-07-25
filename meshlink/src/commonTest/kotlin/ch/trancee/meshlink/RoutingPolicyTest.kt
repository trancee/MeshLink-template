package ch.trancee.meshlink

import ch.trancee.meshlink.model.LinkMetric
import ch.trancee.meshlink.model.Priority
import ch.trancee.meshlink.model.RoutingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class RoutingPolicyTest {
    @Test
    fun `TTL values`() {
        assertEquals(10.minutes, RoutingPolicy.ttlFor(Priority.HIGH))
        assertEquals(5.minutes, RoutingPolicy.ttlFor(Priority.NORMAL))
        assertEquals(1.minutes, RoutingPolicy.ttlFor(Priority.LOW))
    }

    @Test
    fun `MaxHops is 32`() {
        assertEquals(32, RoutingPolicy.MaxHops)
    }

    @Test
    fun `LinkMetric composite combines rssi and flags`() {
        val metric =
            LinkMetric(
                rssiNormalized = 100u,
                supportsCoc = true,
                fastInterval = false,
                highPowerTier = true,
            )
        // flags bits 8-10 shl 8 = (256 | 0 | 1024) shl 8 = 327680, or rssiNormalized 100 = 327780
        assertEquals(327780u, metric.composite)
    }
}
