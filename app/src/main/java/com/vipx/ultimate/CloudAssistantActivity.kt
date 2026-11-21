package com.vipx.ultimate
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


@Composable
private fun LocalAiModelCard(context: Context, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf(LocalLlmRuntime.isModelDownloaded(context)) }
    var ready by remember { mutableStateOf(LocalLlmRuntime.isReady()) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF173A3D)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("الذكاء المحلي الاحتياطي", color = CloudAccent, fontWeight = FontWeight.Bold)
            Text(
                "نموذج Qwen3 صغير يعمل داخل الهاتف دون مفتاح API أو خادم. التنزيل لمرة واحدة بحجم 614 ميغابايت تقريباً، وبعدها يعمل دون إنترنت.",
                color = CloudMuted, fontSize = 11.sp
            )
            if (downloading) LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
            Text(
                when {
                    ready -> "جاهز • يعمل محلياً على الهاتف"
                    downloading -> "تنزيل النموذج: $progress%"
                    downloaded -> "النموذج موجود • يحتاج إلى التهيئة"
                    else -> "لم يتم تنزيل النموذج بعد"
                },
                color = if (ready) CloudAccent else CloudMuted, fontSize = 11.sp
            )
            Button(
                enabled = !downloading && !ready,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        downloading = true
                        try {
                            onStatus("جارٍ التحقق من نموذج الذكاء المحلي أو استكمال تنزيله...")
                            LocalLlmRuntime.downloadModel(context) { progress = it }
                            downloaded = true
                            onStatus("تم تنزيل النموذج والتحقق من سلامته؛ جارٍ تشغيله...")
                            LocalLlmRuntime.initialize(context)
                            ready = true
                            onStatus("الذكاء المحلي جاهز • لن يحتاج إلى الإنترنت بعد التنزيل")
                        } catch (error: Exception) {
                            onStatus("تعذر إعداد الذكاء المحلي: " + (error.message ?: "خطأ"))
                        } finally {
                            downloading = false
                        }
                    }
                }
            ) {
                Text(when {
                    ready -> "الذكاء المحلي جاهز"
                    downloaded -> "تهيئة النموذج المحلي"
                    else -> "تنزيل وتشغيل النموذج المجاني"
                })
            }
        }
    }
}

private suspend fun runLocalAssistant(context: Context, prompt: String): String {
    check(LocalLlmRuntime.isModelDownloaded(context)) {
        "لم يتم تنزيل نموذج الذكاء المحلي بعد. نزّله من بطاقة الذكاء المحلي أولاً."
    }
    if (!LocalLlmRuntime.isReady()) LocalLlmRuntime.initialize(context)
    return LocalLlmRuntime.generate(prompt)
}

private val CloudNavy = Color(0xFF081421)
private val CloudPanel = Color(0xFF122437)
private val CloudAccent = Color(0xFF36D6C5)
private val CloudMuted = Color(0xFF9EB1C4)

class CloudAssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = CloudAccent, background = CloudNavy, surface = CloudPanel)) {
            CloudAssistantScreen(this, onBack = { finish() })
        } }
    }
}

private data class ChatMessage(val role: String, val text: String)
private data class InAppAction(val type: String, val target: String, val reason: String)
private data class AgentReport(val name: String, val result: String)

