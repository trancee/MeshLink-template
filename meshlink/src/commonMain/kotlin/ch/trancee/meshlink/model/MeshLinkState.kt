package ch.trancee.meshlink.model

// SPEC-ANCHOR: meshlink-state

/**
 * MeshLink instance lifecycle state.
 *
 * The state machine progresses UNINITIALIZED → CONFIGURED → RUNNING, then RUNNING ↔ PAUSED, then
 * RUNNING/PAUSED → STOPPED.
 *
 * See SPEC.md §2.3 for lifecycle behavior.
 */
public enum class MeshLinkState {
    /** Instance created but not yet constructed with settings. */
    UNINITIALIZED,

    /** Instance constructed with settings; start() not yet called. */
    CONFIGURED,

    /** Instance started and active. */
    RUNNING,

    /** Instance temporarily suspended. */
    PAUSED,

    /** Instance stopped and resources released. */
    STOPPED,
}
