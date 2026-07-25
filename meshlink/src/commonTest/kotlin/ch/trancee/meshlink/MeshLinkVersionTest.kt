package ch.trancee.meshlink

import kotlin.test.Test
import kotlin.test.assertEquals

class MeshLinkVersionTest {
    @Test
    fun `version is set`() {
        assertEquals("0.0.0", MeshLink.VERSION)
    }
}
