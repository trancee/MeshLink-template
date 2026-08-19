package ch.trancee.meshlink

import ch.trancee.meshlink.diagnostics.DiagnosticEvent
import ch.trancee.meshlink.model.ConfigurationException
import ch.trancee.meshlink.model.KnownPeer
import ch.trancee.meshlink.model.MeshLinkException
import ch.trancee.meshlink.model.MeshLinkState
import ch.trancee.meshlink.model.Message
import ch.trancee.meshlink.model.MessageHandle
import ch.trancee.meshlink.model.PeerIdentity
import ch.trancee.meshlink.model.PowerMode
import ch.trancee.meshlink.model.PowerModeSettings
import ch.trancee.meshlink.model.RegulatoryRegion
import ch.trancee.meshlink.model.Transfer
import ch.trancee.meshlink.model.TransferHandle
import ch.trancee.meshlink.model.TransferOptions
import ch.trancee.meshlink.model.TransferResult
import ch.trancee.meshlink.util.requireSetting
import ch.trancee.meshlink.model.TransferSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import kotlin.native.ObjCName
import co.touchlab.skie.configuration.annotations.SuppressSkieWarning

private const val MAX_APP_ID_BYTES = 255

/**
 * MeshLink SDK entry point. Instance-based — construct with [MeshLinkSettings] and a
 * [MeshLinkEnvironment].
 *
 * Multiple instances may coexist. Virtual environments may run concurrently, but one physical
 * environment grants its BLE radio lease to only one running instance. A conflicting start fails
 * with [RadioInUseException]. Stopping or rolling back a failed start releases the lease.
 *
 * Lifecycle states are [MeshLinkState.UNINITIALIZED], [MeshLinkState.CONFIGURED],
 * [MeshLinkState.RUNNING], [MeshLinkState.PAUSED], and [MeshLinkState.STOPPED]. The constructor
 * transitions `UNINITIALIZED` → `CONFIGURED`; `start()` transitions `CONFIGURED` → `RUNNING`.
 *
 * Commands are serialized, idempotent at their target state, and restartable after stop. Immediate
 * failures use [MeshLinkException]; transfer failures use terminal [TransferResult] outcomes.
 *
 * SPEC-ANCHOR: meshlink-public-api
 */
