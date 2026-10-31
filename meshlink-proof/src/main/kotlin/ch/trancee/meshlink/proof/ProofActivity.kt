package ch.trancee.meshlink.proof

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ProofActivity : Activity() {
    private lateinit var runner: ProofRunner
    private lateinit var status: TextView
    private var started = 0L
    private var transferStarted = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this)
        runner = ProofRunner(AndroidGattTransport(this), changed = ::render)
        layout.addView(TextView(this).apply {
            setText(R.string.proof_warning)
        })
        addButton(layout, "Grant permissions") {
            val missing = proofPermissions().filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        }
        addButton(layout, "Start peripheral") { start(ProofRole.PERIPHERAL) }
        addButton(layout, "Start central") { start(ProofRole.CENTRAL) }
        addButton(layout, "Send 100 probes from central") {
            transferStarted = SystemClock.elapsedRealtime()
            runner.send()
        }
        addButton(layout, "Stop") { runner.stop() }
        layout.addView(status)
        setContentView(layout)
        render(runner.snapshot)
    }

    private fun start(role: ProofRole) {
        started = SystemClock.elapsedRealtime()
        transferStarted = 0
        runner.start(role)
    }

    private fun render(snapshot: ProofSnapshot) {
        val elapsed = if (started == 0L) 0 else SystemClock.elapsedRealtime() - started
        val exchange = if (transferStarted == 0L) 0 else SystemClock.elapsedRealtime() - transferStarted
        status.text = getString(
            R.string.proof_status,
            snapshot.state, snapshot.sent, snapshot.received, elapsed, exchange, snapshot.detail,
        )
        if (snapshot.state in listOf(ProofState.STOPPED, ProofState.PASSED, ProofState.FAILED)) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun addButton(layout: LinearLayout, title: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            text = title
            setOnClickListener { action() }
        })
    }

    override fun onStop() {
        runner.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
}
