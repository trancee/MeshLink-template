package ch.trancee.meshlink.model

import ch.trancee.meshlink.diagnostics.DiagnosticCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TransferStatusTest {

    @Test
    fun `TransferStatus has all fields`() {
        // Arrange
        val status =
            TransferStatus(
                state = TransferState.TRANSFERRING,
                offset = 512L,
                total = 1024L,
                retryCount = 2,
                result = null,
                diagnosticCode = null,
                diagnosticSeverity = null,
            )

        // Act + Assert — verify every field
        assertEquals(TransferState.TRANSFERRING, status.state)
        assertEquals(512L, status.offset)
        assertEquals(1024L, status.total)
        assertEquals(2, status.retryCount)
        assertNull(status.result)
        assertNull(status.diagnosticCode)
        assertNull(status.diagnosticSeverity)
    }

    @Test
    fun `TransferStatus with terminal result and diagnostic fields`() {
        // Arrange — a status with all fields populated
        val result = TransferResult.UnrecoverableFailure("corrupted")
        val code = DiagnosticCode(0x0603u)
        val severity = DiagnosticSeverity.ERROR

        // Act
        val status =
            TransferStatus(
                state = TransferState.RETRANSMITTING,
                offset = 2048L,
                total = 4096L,
                retryCount = 5,
                result = result,
                diagnosticCode = code,
                diagnosticSeverity = severity,
            )

        // Assert
        assertEquals(TransferState.RETRANSMITTING, status.state)
        assertEquals(2048L, status.offset)
        assertEquals(4096L, status.total)
        assertEquals(5, status.retryCount)
        assertEquals(result, status.result)
        assertEquals(code, status.diagnosticCode)
        assertEquals(severity, status.diagnosticSeverity)
        // Verify data-class equality and hashCode consistency
        val same =
            TransferStatus(
                state = TransferState.RETRANSMITTING,
                offset = 2048L,
                total = 4096L,
                retryCount = 5,
                result = result,
                diagnosticCode = code,
                diagnosticSeverity = severity,
            )
        assertEquals(status, same)
        assertEquals(status.hashCode(), same.hashCode())
    }

    @Test
    fun `TransferStatus with zero offset and total`() {
        // Arrange + Act — boundary values
        val status =
            TransferStatus(
                state = TransferState.AWAITING_DECISION,
                offset = 0L,
                total = 0L,
                retryCount = 0,
            )

        // Assert
        assertEquals(0L, status.offset)
        assertEquals(0L, status.total)
        assertEquals(0, status.retryCount)
        assertNull(status.diagnosticCode)
        assertNull(status.diagnosticSeverity)
    }

    @Test
    fun `TransferStatus equality reflects field differences`() {
        // Arrange — two statuses differing only in result
        val withResult =
            TransferStatus(
                state = TransferState.TRANSFERRING,
                offset = 0L,
                total = 100L,
                retryCount = 0,
                result = TransferResult.Completed,
            )
        val withoutResult =
            TransferStatus(
                state = TransferState.TRANSFERRING,
                offset = 0L,
                total = 100L,
                retryCount = 0,
                result = null,
            )
        val identical =
            TransferStatus(
                state = TransferState.TRANSFERRING,
                offset = 0L,
                total = 100L,
                retryCount = 0,
                result = TransferResult.Completed,
            )

        // Assert — different result makes them unequal; same fields makes them equal
        assertNotEquals(withResult, withoutResult)
        assertEquals(withResult, identical)
        assertEquals(withResult.hashCode(), identical.hashCode())
    }
}
