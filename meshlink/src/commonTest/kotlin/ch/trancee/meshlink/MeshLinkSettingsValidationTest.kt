package ch.trancee.meshlink

import ch.trancee.meshlink.model.ConfigurationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MeshLinkSettingsValidationTest {
    @Test
    fun `empty app id is rejected during settings construction`() {
        val ex = assertFailsWith<ConfigurationException> { meshLinkSettings { appId = "" } }
        assertTrue(
            ex.message!!.contains("appId"),
            "Expected message to mention 'appId', got: ${ex.message}",
        )
    }

    @Test
    fun `whitespace only app id is rejected during settings construction`() {
        val ex = assertFailsWith<ConfigurationException> { meshLinkSettings { appId = "   " } }
        assertTrue(
            ex.message!!.contains("appId"),
            "Expected message to mention 'appId', got: ${ex.message}",
        )
    }

    @Test
    fun `app id exceeding 255 UTF-8 bytes is rejected during construction`() {
        val ex =
            assertFailsWith<ConfigurationException> {
                meshLinkSettings { appId = "é".repeat(128) }
            }
        assertTrue(
            ex.message!!.contains("appId"),
            "Expected message to mention 'appId', got: ${ex.message}",
        )
    }

    @Test
    fun `invalid key rotation values are rejected`() {
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                keyRotation { interval = Duration.ZERO }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                keyRotation { rotationGracePeriod = -1.seconds }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                keyRotation { compromiseGracePeriod = -1.seconds }
            }
        }
    }

    @Test
    fun `invalid transfer values are rejected`() {
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                transfer { maxRetries = -1 }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                transfer { chunkSize = 0 }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                transfer { maxTransfersPerPeer = 4 }
            }
        }
    }

    @Test
    fun `invalid routing values are rejected`() {
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                routing { routeAdvertisementChangeThreshold = -1 }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                routing { routeDigestInterval = Duration.ZERO }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                routing {
                    routeDigestInterval = 15.minutes
                    routeExpiry = 15.minutes
                }
            }
        }
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                routing { maxRoutes = 257 }
            }
        }
    }

    @Test
    fun `zero max transfers per peer is rejected`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        builder.transferMaxTransfersPerPeer = 0

        // Act / Assert
        val ex = assertFailsWith<ConfigurationException> { builder.build() }
        assertTrue(ex.message!!.isNotEmpty(), "Exception message should not be empty")
    }

    @Test
    fun `max transfers per peer above three is rejected`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        builder.transferMaxTransfersPerPeer = 4

        // Act / Assert
        val ex = assertFailsWith<ConfigurationException> { builder.build() }
        assertTrue(ex.message!!.isNotEmpty(), "Exception message should not be empty")
    }

    @Test
    fun `zero max routes is rejected`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        builder.maxRoutes = 0

        // Act / Assert
        val ex = assertFailsWith<ConfigurationException> { builder.build() }
        assertTrue(ex.message!!.isNotEmpty(), "Exception message should not be empty")
    }

    @Test
    fun `max routes above 256 is rejected`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        builder.maxRoutes = 257

        // Act / Assert
        val ex = assertFailsWith<ConfigurationException> { builder.build() }
        assertTrue(ex.message!!.isNotEmpty(), "Exception message should not be empty")
    }

    @Test
    fun `maximum valid route and transfer limits are accepted`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        builder.transferMaxTransfersPerPeer = 1
        builder.maxRoutes = 1

        // Act
        val actual = builder.build()

        // Assert
        assertEquals(1, actual.transfer.maxTransfersPerPeer)
        assertEquals(1, actual.routing.maxRoutes)
    }

    @Test
    fun `maximum configured route and transfer limits are accepted`() {
        // Arrange
        val builder = MeshLinkSettingsBuilder()
        builder.appId = "com.example.mesh"
        builder.transferMaxTransfersPerPeer = 3
        builder.maxRoutes = 256

        // Act
        val actual = builder.build()

        // Assert
        assertEquals(3, actual.transfer.maxTransfersPerPeer)
        assertEquals(256, actual.routing.maxRoutes)
    }

    @Test
    fun `invalid diagnostic buffer size is rejected`() {
        assertFailsWith<ConfigurationException> {
            meshLinkSettings {
                appId = "com.example.mesh"
                diagnostics { eventBufferSize = 0 }
            }
        }
    }
}
