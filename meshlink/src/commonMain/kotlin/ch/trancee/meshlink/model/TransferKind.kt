package ch.trancee.meshlink.model

/**
 * Transfer kind discriminates between MESSAGE and PAYLOAD wire formats. Internal to the transfer
 * layer; exposed via [Transfer.kind].
 */
public enum class TransferKind(
    /** Explicit wire code matching `specs/codecs/enums.yaml`. */
    public val code: UByte
) {
    MESSAGE(TransferCode.MESSAGE),
    PAYLOAD(TransferCode.PAYLOAD),
}

private object TransferCode {
    const val MESSAGE: UByte = 0x00u
    const val PAYLOAD: UByte = 0x01u
}
