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
import kotlinx.coroutines.delay
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

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
                primary = LabAccent, background = LabBg, surface = LabPanel,
                onBackground = Color.White, onSurface = Color.White
            )) { CryptoLabScreen() }
        }
    }
}

private data class CryptoTestResult(
    val success: Boolean,
    val attempts: Int,
    val elapsedMs: Long,
    val tamperRejected: Boolean,
    val detail: String
)

private fun runLocalAesGcmTest(): CryptoTestResult {
    val plain = "VIP X LOCAL ENCRYPTION TEST • 10 SECOND SESSION".toByteArray(Charsets.UTF_8)
    val generator = KeyGenerator.getInstance("AES")
    generator.init(256)
    val key = generator.generateKey()
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

    val encryptor = Cipher.getInstance("AES/GCM/NoPadding")
    encryptor.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
    val encrypted = encryptor.doFinal(plain)

    val tampered = encrypted.copyOf()
    tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
    val tamperRejected = try {
        val verifier = Cipher.getInstance("AES/GCM/NoPadding")
        verifier.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        verifier.doFinal(tampered)
        false
    } catch (_: AEADBadTagException) {
        true
    }

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
        if (SystemClock.elapsedRealtime() < deadline) Thread.sleep(10L)
    }

    val elapsed = SystemClock.elapsedRealtime() - started
    val success = verified && tamperRejected
    return CryptoTestResult(
        success, attempts, elapsed, tamperRejected,
        if (success) "تم فك الرسالة الأصلية، ورُفضت الرسالة التي عُبث بها."
        else "لم يكتمل التحقق من فك الرسالة أو كشف العبث."
    )
}

@Composable
private fun CryptoLabScreen() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<CryptoTestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        if (running) {
            for (second in 10 downTo 1) {
                secondsLeft = second
                delay(1_000L)
            }
            secondsLeft = 0
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = LabBg) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VIP X", color = LabAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("مختبر التشفير", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("اختبار محلي بمفتاح مؤقت ينشئه المختبر، دون خدمات مدفوعة أو اتصال خارجي.", color = LabMuted, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = LabPanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المحلّل الأول • سلامة التشفير", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("AES-256-GCM: يتحقق من سلامة الرسالة ويرفض البيانات التي تم العبث بها.", color = LabMuted, fontSize = 13.sp)
                    HorizontalDivider(color = Color(0xFF29415B))
                    Text("المحلّل الثاني • فك التشفير لمدة 10 ثوانٍ", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("ينشئ بيانات مشفّرة ومفتاحها، ثم ينفذ فكاً فعلياً متكرراً ويتحقق من تطابق الناتج.", color = LabMuted, fontSize = 13.sp)
                    Button(
                        onClick = {
                            result = null
                            error = null
                            secondsLeft = 10
                            running = true
                            scope.launch {
                                try {
                                    result = withContext(Dispatchers.IO) { runLocalAesGcmTest() }
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
                Text("الوقت المتبقي: ${secondsLeft} ثوانٍ", color = LabAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("يجري فك بيانات المختبر والتحقق من سلامتها…", color = LabMuted, fontSize = 13.sp)
            }

            result?.let { r ->
                Card(colors = CardDefaults.cardColors(containerColor = LabPanel)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (r.success) "نجح فك تشفير بيانات الاختبار" else "فشل الاختبار",
                            color = if (r.success) LabAccent else Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold
                        )
                        Text("المدة: ${r.elapsedMs} ms", color = Color.White)
                        Text("عدد المحاولات: ${r.attempts}", color = Color.White)
                        Text("كشف العبث: ${if (r.tamperRejected) "ناجح" else "فشل"}", color = Color.White)
                        Text(r.detail, color = LabMuted, fontSize = 13.sp)
                    }
                }
            }

            error?.let { Text("خطأ: $it", color = Color(0xFFFF8A80), fontSize = 12.sp) }
            Text(
                "هذا يثبت فك تشفير بيانات الاختبار ومفتاحها. لا يفتح بث تطبيق آخر ولا يتجاوز DRM أو مفاتيحاً لا نملكها.",
                color = LabMuted, fontSize = 11.sp
            )
        }
    }
}
