package ch.trancee.meshlink.proof

import kotlin.test.Test
import kotlin.test.assertTrue

class ProofRunnerTest {
    @Test
    fun `timeout ends run and ignores late data without sending another packet`() {
        val transport = RecordingTransport()
        val runner = ProofRunner(transport)
        runner.start(ProofRole.CENTRAL)
        transport.listener.ready()
        runner.send()

        transport.listener.failed("45 second deadline")
        transport.reply()

        assertTrue(runner.snapshot.state == ProofState.FAILED)
        assertTrue(runner.snapshot.received == 0)
        assertTrue(transport.sent.size == 1)
        assertTrue(transport.stopped)
    }

    @Test
    fun `corrupt response fails rather than admitting the next request`() {
        val transport = RecordingTransport()
        val runner = ProofRunner(transport)
        runner.start(ProofRole.CENTRAL)
        transport.listener.ready()
        runner.send()

        transport.listener.received(ByteArray(20))

        assertTrue(runner.snapshot.state == ProofState.FAILED)
        assertTrue(transport.sent.size == 1)
        assertTrue(transport.stopped)
    }

    @Test
    fun `repeated start isolates callbacks and send waits for subscription`() {
        val transport = RecordingTransport()
        val runner = ProofRunner(transport)
        runner.start(ProofRole.CENTRAL)
        val old = transport.listener

        runner.start(ProofRole.CENTRAL)
        old.ready()
        old.failed("Old disconnect")
        runner.send()

        assertTrue(runner.snapshot.state == ProofState.STARTING)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun `peripheral validates requests before submitting matching replies`() {
        val transport = RecordingTransport()
        val runner = ProofRunner(transport)
        runner.start(ProofRole.PERIPHERAL)
        transport.listener.ready()
        val request = byteArrayOf(
            0x4d, 0x4c, 1, 0, 0, 0, 0, 42, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7,
        )

        transport.listener.received(request)

        assertTrue(runner.snapshot.received == 1)
        assertTrue(transport.sent.single().contentEquals(byteArrayOf(
            0x4d, 0x4c, 1, 1, 0, 0, 0, 42, 0, 0, 0, 0,
            -1, -2, -3, -4, -5, -6, -7, -8,
        )))
    }

    @Test
    fun `send cannot start a central exchange in peripheral role`() {
        val transport = RecordingTransport()
        val runner = ProofRunner(transport)
        runner.start(ProofRole.PERIPHERAL)
        transport.listener.ready()

        runner.send()

        assertTrue(transport.sent.isEmpty())
        assertTrue(runner.snapshot.state == ProofState.READY)
    }

    @Test
    fun `central passes only after matching synthetic responses`() {
        val transport = RecordingTransport()
        val runner = ProofRunner(transport, packetCount = 2)
        runner.start(ProofRole.CENTRAL)
        transport.listener.ready()

        runner.send()
        transport.reply()
        transport.reply()

        assertTrue(runner.snapshot.state == ProofState.PASSED)
        assertTrue(runner.snapshot.received == 2)
        assertTrue(transport.sent.size == 2)
    }

    private class RecordingTransport : ProofTransport {
        lateinit var listener: ProofTransport.Listener
        val sent = mutableListOf<ByteArray>()
        var stopped = false

        override fun start(role: ProofRole, listener: ProofTransport.Listener) {
            this.listener = listener
            stopped = false
        }

        override fun send(bytes: ByteArray) {
            sent.add(bytes.copyOf())
        }

        override fun stop() {
            stopped = true
        }

        fun reply() {
            val response = sent.last().copyOf()
            response[3] = 1
            for (index in 12..19) response[index] = (255 - (index - 12)).toByte()
            listener.received(response)
        }
    }
}
