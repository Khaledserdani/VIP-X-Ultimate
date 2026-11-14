package com.vipx.ultimate
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val prefs = remember { context.getSharedPreferences("vipx_cloud", Context.MODE_PRIVATE) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://vip-x-ultimate-api-production.up.railway.app") ?: "https://vip-x-ultimate-api-production.up.railway.app") }
    var accessToken by remember { mutableStateOf(SecureTokenStore.load(context)) }
    var prompt by remember { mutableStateOf("") }
    var actionText by remember { mutableStateOf("") }
    var task by remember { mutableStateOf("") }
    var planSummary by remember { mutableStateOf("") }
    val agentReports = remember { mutableStateListOf<AgentReport>() }
    val plannedActions = remember { mutableStateListOf<InAppAction>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أدخل رابط الخادم السحابي ورمز الوصول") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = CloudNavy) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع", tint = CloudAccent) }
                Column(Modifier.weight(1f)) {
                    Text("VIP X • الذكاء السحابي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("مساعد متصل بخادمك الخاص", color = CloudMuted, fontSize = 11.sp)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = CloudPanel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رابط الخادم HTTPS") }, placeholder = { Text("https://vip-x-ultimate-api-production.up.railway.app") })
                    OutlinedTextField(accessToken, { accessToken = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز الوصول") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Text("يُحفظ رمز الوصول مشفراً في Android Keystore بعد التحقق منه من الشاشة الرئيسية.", color = CloudMuted, fontSize = 10.sp)
                    Text(status, color = CloudAccent, fontSize = 11.sp)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg ->
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
                    Button(onClick = {
                        val goal = task.trim()
                        val live = context.getSharedPreferences("vipx_live_context", Context.MODE_PRIVATE)
                        val screen = live.getString("visible_context", "").orEmpty()
                        val pkg = live.getString("active_package", "").orEmpty()
                        if (goal.isBlank()) status = "اكتب المهمة أولاً"
                        
                        else if (!endpoint.startsWith("https://") || accessToken.isBlank()) status = "أدخل رابط الخادم ورمز الربط الصحيح"
                        else {
                            busy = true; status = "المساعدون الأربعة يدرسون المهمة ويبنون خطة..."
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
                                            val payload = JSONObject().put("task", goal).put("appName", if (pkg.isBlank()) "مهمة عامة" else pkg.substringAfterLast('.')).put("packageName", pkg).put("context", screen).put("capabilities", "Android UI context, authorized tap/scroll/back, chat, analysis and configured cloud tools")
                                            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                            val code = conn.responseCode
                                            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                                            val body = stream.bufferedReader().use { it.readText() }
                                            if (code !in 200..299) throw IllegalStateException("HTTP " + code + ": " + body)
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
                                    status = "اكتمل تنسيق المساعدين الأربعة • راجع قرار الفريق أدناه"
                                    messages.add(ChatMessage("assistant", "خطة الفريق: " + planSummary + "\\nعدد الإجراءات: " + plannedActions.size))
                                } catch (e: Exception) { status = "فشل إعداد الخطة: " + (e.message ?: "خطأ غير معروف") }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "الفريق يعمل..." else "اطلب من الفريق إعداد خطة وتنفيذها") }
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
                        prefs.edit().putString("endpoint", endpoint.trim().trimEnd('/')).apply()
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
                            } catch (e: Exception) { status = "تعذر الاتصال: " + (e.message ?: "تحقق من الخادم والإنترنت") }
                            finally { busy = false }
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "جارٍ الإرسال..." else "إرسال إلى الذكاء السحابي")
            }
        }
    }
}