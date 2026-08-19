package ch.trancee.meshlink.benchmark

import ch.trancee.meshlink.MeshLink
import ch.trancee.meshlink.MeshLinkVersion
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup

/**
 * Smoke benchmark verifying that MeshLink library version access is stable
 * under repeated invocation. Once the wire codec and routing layers are
 * implemented, real benchmarks (frame encoding, Noise handshake, routing
 * convergence) will live in this source set.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class LibraryVersionBenchmark {

    @Benchmark
    fun libraryVersionAccess(): MeshLinkVersion = MeshLink.VERSION
}
