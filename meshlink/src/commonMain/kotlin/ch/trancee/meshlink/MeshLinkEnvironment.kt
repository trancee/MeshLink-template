package ch.trancee.meshlink

import kotlin.time.Duration
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

    /** Returns dispatcher for radio operations. */
    public val radioDispatcher: CoroutineDispatcher

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
