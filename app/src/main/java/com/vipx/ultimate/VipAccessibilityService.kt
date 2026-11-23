package com.vipx.ultimate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class VipAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubbleIfAllowed()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() == packageName) return
        val livePrefs = getSharedPreferences("vipx_live_context", MODE_PRIVATE)
        if (!livePrefs.getBoolean("capture_enabled", false)) {
            livePrefs.edit().remove("active_package").remove("visible_context").remove("captured_at").apply()
            return
        }
        val root = rootInActiveWindow ?: return
        val visibleText = StringBuilder()
        collectVisibleText(root, visibleText, 0)
        getSharedPreferences("vipx_live_context", MODE_PRIVATE).edit()
            .putString("active_package", event.packageName?.toString().orEmpty())
            .putString("visible_context", visibleText.toString().take(3500))
            .putLong("captured_at", System.currentTimeMillis()).apply()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 18 || out.length >= 3500 || node.isPassword) return
        if (!node.isEditable) {
            val value = node.text?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) { if (out.isNotEmpty()) out.append(" | "); out.append(value.take(180)) }
        }
        for (i in 0 until node.childCount) {
            if (out.length >= 3500) break
            collectVisibleText(node.getChild(i), out, depth + 1)
        }
    }

    private fun showBubbleIfAllowed() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return
        val view = TextView(this).apply {
            text = "VIP X"; textSize = 14f
            setTextColor(android.graphics.Color.WHITE); setPadding(28, 18, 28, 18)
            setBackgroundColor(android.graphics.Color.rgb(18, 91, 86)); elevation = 12f
            setOnClickListener {
                startActivity(Intent(this@VipAccessibilityService, CloudAssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 12; y = 0 }
        runCatching { windowManager?.addView(view, params); bubble = view }
    }

    private fun findNode(node: AccessibilityNodeInfo?, target: String, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 20 || node.isPassword) return null
        val textLabel = node.text?.toString()?.trim().orEmpty()
        val descriptionLabel = node.contentDescription?.toString()?.trim().orEmpty()
        if (!node.isEditable && (
                textLabel.equals(target, ignoreCase = true) ||
                descriptionLabel.equals(target, ignoreCase = true)
            )
        ) return node
        for (i in 0 until node.childCount) findNode(node.getChild(i), target, depth + 1)?.let { return it }
        return null
    }

    fun tapVisibleText(target: String): Boolean {
        if (target.isBlank()) return false
        val node = findNode(rootInActiveWindow, target.trim()) ?: return false
        val clickable = generateSequence(node) { it.parent }.firstOrNull { it.isClickable } ?: node
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        fun scan(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 18) return null
            if (n.isScrollable && n.performAction(action)) return n
            for (i in 0 until n.childCount) scan(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        return scan(root, 0) != null
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun swipeScreen(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(80, 1200))
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }


    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (active === this) active = null
        runCatching { bubble?.let { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var active: VipAccessibilityService? = null
        fun tapText(text: String): Boolean = active?.tapVisibleText(text) ?: false
        fun scrollPage(forward: Boolean): Boolean = active?.scroll(forward) ?: false
        fun back(): Boolean = active?.goBack() ?: false
        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean = active?.swipeScreen(x1, y1, x2, y2, durationMs) ?: false
        fun isActive(): Boolean = active != null
    }
}
