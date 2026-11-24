package com.vipx.ultimate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.accessibilityservice.GestureDescription
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VipAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var teamPanel: View? = null
    private var screenTextView: TextView? = null
    private var resultTextView: TextView? = null
    private var taskInput: EditText? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubbleIfAllowed()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (bubble == null && Settings.canDrawOverlays(this)) showBubbleIfAllowed()
        if (event == null || event.packageName?.toString() == packageName) return
        val livePrefs = getSharedPreferences("vipx_live_context", MODE_PRIVATE)
        val target = livePrefs.getString("target_package", "").orEmpty()
        val eventPackage = event.packageName?.toString().orEmpty()
        if (target.isNotBlank() && eventPackage != target) return
        if (!livePrefs.getBoolean("capture_enabled", false)) {
            livePrefs.edit().remove("active_package").remove("visible_context").remove("captured_at").apply()
            return
        }
        captureCurrentScreen(eventPackage)
    }

    private fun targetWindowRoot(): AccessibilityNodeInfo? {
        val target = getSharedPreferences("vipx_live_context", MODE_PRIVATE)
            .getString("target_package", "").orEmpty()
        if (target.isBlank()) return rootInActiveWindow
        for (window in windows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() == target) return root
        }
        val activeRoot = rootInActiveWindow
        return activeRoot?.takeIf { it.packageName?.toString() == target }
    }

    private fun captureCurrentScreen(eventPackage: String = ""): String {
        val root = targetWindowRoot() ?: return ""
        val actualPackage = root.packageName?.toString().orEmpty().ifBlank { eventPackage }
        val livePrefs = getSharedPreferences("vipx_live_context", MODE_PRIVATE)
        val target = livePrefs.getString("target_package", "").orEmpty()
        if (target.isNotBlank() && actualPackage.isNotBlank() && actualPackage != target) return ""
        val visibleText = StringBuilder()
        collectVisibleText(root, visibleText, 0)
        val snapshot = visibleText.toString().take(3500)
        if (livePrefs.getBoolean("capture_enabled", false)) {
            livePrefs.edit()
                .putString("active_package", actualPackage)
                .putString("visible_context", snapshot)
                .putLong("captured_at", System.currentTimeMillis()).apply()
        }
        return snapshot
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 18 || out.length >= 3500 || node.isPassword) return
        if (!node.isEditable) {
            val value = node.text?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                if (out.isNotEmpty()) out.append(" | ")
                out.append(value.take(180))
            }
        }
        for (i in 0 until node.childCount) {
            if (out.length >= 3500) break
            collectVisibleText(node.getChild(i), out, depth + 1)
        }
    }

    private fun showBubbleIfAllowed() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return
        val positionPrefs = getSharedPreferences("vipx_overlay_position", MODE_PRIVATE)
        val view = TextView(this).apply {
            text = "VIP X"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded(0xFF125B56.toInt(), 24)
            elevation = dp(12).toFloat()
            setOnClickListener {
                if (teamPanel == null) showTeamPanel()
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = positionPrefs.getInt("x", dp(12)).coerceAtLeast(0)
            y = positionPrefs.getInt("y", 0)
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = params.x
        var startY = params.y
        var dragging = false

        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxX = (resources.displayMetrics.widthPixels - touchedView.width).coerceAtLeast(0)
                        val maxY = ((resources.displayMetrics.heightPixels - touchedView.height) / 2).coerceAtLeast(0)
                        params.x = (startX - deltaX).toInt().coerceIn(0, maxX)
                        params.y = (startY + deltaY).toInt().coerceIn(-maxY, maxY)
                        runCatching { windowManager?.updateViewLayout(touchedView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        positionPrefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    } else {
                        touchedView.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        runCatching { windowManager?.addView(view, params); bubble = view }
    }

    private fun showTeamPanel() {
        if (teamPanel != null || windowManager == null) return
        val prefs = getSharedPreferences("vipx_live_context", MODE_PRIVATE)
        val targetPackage = prefs.getString("target_package", "").orEmpty()
        val density = resources.displayMetrics.density
        fun d(value: Int) = (value * density).toInt()
        fun text(value: String, size: Float = 13f, color: Int = android.graphics.Color.WHITE) =
            TextView(this).apply { this.text = value; textSize = size; setTextColor(color); setPadding(d(5), d(5), d(5), d(5)) }
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { action() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(14), d(12), d(14), d(12))
            background = rounded(0xFF071B2B.toInt(), 20)
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text("فريق VIP المستقل", 18f, 0xFF43E0C6.toInt()),
            LinearLayout.LayoutParams(0, d(44), 1f))
        heading.addView(button("إغلاق") { hideTeamPanel() })
        content.addView(heading)
        content.addView(button("إعداد الذكاء المحلي والسحابي") {
            runCatching {
                startActivity(Intent(this, CloudAssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                resultTextView?.text = "تعذر فتح إعدادات الذكاء: " + (it.message ?: "خطأ")
            }
        })

        val appLabel = if (targetPackage.isBlank()) "لم يُحفظ تطبيق مستهدف بعد" else "التطبيق: $targetPackage"
        content.addView(text(appLabel, 10f, 0xFFA8B9CA.toInt()))
        val consent = CheckBox(this).apply {
            text = "أسمح للفريق بقراءة النص الظاهر في التطبيق المحدد"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            isChecked = prefs.getBoolean("capture_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("capture_enabled", checked).apply()
                if (!checked) {
                    prefs.edit().remove("active_package").remove("visible_context").remove("captured_at").apply()
                    screenTextView?.text = "قراءة الشاشة متوقفة."
                } else {
                    val captured = captureCurrentScreen()
                    screenTextView?.text = captured.ifBlank { "افتح التطبيق المستهدف ليتم التقاط نص الشاشة." }
                }
            }
        }
        content.addView(consent)

        content.addView(text("سياق الشاشة الحالية", 12f, 0xFF43E0C6.toInt()))
        val screenView = text("فعّل موافقة قراءة الشاشة ثم افتح التطبيق المستهدف.", 11f, 0xFFD4E1EA.toInt())
        screenView.maxLines = 6
        screenTextView = screenView
        content.addView(screenView)

        content.addView(text("المهمة أو الأمر", 12f, 0xFF43E0C6.toInt()))
        val input = EditText(this).apply {
            hint = "مثال: انزل للأسفل أو اضغط الإعدادات"
            setHintTextColor(0xFF91A6B8.toInt())
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            minLines = 1
            maxLines = 2
            background = rounded(0xFF173249.toInt(), 12)
            setPadding(d(10), d(8), d(10), d(8))
        }
        taskInput = input
        content.addView(input, LinearLayout.LayoutParams(-1, d(58)))

        val output = text("الفريق جاهز. حلّل الشاشة أولاً أو أدخل أمراً واضحاً.", 11f, 0xFFD4E1EA.toInt())
        output.setPadding(d(8), d(8), d(8), d(8))
        resultTextView = output
        content.addView(output)

        content.addView(button("حدّث سياق الشاشة") {
            val captured = captureCurrentScreen()
            screenView.text = captured.ifBlank {
                if (!prefs.getBoolean("capture_enabled", false)) "فعّل موافقة قراءة الشاشة أولاً."
                else "لم يصل نص من التطبيق المستهدف بعد."
            }
        })
        content.addView(button("شغّل فريق التحليل المحلي الأساسي") {
            val captured = if (prefs.getBoolean("capture_enabled", false)) captureCurrentScreen() else ""
            val pkg = prefs.getString("active_package", targetPackage).orEmpty()
            val report = LocalAgentTeam.analyze(pkg, captured, input.text.toString())
            output.text = report.asArabicText()
            screenView.text = captured.ifBlank { "لا يوجد نص شاشة متاح حالياً." }
        })
        content.addView(button("حلّل المهمة بنموذج Qwen المحلي") {
            val goal = input.text.toString().trim()
            if (goal.isBlank()) {
                output.text = "اكتب المهمة أولاً."
            } else {
                output.text = "جارٍ تهيئة نموذج Qwen المحفوظ وتشغيل الاستدلال على الهاتف..."
                serviceScope.launch {
                    try {
                        check(LocalLlmRuntime.isModelDownloaded(this@VipAccessibilityService)) {
                            "نموذج الذكاء غير موجود بعد. افتح إعداد الذكاء المحلي من الزر أعلى اللوحة ونزّله مرة واحدة."
                        }
                        if (!LocalLlmRuntime.isReady()) LocalLlmRuntime.initialize(this@VipAccessibilityService)
                        val captured = if (prefs.getBoolean("capture_enabled", false)) captureCurrentScreen() else ""
                        val prompt = "أنت مساعد VIP X داخل تطبيق Android. حلّل المهمة بالعربية، واستعمل سياق الشاشة فقط إن توفر. لا تدّع تنفيذ إجراء. اقترح خطوات آمنة واطلب الموافقة قبل النقر أو أي تغيير.\\nالمهمة: $goal\\nالتطبيق: $targetPackage\\nنص الشاشة: $captured"
                        output.text = LocalLlmRuntime.generate(prompt)
                        screenView.text = captured.ifBlank { "لم تُفعّل قراءة الشاشة؛ جرى التحليل من وصف المهمة فقط." }
                    } catch (error: Exception) {
                        output.text = "تعذر تشغيل الذكاء المحلي: " + (error.message ?: "خطأ غير معروف")
                    }
                }
            }
        })
        content.addView(button("نفّذ الأمر الآمن داخل التطبيق") {
            val before = if (prefs.getBoolean("capture_enabled", false)) captureCurrentScreen() else ""
            val result = LocalAgentTeam.execute(this, input.text.toString())
            output.text = result.message + "\n\nنتيجة استدعاء Android: " +
                if (result.success) "نجح تنفيذ الطلب مبدئياً؛ جارٍ التحقق من تغير الشاشة..." else "لم ينفذ الإجراء."
            if (result.success) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val after = captureCurrentScreen()
                    val changed = after.isNotBlank() && after != before
                    output.text = result.message + "\n\nالتحقق بعد التنفيذ: " +
                        if (changed) "تغير نص الشاشة بعد الإجراء." else "لم نرصد تغيراً نصياً مؤكداً؛ لا نعلن نجاح المهمة."
                    screenView.text = after.ifBlank { "تعذر قراءة الشاشة بعد التنفيذ." }
                }, 900)
            }
        })
        content.addView(button("رجوع") {
            val ok = goBack()
            output.text = if (ok) "أُرسل أمر الرجوع إلى Android." else "خدمة إمكانية الوصول غير متصلة."
        })

        val scroll = ScrollView(this).apply { addView(content) }
        val width = minOf(resources.displayMetrics.widthPixels - d(24), d(380))
        val params = WindowManager.LayoutParams(
            width, d(520),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            windowManager?.addView(scroll, params)
            teamPanel = scroll
            val stored = prefs.getString("visible_context", "").orEmpty()
            screenView.text = if (prefs.getBoolean("capture_enabled", false) && stored.isNotBlank())
                stored else "فعّل موافقة قراءة الشاشة ثم افتح التطبيق المستهدف."
        }.onFailure {
            resultTextView = null
            screenTextView = null
            taskInput = null
        }
    }

    private fun hideTeamPanel() {
        runCatching { teamPanel?.let { windowManager?.removeView(it) } }
        teamPanel = null
        screenTextView = null
        resultTextView = null
        taskInput = null
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        val node = findNode(targetWindowRoot(), target.trim()) ?: return false
        val clickable = generateSequence(node) { it.parent }.firstOrNull { it.isClickable } ?: node
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun scroll(forward: Boolean): Boolean {
        val root = targetWindowRoot() ?: return false
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
        serviceScope.cancel()
        if (active === this) active = null
        hideTeamPanel()
        runCatching { bubble?.let { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var active: VipAccessibilityService? = null
        fun tapText(text: String): Boolean = active?.tapVisibleText(text) ?: false
        fun scrollPage(forward: Boolean): Boolean = active?.scroll(forward) ?: false
        fun back(): Boolean = active?.goBack() ?: false
        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean =
            active?.swipeScreen(x1, y1, x2, y2, durationMs) ?: false
        fun isActive(): Boolean = active != null
    }
}
