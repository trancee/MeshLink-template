package ch.trancee.meshlink.benchmark

import ch.trancee.meshlink.MeshLinkVersion
import kotlin.test.Test
import kotlin.test.assertEquals

class MeshLinkBenchmarkTest {
    @Test
    fun `library version under test matches MeshLink`() {
        assertEquals(MeshLinkVersion(0, 1, 0), MeshLinkBenchmark.libraryVersionUnderTest())
    }
}
