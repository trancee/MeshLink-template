package ch.trancee.meshlink

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Default JVM implementation of [MeshLinkEnvironment] for testing and BCV baseline.
 * Platform-specific implementations (Android, iOS) will be provided via platform factories.
 */
public class JvmMeshLinkEnvironment : MeshLinkEnvironment {

    private var radioLease: JvmRadioLease? = null

    public override suspend fun acquireRadioLease(): RadioLease {
        require(radioLease == null) { "Radio lease already acquired" }
        radioLease = JvmRadioLease()
        return radioLease!!
    }

    public override suspend fun releaseRadioLease(lease: RadioLease) {
        require(lease === radioLease) { "Invalid radio lease" }
        radioLease = null
    }

    public override val secureStorage: SecureStorage = JvmSecureStorage()

    public override val monotonicClock: MonotonicClock = JvmMonotonicClock()

    public override val radioDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    public override val computeDispatcher: CoroutineDispatcher =
        kotlinx.coroutines.Dispatchers.Default
}

/** JVM radio lease implementation. */
public class JvmRadioLease internal constructor() : RadioLease()

/** JVM secure storage - in-memory map for testing. */
public class JvmSecureStorage : SecureStorage {
    private val storage = mutableMapOf<String, ByteArray>()

    public override suspend fun put(key: String, value: ByteArray) {
        storage[key] = value.copyOf()
    }

    public override suspend fun get(key: String): ByteArray? {
        return storage[key]?.copyOf()
    }

    public override suspend fun delete(key: String) {
        storage.remove(key)
    }

    public override suspend fun contains(key: String): Boolean {
        return storage.containsKey(key)
    }
}

/** JVM monotonic clock. */
public class JvmMonotonicClock : MonotonicClock {
    public override fun now(): Instant {
        return Clock.System.now()
    }

    public override fun elapsedSince(start: Instant): Duration {
        return (System.currentTimeMillis() - start.toEpochMilliseconds()).milliseconds
    }
}
