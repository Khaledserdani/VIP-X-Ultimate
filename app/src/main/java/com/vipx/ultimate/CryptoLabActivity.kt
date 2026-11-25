package com.vipx.ultimate

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

private val LabBg = Color(0xFF07111F)
private val LabPanel = Color(0xFF122238)
private val LabAccent = Color(0xFF42E3C2)
private val LabMuted = Color(0xFF9FB2C7)

class CryptoLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = LabAccent,
                background = LabBg,
                surface = LabPanel,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                CryptoLabScreen()
            }
        }
    }
}

private data class CryptoTestResult(
    val success: Boolean,
    val attempts: Int,
    val elapsedMs: Long,
    val detail: String
)

private fun runLocalAesGcmTest(): CryptoTestResult {
    val plain = "VIP X LOCAL ENCRYPTION TEST • 10 SECOND SESSION".toByteArray(Charsets.UTF_8)
    val keyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256)
    val key = keyGenerator.generateKey()

    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val encryptor = Cipher.getInstance("AES/GCM/NoPadding")
    encryptor.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
    val encrypted = encryptor.doFinal(plain)

    val started = SystemClock.elapsedRealtime()
    val deadline = started + 10_000L
    var attempts = 0
    var verified = false

    while (SystemClock.elapsedRealtime() < deadline) {
        val decryptor = Cipher.getInstance("AES/GCM/NoPadding")
        decryptor.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val recovered = decryptor.doFinal(encrypted)
        attempts++
        verified = recovered.contentEquals(plain)
        if (!verified) break
        if (SystemClock.elapsedRealtime() < deadline) Thread.sleep(20L)
    }

    val elapsed = SystemClock.elapsedRealtime() - started
    return CryptoTestResult(
        success = verified,
        attempts = attempts,
        elapsedMs = elapsed,
        detail = if (verified) "تم فك بيانات الاختبار والتحقق من تطابقها مع الأصل." else "فشل التحقق من البيانات المفكوكة."
    )
}

@Composable
private fun CryptoLabScreen() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CryptoTestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = LabBg) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VIP X", color = LabAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("مختبر التشفير", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("اختبار محلي فعلي لفك تشفير بيانات أنشأها المختبر بمفتاح مؤقت نملكه.", color = LabMuted, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = LabPanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المحلّل الأول • سلامة التشفير", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("AES-256-GCM • التحقق من سلامة الرسالة والوسم ومطابقة البيانات بعد فكها.", color = LabMuted, fontSize = 13.sp)
                    Divider(color = Color(0xFF29415B))
                    Text("المحلّل الثاني • محاولة فك التشفير", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("ينشئ المختبر رسالة مشفّرة ومفتاحاً عشوائياً مؤقتاً، ثم يحاول فكها والتحقق منها لمدة 10 ثوانٍ.", color = LabMuted, fontSize = 13.sp)
                    Button(
                        onClick = {
                            result = null
                            error = null
                            running = true
                            scope.launch {
                                try {
                                    val test = withContext(Dispatchers.IO) { runLocalAesGcmTest() }
                                    result = test
                                } catch (e: Exception) {
                                    error = e.javaClass.simpleName + ": " + (e.message ?: "خطأ غير معروف")
                                } finally {
                                    running = false
                                }
                            }
                        },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LabAccent, contentColor = LabBg)
                    ) {
                        Text(if (running) "الاختبار جارٍ • 10 ثوانٍ" else "ابدأ اختبار فك التشفير • 10 ثوانٍ", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LabAccent)
                Text("يجري اختبار فك بيانات المختبر والتحقق من سلامتها…", color = LabMuted, fontSize = 13.sp)
            }

            result?.let { r ->
                Card(colors = CardDefaults.cardColors(containerColor = LabPanel)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (r.success) "نجح فك تشفير بيانات الاختبار" else "فشل الاختبار", color = if (r.success) LabAccent else Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                        Text("المدة: ${r.elapsedMs} ms", color = Color.White)
                        Text("عدد المحاولات: ${r.attempts}", color = Color.White)
                        Text(r.detail, color = LabMuted, fontSize = 13.sp)
                    }
                }
            }

            error?.let { Text("خطأ: $it", color = Color(0xFFFF8A80), fontSize = 12.sp) }

            Text("ملاحظة: هذا يثبت فك تشفير حقيقي لبيانات الاختبار ومفتاحها. لا يفتح بث تطبيق آخر ولا يتجاوز DRM أو مفاتيح لا نملكها.", color = LabMuted, fontSize = 11.sp)
        }
    }
}
