package ch.trancee.meshlink.model

/**
 * Transfer type discriminates between MESSAGE and PAYLOAD wire formats. Internal to the transfer
 * layer; exposed via [Transfer.type].
 */
public enum class TransferType(
    /** Explicit wire code matching `specs/codecs/enums.yaml`. */
    public val code: UByte
) {
    MESSAGE(0x00.toUByte()),
    PAYLOAD(0x01.toUByte()),
}
