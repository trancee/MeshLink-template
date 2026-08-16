package ch.trancee.meshlink.transfer

/**
 * Whether the sink accepted or rejected an incoming transfer offer.
 *
 * Wire codes are defined in [specs/codecs/enums.yaml] (PayloadDecision).
 *
 * SPEC-ANCHOR: enums
 */
internal enum class PayloadDecision(public val code: UByte) {
    ACCEPTED(PayloadDecisionCode.ACCEPTED),
    REJECTED(PayloadDecisionCode.REJECTED),
}

private object PayloadDecisionCode {
    const val ACCEPTED: UByte = 0x00u
    const val REJECTED: UByte = 0x01u
}
