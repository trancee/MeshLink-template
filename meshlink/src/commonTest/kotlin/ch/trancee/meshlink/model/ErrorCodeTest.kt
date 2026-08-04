package ch.trancee.meshlink.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorCodeTest {

    @Test
    fun `ErrorCode has all categories with correct ranges`() {
        // Configuration (0x01xx)
        assertEquals(0x0101u, ErrorCode.INVALID_PARAMETER.toUShort())
        assertEquals(0x0102u, ErrorCode.INVALID_STATE.toUShort())

        // Permission (0x02xx)
        assertEquals(0x0201u, ErrorCode.PERMISSION_DENIED.toUShort())

        // Bluetooth (0x03xx)
        assertEquals(0x0301u, ErrorCode.BLUETOOTH_DISABLED.toUShort())
        assertEquals(0x0302u, ErrorCode.COC_NOT_SUPPORTED.toUShort())
        assertEquals(0x0303u, ErrorCode.CONNECTION_FAILED.toUShort())
        assertEquals(0x0304u, ErrorCode.GATT_OPERATION_FAILED.toUShort())
        assertEquals(0x0305u, ErrorCode.L2CAP_CHANNEL_FAILED.toUShort())
        assertEquals(0x0306u, ErrorCode.RADIO_IN_USE.toUShort())

        // Crypto (0x04xx)
        assertEquals(0x0401u, ErrorCode.CRYPTO_OPERATION_FAILED.toUShort())
        assertEquals(0x0402u, ErrorCode.SIGNATURE_VERIFICATION_FAILED.toUShort())
        assertEquals(0x0502u, ErrorCode.ROUTE_ADVERTISEMENT_FAILED.toUShort())

        // Routing (0x05xx)
        assertEquals(0x0501u, ErrorCode.NO_ROUTE.toUShort())
        assertEquals(0x0502u, ErrorCode.ROUTE_ADVERTISEMENT_FAILED.toUShort())
        assertEquals(0x0503u, ErrorCode.ROUTE_LOOP_DETECTED.toUShort())

        // Transfer (0x06xx)
        assertEquals(0x0601u, ErrorCode.TRANSFER_TIMEOUT.toUShort())
        assertEquals(0x0602u, ErrorCode.TRANSFER_CANCELLED.toUShort())
        assertEquals(0x0603u, ErrorCode.TRANSFER_CORRUPTED.toUShort())
        assertEquals(0x0604u, ErrorCode.SESSION_NOT_FOUND.toUShort())
        assertEquals(0x0605u, ErrorCode.CHUNK_OUT_OF_BOUNDS.toUShort())

        // Storage (0x07xx)
        assertEquals(0x0701u, ErrorCode.STORAGE_UNAVAILABLE.toUShort())
        assertEquals(0x0702u, ErrorCode.STORAGE_CORRUPTED.toUShort())

        // Trust (0x0Axx)
        assertEquals(0x0A01u, ErrorCode.PEER_NOT_FOUND.toUShort())
        assertEquals(0x0A02u, ErrorCode.KEY_UNKNOWN.toUShort())
        assertEquals(0x0A03u, ErrorCode.TRUST_VIOLATION.toUShort())

        // Internal (0x0Fxx)
        assertEquals(0x0F01u, ErrorCode.INTERNAL_ERROR.toUShort())
        assertEquals(0x0F02u, ErrorCode.SERIALIZATION_FAILED.toUShort())
    }

    @Test
    fun `ErrorCode fromValue finds correct code`() {
        assertEquals(ErrorCode.INVALID_PARAMETER, ErrorCode.fromValue(0x0101u))
        assertEquals(ErrorCode.NO_ROUTE, ErrorCode.fromValue(0x0501u))
        assertNull(ErrorCode.fromValue(0x9999u))
    }
}
