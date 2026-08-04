package ch.trancee.meshlink.model

import kotlin.test.Test

class TransferSinkTest {

    @Test
    fun `TransferSink interface exists`() {
        val sink =
            object : TransferSink {
                override suspend fun write(offset: Long, bytes: ByteArray) {
                    // Empty implementation for compile test
                }

                override suspend fun complete() {
                    // Empty implementation for compile test
                }

                override suspend fun abort(cause: MeshLinkException?) {
                    // Empty implementation for compile test
                }
            }
        // Compiles = API exists
    }
}
