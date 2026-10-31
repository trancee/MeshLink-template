package ch.trancee.meshlink.proof

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

internal object ProofUuids {
    val service: UUID = UUID.fromString("c247f4e0-18b7-4d48-a73c-7a62f4860190")
    val request: UUID = UUID.fromString("c247f4e1-18b7-4d48-a73c-7a62f4860190")
    val response: UUID = UUID.fromString("c247f4e2-18b7-4d48-a73c-7a62f4860190")
    val cccd: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

internal fun Context.proofPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

internal class AndroidGattTransport(private val context: Context) : ProofTransport {
    private var endpoint: GattEndpoint? = null

    override fun start(role: ProofRole, listener: ProofTransport.Listener) {
        stop()
        if (context.proofPermissions().any {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }) {
            listener.failed("Grant Bluetooth/location permissions first")
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.failed("Enable Bluetooth first")
            return
        }
        val link = if (role == ProofRole.CENTRAL) {
            GattCentral(context, adapter, listener)
        } else {
            GattPeripheral(context, adapter, listener)
        }
        endpoint = link
        link.guard { link.start() }
    }

    override fun send(bytes: ByteArray) {
        endpoint?.let { link -> link.guard { link.send(bytes) } }
    }

    override fun stop() {
        endpoint?.close()
        endpoint = null
    }
}

internal abstract class GattEndpoint(protected val listener: ProofTransport.Listener) {
    protected val handler = Handler(Looper.getMainLooper())
    @Volatile protected var active = true
    private val timeout = Runnable { fail("45 second run deadline reached") }

    init {
        handler.postDelayed(timeout, RUN_DEADLINE_MS)
    }

    abstract fun start()

    abstract fun send(bytes: ByteArray)

    protected abstract fun release()

    fun close() {
        if (!active) return
        active = false
        handler.removeCallbacks(timeout)
        try {
            release()
        } catch (_: SecurityException) {
            // Permission can be revoked while a run is active.
        }
    }

    protected fun post(action: () -> Unit) {
        handler.post { if (active) guard(action) }
    }

    fun guard(action: () -> Unit) {
        try {
            action()
        } catch (_: SecurityException) {
            fail("Bluetooth permission revoked")
        }
    }

    protected fun fail(reason: String) {
        if (active) listener.failed(reason)
    }

    private companion object {
        const val RUN_DEADLINE_MS = 45_000L
    }
}
