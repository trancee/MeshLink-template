package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TransferOptionsTest {

    @Test
    fun `TransferOptions defaults and custom`() {
        // Arrange
        val defaults = TransferOptions.DEFAULT

        // Act + Assert — default values
        assertEquals(Priority.NORMAL, defaults.priority)
        assertNull(defaults.timeToLive)

        // Act — custom values
        val custom = TransferOptions(priority = Priority.HIGH, timeToLive = 5.minutes)

        // Assert — custom values, plus toString contains priority info
        assertEquals(Priority.HIGH, custom.priority)
        assertEquals(5.minutes, custom.timeToLive)
        assertTrue(custom.toString().contains("HIGH"))
    }

    @Test
    fun `TransferOptions with LOW priority and explicit TTL`() {
        // Arrange
        val options = TransferOptions(priority = Priority.LOW, timeToLive = 30.seconds)

        // Act + Assert
        assertEquals(Priority.LOW, options.priority)
        assertNotNull(options.timeToLive)
        assertEquals(30.seconds, options.timeToLive)
    }

    @Test
    fun `TransferOptions DEFAULT is a singleton`() {
        // Arrange + Act — three independent references must all be the same instance
        val a = TransferOptions.DEFAULT
        val b = TransferOptions.DEFAULT
        val c = TransferOptions.DEFAULT

        // Assert — equal, identical, and consistent hashCode
        assertEquals(a, b)
        assertEquals(a, c)
        assertTrue(a === b)
        assertTrue(b === c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `TransferOptions with all priority values`() {
        // Arrange + Act — construct with each Priority level
        val high = TransferOptions(priority = Priority.HIGH)
        val normal = TransferOptions(priority = Priority.NORMAL)
        val low = TransferOptions(priority = Priority.LOW)

        // Assert — each priority is preserved, all are distinct, and all have null TTL
        assertEquals(Priority.HIGH, high.priority)
        assertEquals(Priority.NORMAL, normal.priority)
        assertEquals(Priority.LOW, low.priority)
        assertNull(high.timeToLive)
        assertNull(normal.timeToLive)
        assertNull(low.timeToLive)
        assertNotEquals(high, normal)
        assertNotEquals(high, low)
        assertNotEquals(normal, low)
    }

    @Test
    fun `TransferOptions with explicit TTL differs from default`() {
        // Arrange + Act — explicit TTL on NORMAL priority (which would be null by default)
        val withTtl = TransferOptions(timeToLive = 1.minutes)
        val withoutTtl = TransferOptions()

        // Assert — explicit TTL is set, differs from default, and has different toString
        assertEquals(1.minutes, withTtl.timeToLive)
        assertNull(withoutTtl.timeToLive)
        assertNotEquals(withTtl, withoutTtl)
        assertNotEquals(withTtl.toString(), withoutTtl.toString())
    }
}
