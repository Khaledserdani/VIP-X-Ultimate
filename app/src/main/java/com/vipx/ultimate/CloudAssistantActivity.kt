package com.vipx.ultimate
import android.content.Context
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

@Composable
private fun CloudAssistantScreen(context: Context, onBack: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("vipx_cloud", Context.MODE_PRIVATE) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var accessToken by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
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
                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رابط الخادم HTTPS") }, placeholder = { Text("https://your-worker.example.workers.dev") })
                    OutlinedTextField(accessToken, { accessToken = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز الوصول") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Text("يحفظ التطبيق الرابط فقط. رمز الوصول يبقى في الذاكرة ولا يُحفظ على الهاتف.", color = CloudMuted, fontSize = 10.sp)
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
                        messages.dropLast(1).takeLast(12).forEach { item -> history.put(JSONObject().put("role", item.role).put("content", item.text)) }
                        scope.launch {
                            try {
                                val answer = withContext(Dispatchers.IO) {
                                    val conn = (URL(endpoint.trim().trimEnd('/') + "/api/chat").openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                        setRequestProperty("Authorization", "Bearer " + accessToken)
                                    }
                                    try {
                                        val payload = JSONObject().put("message", question).put("history", history)
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