@Composable
private fun CloudAssistantScreen(context: Context, onBack: () -> Unit) {
    var endpoint by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf(SecureTokenStore.load(context)) }
    var prompt by remember { mutableStateOf("") }
    var actionText by remember { mutableStateOf("") }
    var task by remember { mutableStateOf("") }
    var attachedFileName by remember { mutableStateOf("") }
    var attachedFileText by remember { mutableStateOf("") }
    var planSummary by remember { mutableStateOf("") }
    val agentReports = remember { mutableStateListOf<AgentReport>() }
    val plannedActions = remember { mutableStateListOf<InAppAction>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أدخل رابط الخادم السحابي ورمز الوصول") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("تعذر فتح الملف")
                    if (bytes.size > 1_000_000) throw IllegalStateException("الملف أكبر من حد القراءة 1 ميغابايت")
                    String(bytes, Charsets.UTF_8)
                }
                attachedFileName = uri.lastPathSegment?.substringAfterLast("/") ?: "ملف مرفق"
                attachedFileText = content
                status = "تمت قراءة الملف محلياً: " + attachedFileName
            } catch (e: Exception) { status = "تعذر قراءة الملف: " + (e.message ?: "خطأ") }
        }
    }
    Surface(Modifier.fillMaxSize(), color = CloudNavy) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع", tint = CloudAccent) }
                Column(Modifier.weight(1f)) {
                    Text("VIP X • الذكاء السحابي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("مساعد متصل بخادمك الخاص", color = CloudMuted, fontSize = 11.sp)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = CloudPanel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رابط خادم اختياري") }, placeholder = { Text("https://your-server.example") })
                    OutlinedTextField(accessToken, { accessToken = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز الوصول") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Text("هذه خدمة خارجية اختيارية وقد تكون محدودة الحصة أو مدفوعة. لا تستخدمها قبل التحقق من شروط المزود والاستضافة. المختبر المحلي لا يحتاج إلى خادم أو رمز.", color = CloudMuted, fontSize = 10.sp)
                    Text(status, color = CloudAccent, fontSize = 11.sp)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.takeLast(20).forEach { msg ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (msg.role == "user") Color(0xFF173A4A) else CloudPanel)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (msg.role == "user") "أنت" else "المساعد السحابي", color = CloudAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(msg.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF173A3D)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("أدوات التفاعل مع التطبيق (اختيارية)", color = CloudAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(if (VipAccessibilityService.isActive()) "خدمة التنفيذ متصلة" else "فعّل خدمة VIP X من إعدادات إمكانية الوصول أولاً", color = CloudMuted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { status = if (VipAccessibilityService.back()) "تم الرجوع داخل التطبيق" else "خدمة التنفيذ غير متصلة" }, modifier = Modifier.weight(1f)) { Text("رجوع") }
                        OutlinedButton(onClick = { status = if (VipAccessibilityService.scrollPage(true)) "تم التمرير للأسفل" else "لم أجد مساحة قابلة للتمرير" }, modifier = Modifier.weight(1f)) { Text("أسفل") }
                        OutlinedButton(onClick = { status = if (VipAccessibilityService.scrollPage(false)) "تم التمرير للأعلى" else "لم أجد مساحة قابلة للتمرير" }, modifier = Modifier.weight(1f)) { Text("أعلى") }
                    }
                    OutlinedTextField(actionText, { actionText = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("النص المطابق للزر أو العنصر") })
                    Button(onClick = {
                        status = if (VipAccessibilityService.tapText(actionText)) "نُفذ النقر على: " + actionText
                        else "لم أعثر على عنصر مطابق أو أن التطبيق منع النقر"
                    }, enabled = actionText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("نفّذ النقر على العنصر") }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF173A3D)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("فريق VIP X • تنفيذ مهام متنوعة", color = CloudAccent, fontWeight = FontWeight.Bold)
                    OutlinedTextField(task, { task = it }, Modifier.fillMaxWidth(), label = { Text("اكتب أي مهمة تريد من فريق VIP X إنجازها") }, minLines = 2, maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml")) }) { Text("إرفاق ملف نصي") }
                        if (attachedFileName.isNotBlank()) Text("مرفق: " + attachedFileName, color = CloudMuted, fontSize = 10.sp)
                    }
                    Button(onClick = {
                        val goal = task.trim()
                        val live = context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                        val screen = live.getString("visible_context", "").orEmpty()
                        val pkg = live.getString("active_package", "").orEmpty()
                        if (goal.isBlank()) status = "اكتب المهمة أولاً"
                        
                        else if (!endpoint.startsWith("https://") || accessToken.isBlank()) status = "أدخل رابط الخادم ورمز الربط الصحيح"
                        else {
                            busy = true; status = "VIP X يحلل المهمة ويحدد ما يلزم..."
                            plannedActions.clear(); agentReports.clear(); planSummary = ""
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val conn = (URL(endpoint.trim().trimEnd('/') + "/api/mission").openConnection() as HttpURLConnection).apply {
                                            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 120000; doOutput = true
                                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                            setRequestProperty("Authorization", "Bearer " + accessToken)
                                        }
                                        try {
                                            val payload = JSONObject().put("task", goal).put("appName", if (pkg.isBlank()) "مهمة عامة" else pkg.substringAfterLast('.')).put("packageName", pkg).put("context", screen + if (attachedFileText.isNotBlank()) "\n\nمحتوى الملف " + attachedFileName + ":\n" + attachedFileText else "").put("capabilities", "Android UI context, authorized tap/scroll/back, chat, analysis and configured cloud tools")
                                            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                            val code = conn.responseCode
                                            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                                            val body = stream.bufferedReader().use { it.readText() }
                                            if (code !in 200..299) {
                                                val detail = runCatching { JSONObject(body).optString("error").takeIf { it.isNotBlank() } }.getOrNull()
                                                throw IllegalStateException(detail ?: ("HTTP " + code))
                                            }
                                            JSONObject(body)
                                        } finally { conn.disconnect() }
                                    }
                                    planSummary = result.optString("decision", "اكتمل تحليل الفريق")
                                    val reports = result.optJSONArray("team") ?: JSONArray()
                                    for (i in 0 until reports.length()) { val a = reports.getJSONObject(i); agentReports.add(AgentReport(a.optString("name"), a.optString("result"))) }
                                    val executions = result.optJSONArray("execution") ?: JSONArray()
                                    for (i in 0 until executions.length()) { agentReports.add(AgentReport("أداة نُفذت فعلياً", executions.getJSONObject(i).toString(2))) }
                                    val list = result.optJSONArray("actions") ?: JSONArray()
                                    for (i in 0 until list.length()) {
                                        val a = list.getJSONObject(i)
                                        plannedActions.add(InAppAction(a.optString("type"), a.optString("target"), a.optString("reason")))
                                    }
                                    status = "اكتمل تحليل المهمة • راجع النتيجة أدناه"
                                    messages.add(ChatMessage("assistant", "خطة الفريق: " + planSummary + "\\nعدد الإجراءات: " + plannedActions.size))
                                } catch (e: Exception) {
                                    try {
                                        val localPrompt = """
                                            أنت مخطط مهام محلي داخل VIP X. لا يوجد إنترنت ولا أدوات خارجية.
                                            حلّل المهمة والسياق، ثم أعد JSON فقط بلا Markdown بهذا الشكل:
                                            {"decision":"ملخص الخطة","team":[{"name":"المحلل المحلي","result":"التحليل"}],"actions":[{"type":"TAP_TEXT|OPEN_URL|SHARE_TEXT|SCROLL_FORWARD|SCROLL_BACK|BACK","target":"النص أو الرابط عند الحاجة","reason":"سبب الخطوة"}]}
                                            لا تخترع عناصر شاشة غير موجودة. TAP_TEXT مسموح فقط إذا كان النص ظاهراً حرفياً في السياق.
                                            لا تنفذ أي شيء؛ اعرض خطوات مقترحة فقط، فالمستخدم سيوافق على كل خطوة بنفسه.
                                            المهمة: $goal
                                            التطبيق: $pkg
                                            سياق الشاشة: $screen
                                            الملف المرفق: $attachedFileText
                                        """.trimIndent()
                                        val raw = runLocalAssistant(context, localPrompt)
                                        val start = raw.indexOf('{')
                                        val end = raw.lastIndexOf('}')
                                        require(start >= 0 && end > start) { "النموذج المحلي لم يُرجع خطة JSON صالحة: " + raw.take(240) }
                                        val localResult = JSONObject(raw.substring(start, end + 1))
                                        planSummary = localResult.optString("decision", "خطة محلية")
                                        val reports = localResult.optJSONArray("team") ?: JSONArray()
                                        for (i in 0 until reports.length()) {
                                            val item = reports.getJSONObject(i)
                                            agentReports.add(AgentReport(item.optString("name", "المحلل المحلي"), item.optString("result")))
                                        }
                                        val actions = localResult.optJSONArray("actions") ?: JSONArray()
                                        val allowed = setOf("OPEN_URL", "SHARE_TEXT", "TAP_TEXT", "SCROLL_FORWARD", "SCROLL_BACK", "BACK")
                                        for (i in 0 until actions.length()) {
                                            val item = actions.getJSONObject(i)
                                            val type = item.optString("type")
                                            val target = item.optString("target").take(500)
                                            val valid = when (type) {
                                                "TAP_TEXT" -> target.isNotBlank() && screen.contains(target, ignoreCase = true)
                                                "OPEN_URL" -> target.startsWith("https://") || target.startsWith("http://")
                                                "SHARE_TEXT" -> target.isNotBlank()
                                                "SCROLL_FORWARD", "SCROLL_BACK", "BACK" -> true
                                                else -> false
                                            }
                                            if (type in allowed && valid) plannedActions.add(InAppAction(type, target, item.optString("reason").take(300)))
                                        }
                                        status = "تعذر السحاب؛ أُعدّت خطة محلية دون إنترنت • راجع كل خطوة قبل التنفيذ"
                                        messages.add(ChatMessage("assistant", "خطة محلية: " + planSummary + "\\nعدد الخطوات المقترحة: " + plannedActions.size))
                                    } catch (localError: Exception) {
                                        status = "تعذر السحاب والذكاء المحلي: " + (localError.message ?: e.message ?: "خطأ")
                                    }
                                }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "الفريق يعمل..." else "حلّل المهمة مع VIP X") }
                    OutlinedButton(
                        enabled = !busy && task.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val localGoal = task.trim()
                            val live = context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                            val localScreen = live.getString("visible_context", "").orEmpty()
                            val localPackage = live.getString("active_package", "").orEmpty()
                            busy = true
                            status = "جارٍ تحليل المهمة محلياً..."
                            agentReports.clear()
                            plannedActions.clear()
                            scope.launch {
                                try {
                                    val localPrompt = """
                                        حلّل المهمة التالية كمساعد محلي دون إنترنت أو أدوات خارجية.
                                        أعطني خطة واضحة بالعربية: الهدف، المعلومات الناقصة، خطوات آمنة، وما يحتاج موافقة المستخدم.
                                        لا تدّع تنفيذ أي إجراء ولا تخترع حالة غير موجودة.
                                        المهمة: $localGoal
                                        التطبيق: $localPackage
                                        سياق الشاشة: $localScreen
                                        محتوى الملف: $attachedFileText
                                    """.trimIndent()
                                    val result = runLocalAssistant(context, localPrompt)
                                    planSummary = "تحليل محلي مكتمل"
                                    agentReports.add(AgentReport("مخطط VIP X المحلي", result))
                                    status = "اكتمل التحليل محلياً • لا اتصال بخادم"
                                } catch (error: Exception) {
                                    status = error.message ?: "تعذر التحليل المحلي"
                                } finally { busy = false }
                            }
                        }
                    ) { Text("حلّل المهمة محلياً دون إنترنت") }
                    if (planSummary.isNotBlank()) Text(planSummary, color = Color.White, fontSize = 12.sp)
                    agentReports.forEach { report ->
                        Text(report.name, color = CloudAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(report.result, color = Color.White, fontSize = 11.sp)
                    }
                    plannedActions.forEachIndexed { index, action ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${index + 1}. " + when(action.type) {
                                "OPEN_URL" -> "افتح الرابط في المتصفح: " + action.target
                                "SHARE_TEXT" -> "اعرض مشاركة النص: " + action.target.take(90)
                                "TAP_TEXT" -> "انقر على: " + action.target
                                "SCROLL_FORWARD" -> "مرّر للأسفل"
                                "SCROLL_BACK" -> "مرّر للأعلى"
                                "BACK" -> "ارجع خطوة"
                                else -> "إجراء غير معروف"
                            }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (action.reason.isNotBlank()) Text(action.reason, color = CloudMuted, fontSize = 10.sp)
                            Button(onClick = {
                                val ok = when(action.type) {
                                    "OPEN_URL" -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.target))); true }.getOrDefault(false)
                                    "SHARE_TEXT" -> runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.target), "مشاركة عبر")); true }.getOrDefault(false)
                                    "TAP_TEXT" -> VipAccessibilityService.tapText(action.target)
                                    "SCROLL_FORWARD" -> VipAccessibilityService.scrollPage(true)
                                    "SCROLL_BACK" -> VipAccessibilityService.scrollPage(false)
                                    "BACK" -> VipAccessibilityService.back()
                                    else -> false
                                }
                                status = if (ok) "تم تنفيذ الخطوة ${index + 1}" else "تعذر تنفيذ الخطوة ${index + 1}: العنصر غير ظاهر أو الخدمة غير متصلة"
                                if (ok) plannedActions.removeAt(index)
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("وافق ونفّذ هذه الخطوة") }
                        }
                        Divider(color = CloudMuted.copy(alpha = 0.25f))
                    }
                }
            }
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("اكتب سؤالك") }, minLines = 2, maxLines = 4)
            Button(onClick = {
                val question = prompt.trim()
                if (question.isNotEmpty() && !busy) {
                    if (!endpoint.startsWith("https://") || accessToken.isBlank()) status = "تحقق من رابط HTTPS ورمز الوصول"
                    else {
                        prompt = ""
                        messages.add(ChatMessage("user", question))
                        busy = true
                        status = "جارٍ الاتصال بالخادم..."
                        val history = JSONArray()
                        val recentMessages = messages.takeLast(20)
                        val liveContext = context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                            .getString("visible_context", "").orEmpty()
                        val activePackage = liveContext.let {
                            context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                                .getString("active_package", "").orEmpty()
                        }
                        recentMessages.forEachIndexed { index, item ->
                            val content = if (index == recentMessages.lastIndex && item.role == "user" && liveContext.isNotBlank())
                                item.text + "\\n\\n[سياق الشاشة الظاهر من جلسة VIP X للتطبيق " + activePackage + "]\\n" + liveContext
                            else item.text
                            history.put(JSONObject().put("role", item.role).put("content", content))
                        }
                        scope.launch {
                            try {
                                val answer = withContext(Dispatchers.IO) {
                                    val conn = (URL(endpoint.trim().trimEnd('/') + "/api/chat").openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                        setRequestProperty("Authorization", "Bearer " + accessToken)
                                    }
                                    try {
                                        val payload = JSONObject().put("messages", history)
                                        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                                        val body = stream.bufferedReader().use { it.readText() }
                                        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP " + conn.responseCode + ": " + body)
                                        JSONObject(body).optString("reply").ifBlank { "وصل رد فارغ من الخادم" }
                                    } finally { conn.disconnect() }
                                }
                                messages.add(ChatMessage("assistant", answer)); status = "متصل • اكتمل الرد"
                            } catch (e: Exception) {
                                try {
                                    val live = context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                                    val visibleContext = live.getString("visible_context", "").orEmpty()
                                    val packageName = live.getString("active_package", "").orEmpty()
                                    val localPrompt = question + if (visibleContext.isNotBlank())
                                        "\\n\\nالسياق الظاهر محلياً من التطبيق $packageName:\\n$visibleContext"
                                    else ""
                                    val localAnswer = runLocalAssistant(context, localPrompt)
                                    messages.add(ChatMessage("assistant", localAnswer))
                                    status = "السحابة غير متاحة؛ أجاب الذكاء المحلي دون إرسال بياناتك إلى خادم"
                                } catch (localError: Exception) {
                                    status = "تعذر السحاب والبديل المحلي: " + (localError.message ?: e.message ?: "خطأ")
                                }
                            }
                            finally { busy = false }
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "جارٍ الإرسال..." else "إرسال إلى الذكاء السحابي")
            }
            OutlinedButton(
                onClick = {
                    val question = prompt.trim()
                    if (question.isNotEmpty() && !busy) {
                        prompt = ""
                        messages.add(ChatMessage("user", question))
                        busy = true
                        status = "جارٍ تشغيل الذكاء المحلي..."
                        scope.launch {
                            try {
                                val live = context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                                val visibleContext = live.getString("visible_context", "").orEmpty()
                                val packageName = live.getString("active_package", "").orEmpty()
                                val localPrompt = question + if (visibleContext.isNotBlank())
                                    "\\n\\nالسياق الظاهر محلياً من التطبيق $packageName:\\n$visibleContext"
                                else ""
                                val answer = runLocalAssistant(context, localPrompt)
                                messages.add(ChatMessage("assistant", answer))
                                status = "اكتمل الرد محلياً • دون اتصال سحابي"
                            } catch (error: Exception) {
                                status = error.message ?: "تعذر تشغيل الذكاء المحلي"
                            } finally { busy = false }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("إرسال إلى الذكاء المحلي دون إنترنت") }

        }
    }
}