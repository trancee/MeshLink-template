package ch.trancee.meshlink.transport

/**
 * Capability and process-local health state for one adjacent L2CAP channel.
 *
 * Ordinal values are defined in [specs/codecs/enums.yaml] (L2capState). This enum uses ordinal
 * storage (no wire codes) — it is internal process state only.
 *
 * SPEC-ANCHOR: enums
 */
internal enum class L2capState {
    UNSUPPORTED,
    AVAILABLE,
    CONNECTING,
    ACTIVE,
    BACKING_OFF,
    DISABLED,
}
