package com.example.monopolyassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility helper with a manually-triggered tap and an opt-in,
 * conservative label-based auto mode. Auto mode never purchases items.
 * It only clicks visible, explicitly labeled low-risk controls and waits
 * for the screen content to change before considering another action.
 */
class TapOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var autoButton: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var busy = false
    @Volatile private var autoMode = false
    private var lastActionAt = 0L
    private var lastSignature = ""
    private var lastActionLabel = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        Toast.makeText(this, "Tap helper ready. Auto mode starts OFF.", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoMode || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val root = rootInActiveWindow ?: return
        val sourcePackage = root.packageName?.toString().orEmpty()
        if (sourcePackage.isBlank() || sourcePackage == packageName) return
        val signature = collectVisibleText(root).take(1600)
        if (signature.isBlank() || signature == lastSignature) return
        lastSignature = signature
        val now = System.currentTimeMillis()
        if (now - lastActionAt < 2500L) return
        val target = findSafeTarget(root) ?: return
        if (target.first == lastActionLabel && now - lastActionAt < 8000L) return
        val clicked = try { target.second.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
        if (clicked) {
            lastActionAt = now
            lastActionLabel = target.first
            mainHandler.post { Toast.makeText(this, "Auto-play tapped: ${target.first}", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onInterrupt() {
        autoMode = false
        updateAutoLabel()
        removeOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setBackgroundColor(0xEE315C4B.toInt())
        }
        val tap = TextView(this).apply {
            text = "DRAG • TAP ONCE"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding((7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt())
        }
        autoButton = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding((7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt())
            setOnClickListener {
                autoMode = !autoMode
                lastSignature = ""
                updateAutoLabel()
                Toast.makeText(this@TapOverlayService,
                    if (autoMode) "Auto mode ON: labeled controls only" else "Auto mode OFF",
                    Toast.LENGTH_LONG).show()
            }
        }
        updateAutoLabel()
        val close = Button(this).apply {
            text = "×"
            textSize = 18f
            minWidth = (40 * density).toInt()
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                autoMode = false
                removeOverlay()
            }
        }
        container.addView(tap)
        container.addView(autoButton)
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
        try { windowManager.addView(container, lp) } catch (_: Exception) { overlay = null }
    }

    private fun updateAutoLabel() {
        autoButton?.text = if (autoMode) "AUTO: ON" else "AUTO: OFF"
    }

    private fun findSafeTarget(root: AccessibilityNodeInfo): Pair<String, AccessibilityNodeInfo>? {
        // Conservative allowlist: no buy, spend, purchase, upgrade, or offer actions.
        val allowed = listOf("roll", "continue", "claim", "collect", "next", "done", "skip", "close")
        val nodes = ArrayList<AccessibilityNodeInfo>()
        gatherNodes(root, nodes, 0)
        for (node in nodes) {
            val label = (node.text?.toString() ?: node.contentDescription?.toString())
                ?.trim()?.lowercase().orEmpty()
            if (label.isBlank() || label.length > 40) continue
            if (allowed.none { label == it || label.startsWith("$it ") || label.endsWith(" $it") }) continue
            if (node.isVisibleToUser && node.isEnabled) {
                if (node.isClickable) return label to node
                val parent = node.parent
                if (parent != null && parent.isClickable && parent.isEnabled) return label to parent
            }
        }
        return null
    }

    private fun gatherNodes(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (depth > 12 || out.size > 300) return
        out.add(node)
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            gatherNodes(child, out, depth + 1)
        }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): String {
        val out = ArrayList<AccessibilityNodeInfo>()
        gatherNodes(root, out, 0)
        return out.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .joinToString("|") { it.trim().take(100) }
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
        autoButton = null
    }

    override fun onDestroy() {
        autoMode = false
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
