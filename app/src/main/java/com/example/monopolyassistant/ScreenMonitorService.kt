package com.example.monopolyassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class ScreenMonitorService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_TEXT = "text"
        const val ACTION_OCR = "com.example.monopolyassistant.OCR"
        const val ACTION_STATUS = "com.example.monopolyassistant.STATUS"
        private const val CHANNEL_ID = "screen_monitor"
        private const val NOTIFICATION_ID = 44
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val busy = AtomicBoolean(false)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var lastText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null || code == 0) {
            broadcastStatus("Missing screen capture permission. Stop and start again.")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(mainLooper))
            startCapture()
            broadcastStatus("Monitoring is active — open Monopoly GO.")
        } catch (e: Exception) {
            broadcastStatus("Could not start capture: ${e.message ?: "unknown error"}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        worker = HandlerThread("screen-ocr").also { it.start() }
        handler = Handler(worker!!.looper)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ source ->
            val image = try { source.acquireLatestImage() } catch (_: Exception) { null }
            if (image != null) processImage(image)
        }, handler)
        display = projection?.createVirtualDisplay("MonopolyAssistantCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
    }

    private fun processImage(image: Image) {
        if (!busy.compareAndSet(false, true)) { image.close(); return }
        try {
            val bitmap = imageToBitmap(image)
            image.close()
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val text = result.text.trim()
                    if (text != lastText) { lastText = text; broadcastText(makeSuggestion(text)) }
                    bitmap.recycle()
                }
                .addOnFailureListener {
                    bitmap.recycle()
                    broadcastStatus("OCR could not read this frame. Try a different screen.")
                }
                .addOnCompleteListener { busy.set(false) }
        } catch (_: Exception) {
            try { image.close() } catch (_: Exception) {}
            busy.set(false)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun makeSuggestion(raw: String): String {
        if (raw.isBlank()) return "No readable text found. Keep the game screen open and try again."
        val lower = raw.lowercase()
        val advice = when {
            "free gift" in lower || "free gifts" in lower -> "Possible free gift screen. Review it and claim manually if available."
            "bank heist" in lower || "shutdown" in lower -> "A railroad mini-game may be open. Review the choices before acting."
            "album" in lower || "sticker" in lower -> "Sticker or album screen may be open. Check duplicates and event goals."
            "upgrade" in lower || "landmark" in lower -> "A landmark screen may be open. Check costs and cash before upgrading."
            "roll" in lower || "dice" in lower -> "Dice-related text detected. Check your event, dice reserve, and multiplier."
            "tournament" in lower || "event" in lower -> "An event screen may be open. Review milestones and remaining time."
            else -> "Text captured. Suggestions are heuristic; verify the screen visually."
        }
        return "$advice\n\n--- Recognized text ---\n$raw"
    }

    private fun broadcastText(text: String) {
        sendBroadcast(Intent(ACTION_OCR).setPackage(packageName).putExtra(EXTRA_TEXT, text))
    }
    private fun broadcastStatus(text: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_TEXT, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen monitoring", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Monopoly GO Assistant")
        .setContentText("Screen monitoring is active")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setOngoing(true).build()

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        worker?.quitSafely()
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
