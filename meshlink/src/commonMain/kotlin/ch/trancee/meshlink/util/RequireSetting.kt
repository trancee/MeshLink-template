package ch.trancee.meshlink.util

import ch.trancee.meshlink.model.ConfigurationException
import ch.trancee.meshlink.model.ErrorCode

/**
 * Asserts that [condition] is true, throwing a [ConfigurationException] when the invariant
 * is violated. Use this for settings validation — pass the "is valid" condition, not the
 * "should throw" condition.
 */
internal fun requireSetting(condition: Boolean, message: String) {
    if (!condition) throw ConfigurationException(ErrorCode.INVALID_PARAMETER, message)
}
