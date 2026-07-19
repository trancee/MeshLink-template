package ch.trancee.meshlink.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals

class MeshLinkBenchmarkTest {
    @Test
    fun `library version under test matches MeshLink`() {
        assertEquals("0.0.0", MeshLinkBenchmark.libraryVersionUnderTest())
    }
}
