package ch.trancee.meshlink

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform capability supplied to [MeshLink] instance.
 *
 * Platform factories create this; shared callers never pass Android Context, Core Bluetooth
 * objects, TransportHandle, private keys, or provider objects through the protocol API. Internally
 * it owns BLE central/peripheral access, GATT/L2CAP, secure storage, crypto selection, secure
 * randomness, monotonic time, dispatchers, radio lease, and background restoration hooks.
 *
 * For the BCV baseline, a default JVM implementation is provided. Platform-specific implementations
 * will use expect/actual when platform code is implemented.
 *
 * SPEC-ANCHOR: meshlink-environment
 */
public interface MeshLinkEnvironment {
    /** Acquires the BLE radio lease for this environment. */
    public suspend fun acquireRadioLease(): RadioLease

    /** Releases the BLE radio lease. */
    public suspend fun releaseRadioLease(lease: RadioLease)

    /** Returns platform-specific secure storage. */
    public val secureStorage: SecureStorage

    /** Returns platform monotonic clock. */
    public val monotonicClock: MonotonicClock

    /** Returns dispatcher for BLE operations. */
    public val bleDispatcher: CoroutineDispatcher

    /** Returns dispatcher for compute-intensive operations. */
    public val computeDispatcher: CoroutineDispatcher
}

/** Radio lease that must be released when MeshLink stops. */
public open class RadioLease internal constructor()

/** Secure storage abstraction. */
public interface SecureStorage {
    public suspend fun put(key: String, value: ByteArray)

    public suspend fun get(key: String): ByteArray?

    public suspend fun delete(key: String)

    public suspend fun contains(key: String): Boolean
}

/** Monotonic clock abstraction. */
public interface MonotonicClock {
    public fun now(): Instant

    public fun elapsedSince(start: Instant): Duration
}

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

    public override val bleDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

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
