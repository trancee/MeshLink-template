package ch.trancee.meshlink.proof

import java.nio.ByteBuffer
import java.security.SecureRandom

enum class ProofRole { CENTRAL, PERIPHERAL }

enum class ProofState { STOPPED, STARTING, READY, SENDING, PASSED, FAILED }

data class ProofSnapshot(
    val state: ProofState = ProofState.STOPPED,
    val sent: Int = 0,
    val received: Int = 0,
    val detail: String = "",
)

interface ProofTransport {
    fun start(role: ProofRole, listener: Listener)

    fun send(bytes: ByteArray)

    fun stop()

    interface Listener {
        fun ready()

        fun received(bytes: ByteArray)

        fun failed(reason: String)
    }
}

/** Single-thread-confined synthetic probe, not a MeshLink protocol implementation. */
class ProofRunner(
    private val transport: ProofTransport,
    private val packetCount: Int = 100,
    private val changed: (ProofSnapshot) -> Unit = {},
) {
    init {
        require(packetCount in 1..MAX_PACKETS)
    }

    var snapshot: ProofSnapshot = ProofSnapshot()
        private set

    private var generation = 0
    private var token = 0
    private var role = ProofRole.CENTRAL
    private var expected = byteArrayOf()

    fun start(role: ProofRole) {
        stop()
        this.role = role
        val current = generation
        update(ProofSnapshot(ProofState.STARTING))
        transport.start(
            role,
            object : ProofTransport.Listener {
                override fun ready() {
                    if (current == generation && snapshot.state == ProofState.STARTING) {
                        update(snapshot.copy(state = ProofState.READY))
                    }
                }

                override fun received(bytes: ByteArray) {
                    if (current == generation) receive(bytes)
                }

                override fun failed(reason: String) {
                    if (current == generation) finish(ProofState.FAILED, reason)
                }
            },
        )
    }

    fun send() {
        if (role != ProofRole.CENTRAL || snapshot.state != ProofState.READY) return
        token = SecureRandom().nextInt()
        update(snapshot.copy(state = ProofState.SENDING))
        sendNext()
    }

    fun stop() {
        generation++
        transport.stop()
        update(snapshot.copy(state = ProofState.STOPPED))
    }

    private fun sendNext() {
        val packet = ByteBuffer.allocate(PACKET_SIZE)
            .put(0x4d.toByte()).put(0x4c.toByte()).put(1).put(0)
            .putInt(token).putInt(snapshot.received)
            .put(ByteArray(PAYLOAD_SIZE) { it.toByte() }).array()
        expected = packet.copyOf().also {
            it[KIND_OFFSET] = 1
            for (index in PAYLOAD_OFFSET until PACKET_SIZE) {
                it[index] = (BYTE_MAX - (index - PAYLOAD_OFFSET)).toByte()
            }
        }
        update(snapshot.copy(sent = snapshot.sent + 1))
        transport.send(packet)
    }

    private fun receive(bytes: ByteArray) {
        if (role == ProofRole.PERIPHERAL) {
            receiveRequest(bytes)
            return
        }
        if (snapshot.state != ProofState.SENDING || !bytes.contentEquals(expected)) {
            finish(ProofState.FAILED, "Unexpected or corrupt response")
            return
        }

        update(snapshot.copy(received = snapshot.received + 1))
        if (snapshot.received == packetCount) {
            finish(ProofState.PASSED, "All synthetic responses verified")
        } else {
            sendNext()
        }
    }

    private fun finish(state: ProofState, detail: String) {
        generation++
        transport.stop()
        update(snapshot.copy(state = state, detail = detail))
    }

    private fun receiveRequest(bytes: ByteArray) {
        val valid = snapshot.state == ProofState.READY &&
            snapshot.received < packetCount && bytes.size == PACKET_SIZE &&
            bytes.take(4) == listOf<Byte>(0x4d, 0x4c, 1, 0) &&
            ByteBuffer.wrap(bytes, 8, 4).int == snapshot.received &&
            bytes.sliceArray(PAYLOAD_OFFSET until PACKET_SIZE)
                .contentEquals(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        if (!valid) {
            finish(ProofState.FAILED, "Unexpected or corrupt request")
            return
        }
        val receivedToken = ByteBuffer.wrap(bytes, 4, 4).int
        if (snapshot.received == 0) token = receivedToken
        if (receivedToken != token) {
            finish(ProofState.FAILED, "Request token changed")
            return
        }
        val response = bytes.copyOf()
        response[KIND_OFFSET] = 1
        for (index in PAYLOAD_OFFSET until PACKET_SIZE) {
            response[index] = (BYTE_MAX - (index - PAYLOAD_OFFSET)).toByte()
        }
        update(snapshot.copy(received = snapshot.received + 1, sent = snapshot.sent + 1))
        transport.send(response)
    }

    private fun update(value: ProofSnapshot) {
        snapshot = value
        changed(value)
    }

    private companion object {
        const val PACKET_SIZE = 20
        const val MAX_PACKETS = 100
        const val PAYLOAD_SIZE = 8
        const val PAYLOAD_OFFSET = 12
        const val BYTE_MAX = 255
        const val KIND_OFFSET = 3
    }
}
