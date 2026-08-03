package ch.trancee.meshlink.benchmark

import ch.trancee.meshlink.MeshLink
import ch.trancee.meshlink.MeshLinkVersion

/**
 * Placeholder JVM smoke-benchmark entry point. Replaced once real kotlinx-benchmark scenarios are
 * written.
 */
public object MeshLinkBenchmark {
    public fun libraryVersionUnderTest(): MeshLinkVersion = MeshLink.VERSION
}