@ObjCName(name = "MeshLink")
@SuppressSkieWarning.NameCollision(suppress = true)
public class MeshLink
private constructor(
    private val settings: MeshLinkSettings,
    private val environment: MeshLinkEnvironment,
) {
    /** The MeshLink library version. */
    public companion object {
        public val VERSION: MeshLinkVersion = MeshLinkVersion(0, 1, 0)

        /**
         * Creates a [MeshLink] instance.
         *
         * @param settings immutable settings validated at construction
         * @param environment platform-specific environment (created via platform factory)
         * @return MeshLink instance in [MeshLinkState.CONFIGURED] state
         * @throws ConfigurationException if settings validation fails
         */
        public fun create(settings: MeshLinkSettings, environment: MeshLinkEnvironment): MeshLink {
            // Validate critical settings — also covers direct MeshLinkSettings construction
            // bypassing the DSL builder. Full parameter validation runs in MeshLinkSettingsBuilder.build().
            requireSetting(settings.appId.isNotBlank(), "appId must not be blank")
            requireSetting(
                settings.appId.encodeToByteArray().size <= MAX_APP_ID_BYTES,
                "appId exceeds $MAX_APP_ID_BYTES bytes",
            )

            return MeshLink(settings, environment)
        }
    }

    /** Current lifecycle state. */
    public val state: StateFlow<MeshLinkState> = MutableStateFlow(MeshLinkState.CONFIGURED)

    /** Peers snapshot (includes unverified, verifying, trusted, mismatched, revoked). */
    public val peers: StateFlow<List<KnownPeer>> = MutableStateFlow(emptyList())

    /** Active transfers snapshot. */
    public val transfers: StateFlow<List<Transfer>> = MutableStateFlow(emptyList())

    /** Incoming complete messages flow. */
    public val messages: Flow<Message> = MutableSharedFlow()

    /** Diagnostic events flow. */
    public val diagnostics: Flow<DiagnosticEvent> = MutableSharedFlow()

    /** Current power mode. */
    public val powerMode: StateFlow<PowerMode> = MutableStateFlow(PowerMode.MEDIUM)

    /** Effective power settings after regulatory/platform clamping. */
    public val powerModeSettings: StateFlow<PowerModeSettings> =
        MutableStateFlow(PowerMode.MEDIUM.settings)

    /**
     * Starts the MeshLink instance.
     *
     * Transitions state from [MeshLinkState.CONFIGURED] to [MeshLinkState.RUNNING]. Acquires BLE
     * radio lease, validates permissions, starts advertising and scanning.
     *
     * @throws LifecycleException if not in CONFIGURED state
     * @throws PermissionException if required permissions not granted
     * @throws BluetoothException if Bluetooth radio unavailable
     * @throws RadioInUseException if another instance holds the radio lease
     */
    public suspend fun start() {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Pauses the MeshLink instance.
     *
     * Transitions state from [MeshLinkState.RUNNING] to [MeshLinkState.PAUSED]. Retains environment
     * lease and in-memory protocol state while stopping new discovery and transfer admission.
     *
     * @throws LifecycleException if not in RUNNING state
     */
    public suspend fun pause() {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Resumes the MeshLink instance.
     *
     * Transitions state from [MeshLinkState.PAUSED] to [MeshLinkState.RUNNING].
     *
     * @throws LifecycleException if not in PAUSED state
     */
    public suspend fun resume() {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Stops the MeshLink instance.
     *
     * Transitions state to [MeshLinkState.STOPPED]. Releases radio resources, clears ephemeral
     * routes and transfers, retains only required persisted identity and trust state.
     *
     * Idempotent — safe to call multiple times.
     */
    public suspend fun stop() {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Changes the power mode at runtime.
     *
     * All routing, security, regulatory, persistence, enableBackground, diagnostics capacity, and
     * transfer defaults remain fixed. Existing transfers retain their established chunk framing;
     * new transfers and connections use the updated settings.
     *
     * A failed update leaves both [powerMode] and [powerModeSettings] at their previous successful
     * values.
     *
     * @throws LifecycleException if not in RUNNING or PAUSED state
     * @throws ConfigurationException if power mode not available
     */
    public suspend fun setPowerMode(powerMode: PowerMode) {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Sends a MESSAGE (up to 64 KiB, auto-accepted).
     *
     * @param destination target peer identity
     * @param payload message payload bytes
     * @param options priority and optional time-to-live override
     * @return handle for observing status and awaiting outcome
     * @throws TransferException if transfer cannot be initiated
     */
    public suspend fun sendMessage(
        destination: PeerIdentity,
        payload: ByteArray,
        options: TransferOptions = TransferOptions.DEFAULT,
    ): MessageHandle {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Sends a PAYLOAD (large payload, requires host sink on receiver).
     *
     * @param destination target peer identity
     * @param source random-access source for payload data
     * @param options priority and optional time-to-live override
     * @return handle for observing status and awaiting outcome
     * @throws TransferException if transfer cannot be initiated
     */
    public suspend fun sendPayload(
        destination: PeerIdentity,
        source: TransferSource,
        options: TransferOptions = TransferOptions.DEFAULT,
    ): TransferHandle {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Revokes trust for a peer.
     *
     * Cancels active work, persists [REVOKED], rejects future XX/IK/rotation recovery after
     * identity resolution. Does not delete the blocking record. An explicit [resetTrust] is
     * required to permit trust again.
     *
     * @param peer peer identity to revoke
     * @throws TrustException if peer not found
     */
    public suspend fun revokeTrust(peer: PeerIdentity) {
        TODO("Not implemented — scaffold for BCV baseline")
    }

    /**
     * Resets trust for a peer.
     *
     * Cancels active work, deletes the peer's current binding and rotation position, and permits a
     * future first-contact XX/automatic TOFU flow. Never changes local identity or keys.
     *
     * @param peer peer identity to reset
     * @throws TrustException if peer not found
     */
    public suspend fun resetTrust(peer: PeerIdentity) {
        TODO("Not implemented — scaffold for BCV baseline")
    }
}
