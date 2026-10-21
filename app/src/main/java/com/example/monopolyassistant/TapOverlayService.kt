package com.example.monopolyassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * User-triggered one-shot tap helper.
 * The user drags the overlay over a game control and taps the overlay.
 * It removes itself briefly, then injects one tap at that exact screen position.
 * No unattended loop or automatic game-state action is performed.
 */
class TapOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        Toast.makeText(this, "Tap helper ready. Drag the pill over a control, then tap it once.", Toast.LENGTH_LONG).show()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setBackgroundColor(0xEE315C4B.toInt())
        }
        val tap = TextView(this).apply {
            text = "DRAG • TAP ONCE"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
        }
        val close = Button(this).apply {
            text = "×"
            textSize = 18f
            minWidth = (44 * density).toInt()
            setPadding(0, 0, 0, 0)
            setOnClickListener { removeOverlay() }
        }
        container.addView(tap)
        container.addView(close)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (180 * density).toInt()
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        tap.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = lp.x; startY = lp.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        lp.x = (startX + dx).coerceAtLeast(0)
                        lp.y = (startY + dy).coerceAtLeast(0)
                        try { windowManager.updateViewLayout(container, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val x = lp.x + container.width / 2
                        val y = lp.y + container.height / 2
                        performOneTap(x.toFloat(), y.toFloat())
                    }
                    true
                }
                else -> true
            }
        }
        overlay = container
        params = lp
        try { windowManager.addView(container, lp) } catch (_: Exception) {
            overlay = null
            params = null
        }
    }

    private fun performOneTap(x: Float, y: Float) {
        if (busy) return
        busy = true
        removeOverlay()
        mainHandler.postDelayed({
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    busy = false
                    mainHandler.postDelayed({ showOverlay() }, 250)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    busy = false
                    mainHandler.postDelayed({ showOverlay() }, 250)
                    Toast.makeText(this@TapOverlayService, "Tap was cancelled by Android.", Toast.LENGTH_SHORT).show()
                }
            }, null)
            if (!dispatched) {
                busy = false
                showOverlay()
                Toast.makeText(this, "Android did not accept the tap gesture.", Toast.LENGTH_SHORT).show()
            }
        }, 120)
    }

    private fun removeOverlay() {
        val current = overlay ?: return
        try { windowManager.removeView(current) } catch (_: Exception) {}
        overlay = null
        params = null
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
