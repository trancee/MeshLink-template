package ch.trancee.meshlink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MeshLinkVersionTest {
    @Test
    fun `version is set`() {
        assertEquals(MeshLinkVersion(0, 1, 0), MeshLink.VERSION)
    }

    @Test
    fun `version comparison works`() {
        assertEquals(0, MeshLinkVersion(1, 0, 0).compareTo(MeshLinkVersion(1, 0, 0)))
        assertTrue(MeshLinkVersion(1, 0, 0) > MeshLinkVersion(0, 9, 9))
        assertTrue(MeshLinkVersion(0, 1, 0) > MeshLinkVersion(0, 0, 99))
        assertTrue(MeshLinkVersion(0, 0, 1) > MeshLinkVersion(0, 0, 0))
    }

    @Test
    fun `version parse works`() {
        assertEquals(MeshLinkVersion(1, 2, 3), MeshLinkVersion.parse("1.2.3"))
        assertEquals(MeshLinkVersion(0, 0, 0), MeshLinkVersion.parse("0.0.0"))
        assertEquals(MeshLinkVersion(10, 20, 30), MeshLinkVersion.parse("10.20.30"))
    }

    @Test
    fun `version parse throws on malformed input`() {
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("not-a-version") }
    }

    @Test
    fun `version toString works`() {
        assertEquals("1.2.3", MeshLinkVersion(1, 2, 3).toString())
        assertEquals("0.0.0", MeshLinkVersion(0, 0, 0).toString())
        assertEquals("10.20.30", MeshLinkVersion(10, 20, 30).toString())
    }

    @Test
    fun `version parse throws on wrong number of parts`() {
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("1.2") }
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("1.2.3.4") }
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("") }
    }

    @Test
    fun `version parse throws on invalid major`() {
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("x.2.3") }
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("  .2.3") }
    }

    @Test
    fun `version parse throws on invalid minor`() {
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("1.y.3") }
    }

    @Test
    fun `version parse throws on invalid patch`() {
        assertFailsWith<IllegalArgumentException> { MeshLinkVersion.parse("1.2.z") }
    }
}
