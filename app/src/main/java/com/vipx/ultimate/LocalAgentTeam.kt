package com.vipx.ultimate

data class LocalAgentResult(val agent: String, val finding: String)
data class LocalTeamReport(val agents: List<LocalAgentResult>, val supportedCommand: String) {
    fun asArabicText(): String = buildString {
        append("فريق VIP المحلي • نتائج الوكلاء المستقلين\n\n")
        agents.forEach {
            append("● ").append(it.agent).append("\n")
            append(it.finding).append("\n\n")
        }
        append("الأمر الذي جهّزه الفريق: ").append(supportedCommand)
    }
}
data class LocalActionResult(val success: Boolean, val message: String, val actionName: String)

private data class LocalAgentInput(val packageName: String, val screenText: String, val task: String)
private interface LocalWorker {
    val name: String
    fun inspect(input: LocalAgentInput): String
}

private object ScreenMonitorAgent : LocalWorker {
    override val name = "مساعد مراقبة الشاشة"
    override fun inspect(input: LocalAgentInput): String {
        val text = input.screenText.trim()
        val count = text.split('|').count { it.isNotBlank() }
        return if (text.isBlank()) "لا يوجد نص شاشة متاح. فعّل موافقة قراءة الشاشة وافتح التطبيق المحدد."
        else "التقط سياقاً من الشاشة الحالية: ${count} مقطعاً نصياً و${text.length} حرفاً."
    }
}

private object AppIdentityAgent : LocalWorker {
    override val name = "مساعد هوية التطبيق"
    override fun inspect(input: LocalAgentInput): String =
        if (input.packageName.isBlank()) "لم يُحدد التطبيق المستهدف."
        else "تحقق من أن الجلسة موجهة إلى الحزمة ${input.packageName}."
}

private object ElementAnalysisAgent : LocalWorker {
    override val name = "مساعد تحليل العناصر"
    override fun inspect(input: LocalAgentInput): String {
        val labels = input.screenText.split('|').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return if (labels.isEmpty()) "لم تظهر تسميات نصية قابلة للتحليل."
        else "العناصر النصية التي أمكن قراءتها: " + labels.take(10).joinToString("، ")
    }
}

private object TaskPlannerAgent : LocalWorker {
    override val name = "مساعد تخطيط المهمة"
    override fun inspect(input: LocalAgentInput): String {
        val command = describeLocalCommand(input.task)
        return if (command == "غير مدعوم") "لم يتعرف على أمر تنقل محلي مدعوم؛ لم ينفذ أي إجراء."
        else "حوّل طلب المستخدم إلى إجراء محدد: $command"
    }
}

private object NavigationAgent : LocalWorker {
    override val name = "مساعد التنقل"
    override fun inspect(input: LocalAgentInput): String =
        "أدوات التنقل المتاحة: رجوع النظام، تمرير للأعلى/الأسفل، والنقر على نص ظاهر يحدده المستخدم."
}

private object SafetyAgent : LocalWorker {
    override val name = "مساعد الحماية"
    override fun inspect(input: LocalAgentInput): String =
        if (containsSensitiveIntent(input.task)) "رفض المهمة: رُصد طلب يتصل بالدفع أو الإرسال أو الحذف أو بيانات الاعتماد."
        else "لم يرصد في صياغة المهمة طلباً حساساً. تبقى الإجراءات الحساسة خارج التنفيذ الآلي."
}

private object PrivacyAgent : LocalWorker {
    override val name = "مساعد الخصوصية"
    override fun inspect(input: LocalAgentInput): String =
        if (input.screenText.isBlank()) "لا توجد بيانات شاشة محفوظة في هذه الجولة."
        else "يحلل النص الظاهر المسموح به فقط؛ خدمة الالتقاط تستبعد حقول كلمات المرور وحقول الإدخال."
}

private object VerificationAgent : LocalWorker {
    override val name = "مساعد التحقق"
    override fun inspect(input: LocalAgentInput): String =
        "مرحلة التحليل لا تنفذ إجراءً. بعد طلب التنفيذ تُقارن لقطة الشاشة قبل الإجراء وبعده."
}

private object CoordinatorAgent : LocalWorker {
    override val name = "منسق الفريق"
    override fun inspect(input: LocalAgentInput): String {
        val command = describeLocalCommand(input.task)
        return when {
            containsSensitiveIntent(input.task) -> "أوقف التنفيذ وأبقى التحكم للمستخدم."
            command == "غير مدعوم" -> "اكتمل توزيع التحليل، لكن لا يوجد أمر آمن مدعوم للتنفيذ التلقائي."
            else -> "نسّق نتائج المساعدين وجهّز إجراءً محلياً واحداً: $command"
        }
    }
}

object LocalAgentTeam {
    private val workers: List<LocalWorker> = listOf(
        ScreenMonitorAgent, AppIdentityAgent, ElementAnalysisAgent, TaskPlannerAgent,
        NavigationAgent, SafetyAgent, PrivacyAgent, VerificationAgent, CoordinatorAgent
    )

    fun analyze(packageName: String, screenText: String, task: String): LocalTeamReport {
        val input = LocalAgentInput(packageName, screenText, task)
        val results = workers.map { LocalAgentResult(it.name, it.inspect(input)) }
        val command = if (containsSensitiveIntent(task)) "محظور لأسباب السلامة" else describeLocalCommand(task)
        return LocalTeamReport(results, command)
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
}

private fun describeLocalCommand(task: String): String {
    if (containsSensitiveIntent(task)) return "محظور لأسباب السلامة"
    val value = task.trim().lowercase()
    return when {
        value in listOf("ارجع", "رجوع", "عودة", "back", "go back") -> "رجوع"
        value.contains("انزل") || value.contains("أسفل") || value == "scroll down" -> "تمرير للأسفل"
        value.contains("اصعد") || value.contains("أعلى") || value == "scroll up" -> "تمرير للأعلى"
        Regex("""^(?:اضغط|انقر|اختر|افتح)\s+.+$""", RegexOption.IGNORE_CASE).containsMatchIn(task.trim()) ->
            "نقر على نص ظاهر يحدده المستخدم"
        else -> "غير مدعوم"
    }
}

private fun containsSensitiveIntent(value: String): Boolean {
    val text = value.lowercase()
    val terms = listOf(
        "كلمة المرور", "كلمة السر", "password", "passcode", "otp", "رمز التحقق",
        "الدفع", "اشتر", "شراء", "تحويل", "إرسال", "احذف", "حذف الحساب",
        "تسجيل الخروج", "اشتراك", "pay", "purchase", "delete account", "send message"
    )
    return terms.any { text.contains(it) }
}
