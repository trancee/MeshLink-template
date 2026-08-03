package ch.trancee.meshlink.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Immutable snapshot of a finite payload operation (MESSAGE or PAYLOAD).
 *
 * Exposed via [MeshLink.transfers] StateFlow.
 */
public data class Transfer(
    /** Discriminates MESSAGE vs PAYLOAD wire format. */
    public val kind: TransferKind,

    /** Origin-scoped identifier. */
    public val id: UInt,

    /** Current transfer status. */
    public val status: StateFlow<TransferStatus>,
)
