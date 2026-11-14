package com.vipx.ultimate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubbleIfAllowed()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() == packageName) return
        val root = rootInActiveWindow ?: return
        val visibleText = StringBuilder()
        collectVisibleText(root, visibleText, 0)
        getSharedPreferences("vipx_live_context", MODE_PRIVATE).edit()
            .putString("active_package", event.packageName?.toString().orEmpty())
            .putString("visible_context", visibleText.toString().take(3500))
            .putLong("captured_at", System.currentTimeMillis())
            .apply()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 18 || out.length >= 3500) return
        // Never collect password or editable credential fields.
        if (node.isPassword || (node.isEditable && node.text.isNullOrBlank())) return
        val value = node.text?.toString()?.trim().orEmpty()
        if (value.isNotEmpty() && !node.isPassword) {
            if (out.isNotEmpty()) out.append(" | ")
            out.append(value.take(180))
        }
        for (i in 0 until node.childCount) {
            if (out.length >= 3500) break
            collectVisibleText(node.getChild(i), out, depth + 1)
        }
    }

    private fun showBubbleIfAllowed() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return
        val view = TextView(this).apply {
            text = "VIP X"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(28, 18, 28, 18)
            setBackgroundColor(android.graphics.Color.rgb(18, 91, 86))
            elevation = 12f
            setOnClickListener {
                val intent = Intent(this@VipAccessibilityService, CloudAssistantActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 12; y = 0 }
        runCatching { windowManager?.addView(view, params); bubble = view }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { bubble?.let { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
