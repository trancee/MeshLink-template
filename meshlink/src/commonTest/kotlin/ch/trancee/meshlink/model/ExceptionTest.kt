package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExceptionTest {

    @Test
    fun `MeshLinkException hierarchy exists`() {
        // All subtypes must be instantiable
        val configEx = ConfigurationException(ErrorCode.INVALID_PARAMETER, "bad param")
        val lifecycleEx = LifecycleException(ErrorCode.INVALID_STATE, "bad state")
        val permEx = PermissionException(ErrorCode.PERMISSION_DENIED, "denied")
        val btEx = BluetoothException(ErrorCode.CONNECTION_FAILED, "conn fail")
        val storageEx = StorageException(ErrorCode.STORAGE_CORRUPTED, "corrupt")
        val cryptoEx = CryptoException(ErrorCode.REPLAY_DETECTED, "replay")
        val trustEx = TrustException(ErrorCode.TRUST_VIOLATION, "trust")
        val routingEx = RoutingException(ErrorCode.NO_ROUTE, "no route")
        val transferEx = TransferException(ErrorCode.TRANSFER_TIMEOUT, "timeout")

        val radioEx = RadioInUseException()
        assertEquals(ErrorCode.BLUETOOTH_DISABLED, radioEx.errorCode)
        assertEquals("BLE radio lease already held by another MeshLink instance", radioEx.message)

        val radioEx2 = RadioInUseException(ErrorCode.CONNECTION_FAILED, "custom msg")
        assertEquals(ErrorCode.CONNECTION_FAILED, radioEx2.errorCode)
        assertEquals("custom msg", radioEx2.message)

        assertEquals(ErrorCode.INVALID_PARAMETER, configEx.errorCode)
        assertEquals(ErrorCode.INVALID_STATE, lifecycleEx.errorCode)
        assertEquals(ErrorCode.PERMISSION_DENIED, permEx.errorCode)
        assertEquals(ErrorCode.CONNECTION_FAILED, btEx.errorCode)
        assertEquals(ErrorCode.STORAGE_CORRUPTED, storageEx.errorCode)
        assertEquals(ErrorCode.REPLAY_DETECTED, cryptoEx.errorCode)
        assertEquals(ErrorCode.TRUST_VIOLATION, trustEx.errorCode)
        assertEquals(ErrorCode.NO_ROUTE, routingEx.errorCode)
        assertEquals(ErrorCode.TRANSFER_TIMEOUT, transferEx.errorCode)

        // All are MeshLinkException
        val all: List<Any> =
            listOf(
                configEx,
                lifecycleEx,
                permEx,
                btEx,
                storageEx,
                cryptoEx,
                trustEx,
                routingEx,
                transferEx,
                radioEx,
            )
        all.forEach { assertTrue(it is MeshLinkException) }
    }

    @Test
    fun `MeshLinkException sealed interface exhaustive`() {
        // This test ensures when expressions over MeshLinkException are exhaustive
        val ex: MeshLinkException = ConfigurationException(ErrorCode.INVALID_PARAMETER, "test")

        val category =
            when (ex) {
                is ConfigurationException -> "config"
                is LifecycleException -> "lifecycle"
                is PermissionException -> "permission"
                is BluetoothException -> "bluetooth"
                is StorageException -> "storage"
                is CryptoException -> "crypto"
                is TrustException -> "trust"
                is RoutingException -> "routing"
                is TransferException -> "transfer"
                is RadioInUseException -> "radio-in-use"
            }

        assertEquals("config", category)
    }
}
