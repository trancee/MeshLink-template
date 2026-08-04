package ch.trancee.meshlink.model

/**
 * Transfer kind discriminates between MESSAGE and PAYLOAD wire formats. Internal to the transfer
 * layer; exposed via [Transfer.kind].
 */
public enum class TransferKind(
    /** Explicit wire code matching `specs/codecs/enums.yaml`. */
    public val code: UByte
) {
    MESSAGE(0x00.toUByte()),
    PAYLOAD(0x01.toUByte()),
}
