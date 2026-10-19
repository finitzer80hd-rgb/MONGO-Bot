package com.example.monopolyassistant

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var output: TextView
    private val captureRequest = 7001

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(ScreenMonitorService.EXTRA_TEXT).orEmpty()
            when (intent?.action) {
                ScreenMonitorService.ACTION_OCR -> runOnUiThread {
                    output.text = text.ifBlank { "No readable text detected in the latest frame." }
                    status.text = "Monitoring is active"
                }
                ScreenMonitorService.ACTION_STATUS -> runOnUiThread { status.text = text }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        root.addView(TextView(this).apply { text = "Monopoly GO Assistant"; textSize = 24f })
        status = TextView(this).apply { text = "Not monitoring"; textSize = 16f; setPadding(0, pad, 0, pad) }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Reads visible screen text locally and suggests possible next steps. It does not tap or play automatically. Android will ask you to approve screen capture."
            textSize = 14f
        })
        root.addView(Button(this).apply {
            text = "Start screen monitoring"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Stop monitoring"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenMonitorService::class.java))
                status.text = "Monitoring stopped"
            }
        })
        root.addView(TextView(this).apply { text = "Latest recognized text / suggestions"; textSize = 18f; setPadding(0, pad, 0, pad / 2) })
        output = TextView(this).apply {
            text = "Start monitoring, then open Monopoly GO. Recognized screen text will appear here."
            textSize = 14f; setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val filter = IntentFilter().apply {
            addAction(ScreenMonitorService.ACTION_OCR)
            addAction(ScreenMonitorService.ACTION_STATUS)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }

    @Deprecated("Activity result API retained for starter compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, ScreenMonitorService::class.java).apply {
                putExtra(ScreenMonitorService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenMonitorService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, service)
            status.text = "Starting screen monitor…"
        } else if (requestCode == captureRequest) status.text = "Screen capture permission was not granted"
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
