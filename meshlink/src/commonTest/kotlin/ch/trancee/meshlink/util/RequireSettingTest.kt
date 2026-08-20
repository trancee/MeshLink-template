package ch.trancee.meshlink.util

import ch.trancee.meshlink.model.ConfigurationException
import ch.trancee.meshlink.model.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequireSettingTest {
    @Test
    fun `when condition is true does not throw`() {
        // Arrange
        val condition = true
        val message = "should not be reached"

        // Act
        requireSetting(condition, message)

        // Assert — no exception thrown means test passes
    }

    @Test
    fun `when condition is false throws ConfigurationException with message and code`() {
        // Arrange
        val condition = false
        val message = "invalid configuration"

        // Act
        val exception =
            assertFailsWith<ConfigurationException> {
                requireSetting(condition, message)
            }

        // Assert
        assertEquals(message, exception.message)
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.errorCode)
    }
}
