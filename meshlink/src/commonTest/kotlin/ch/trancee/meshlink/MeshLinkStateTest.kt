package ch.trancee.meshlink

import ch.trancee.meshlink.model.MeshLinkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MeshLinkStateTest {
    @Test
    fun `all entries have non-null name`() {
        MeshLinkState.entries.forEach { assertNotNull(it.name) }
    }

    @Test
    fun `has five lifecycle states`() {
        assertEquals(5, MeshLinkState.entries.size)
    }

    @Test
    fun `states have expected names`() {
        assertEquals(
            listOf("UNINITIALIZED", "CONFIGURED", "RUNNING", "PAUSED", "STOPPED"),
            MeshLinkState.entries.map { it.name },
        )
    }
}
