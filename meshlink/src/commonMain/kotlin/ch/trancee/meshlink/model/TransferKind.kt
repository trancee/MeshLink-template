package ch.trancee.meshlink.model

/**
 * Transfer kind discriminates between MESSAGE and PAYLOAD wire formats. Internal to the transfer
 * layer; exposed via [Transfer.kind].
 */
public enum class TransferKind {
    MESSAGE,
    PAYLOAD,
}
