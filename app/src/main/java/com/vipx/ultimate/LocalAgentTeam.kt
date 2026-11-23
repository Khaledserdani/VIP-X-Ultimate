package com.vipx.ultimate

/** Independent, deterministic on-device agents; no server or API key is used. */
data class LocalAgentResult(val agent: String, val finding: String)

data class LocalTeamReport(val agents: List<LocalAgentResult>, val supportedCommand: String) {
    fun asArabicText(): String = buildString {
        append("فريق VIP المحلي • نتيجة تشغيل فعلية\n\n")
        agents.forEach {
            append("● ").append(it.agent).append("\n")
            append(it.finding).append("\n\n")
        }
        append("الأمر المدعوم: ").append(supportedCommand)
    }
}

data class LocalActionResult(val success: Boolean, val message: String, val actionName: String)

object LocalAgentTeam {
    private val sensitiveTerms = listOf(
        "كلمة المرور", "كلمة السر", "password", "passcode", "otp", "رمز التحقق",
        "الدفع", "اشتر", "شراء", "تحويل", "إرسال", "احذف", "حذف الحساب",
        "تسجيل الخروج", "اشتراك", "pay", "purchase", "delete account", "send message"
    )

    fun analyze(packageName: String, screenText: String, task: String): LocalTeamReport {
        val cleanText = screenText.trim()
        val labels = cleanText.split('|').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val safe = !containsSensitiveIntent(task)
        val command = supportedCommand(task)
        val agents = listOf(
            LocalAgentResult("مساعد مراقبة الشاشة",
                if (cleanText.isBlank()) "لا يوجد نص شاشة متاح. فعّل موافقة قراءة الشاشة وافتح التطبيق المحدد."
                else "استقبل ${labels.size} مقطعاً نصياً من الشاشة الحالية، بإجمالي ${cleanText.length} حرفاً."),
            LocalAgentResult("مساعد تحديد التطبيق",
                if (packageName.isBlank()) "لم يُحدد التطبيق المستهدف." else "الجلسة مرتبطة بالحزمة: $packageName"),
            LocalAgentResult("مساعد فهم العناصر",
                if (labels.isEmpty()) "لم تظهر تسميات نصية قابلة للتحليل."
                else "أبرز العناصر الظاهرة: " + labels.take(8).joinToString("، ")),
            LocalAgentResult("مساعد تخطيط المهمة",
                if (command == "غير مدعوم") "لم يتعرف على أمر تنقل محلي مدعوم؛ لم ينفذ أي إجراء."
                else "تعرف على أمر محلي قابل للتنفيذ: $command"),
            LocalAgentResult("مساعد الحماية",
                if (safe) "لم يرصد طلباً حساساً في صياغة المهمة. تبقى الإجراءات الحساسة محظورة."
                else "أوقف المهمة لأنها تتضمن دفعاً أو إرسالاً أو حذفاً أو بيانات اعتماد."),
            LocalAgentResult("مساعد التحقق",
                "لم يُنفذ أي إجراء أثناء التحليل. سيُقارن نص الشاشة قبل التنفيذ وبعده عند طلب تنفيذ أمر مدعوم.")
        )
        return LocalTeamReport(agents, if (safe) command else "محظور لأسباب السلامة")
    }

    fun execute(service: VipAccessibilityService, task: String): LocalActionResult {
        if (task.isBlank()) return LocalActionResult(false, "اكتب أمراً واضحاً أولاً.", "لا شيء")
        if (containsSensitiveIntent(task)) {
            return LocalActionResult(false, "رفض الفريق التنفيذ: المهمة تتضمن إجراءً حساساً يتطلب تدخلاً يدوياً.", "حماية")
        }
        val normalized = task.trim().lowercase()
        return when {
            normalized in listOf("ارجع", "رجوع", "عودة", "back", "go back") ->
                LocalActionResult(service.goBack(), "طلب الرجوع أُرسل إلى Android.", "رجوع")
            normalized.contains("انزل") || normalized.contains("أسفل") || normalized == "scroll down" ->
                LocalActionResult(service.scroll(true), "طلب التمرير للأسفل أُرسل إلى التطبيق.", "تمرير للأسفل")
            normalized.contains("اصعد") || normalized.contains("أعلى") || normalized == "scroll up" ->
                LocalActionResult(service.scroll(false), "طلب التمرير للأعلى أُرسل إلى التطبيق.", "تمرير للأعلى")
            else -> {
                val match = Regex("""^(?:اضغط|انقر|اختر|افتح)\s+["«]?(.+?)["»]?$""", RegexOption.IGNORE_CASE).find(task.trim())
                if (match == null) LocalActionResult(false, "الأمر غير مدعوم بعد. جرّب: ارجع، انزل للأسفل، اصعد للأعلى، أو اضغط متبوعاً باسم عنصر ظاهر.", "غير مدعوم")
                else {
                    val label = match.groupValues[1].trim().trim('"', '«', '»', ' ')
                    if (label.isBlank()) LocalActionResult(false, "اكتب اسم العنصر الظاهر بعد كلمة اضغط.", "نقر")
                    else LocalActionResult(service.tapVisibleText(label), "محاولة النقر على العنصر الظاهر: $label", "نقر على عنصر")
                }
            }
        }
    }

    private fun supportedCommand(task: String): String {
        if (containsSensitiveIntent(task)) return "محظور لأسباب السلامة"
        val value = task.trim().lowercase()
        return when {
            value in listOf("ارجع", "رجوع", "عودة", "back", "go back") -> "رجوع"
            value.contains("انزل") || value.contains("أسفل") || value == "scroll down" -> "تمرير للأسفل"
            value.contains("اصعد") || value.contains("أعلى") || value == "scroll up" -> "تمرير للأعلى"
            Regex("""^(?:اضغط|انقر|اختر|افتح)\s+.+$""", RegexOption.IGNORE_CASE).containsMatchIn(task.trim()) -> "نقر على نص ظاهر يحدده المستخدم"
            else -> "غير مدعوم"
        }
    }

    private fun containsSensitiveIntent(value: String): Boolean {
        val text = value.lowercase()
        return sensitiveTerms.any { text.contains(it) }
    }
}
