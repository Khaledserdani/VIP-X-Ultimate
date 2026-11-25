package com.vipx.ultimate

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaCaptureActivity : ComponentActivity() {
    private var decryptedPreviewFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val candidate = SharedMediaCapture.fromIntent(intent)
        val sharedUris = SharedMediaCapture.sharedContentUris(intent)
        val sourceApp = referrer?.host ?: "تطبيق مشاركة على Android"
        val sharedMime = sharedUris.firstOrNull()?.let { contentResolver.getType(it) }
            ?: intent.type.orEmpty()
        val encryptedMime = sharedUris.firstOrNull()?.let { uri ->
            runCatching {
                contentResolver.openInputStream(uri)?.use { VipxEncryptedMedia.inspect(it).mimeType }
            }.getOrNull()
        }.orEmpty()

        setContent {
            var previewUri by remember(sharedUris, sharedMime) { mutableStateOf(if (sharedMime.startsWith("image/")) sharedUris.firstOrNull() else null) }
            var previewMime by remember(sharedMime) { mutableStateOf(if (sharedMime.startsWith("image/")) sharedMime else "") }
            var password by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var message by remember {
                mutableStateOf(
                    if (encryptedMime.startsWith("image/") || encryptedMime.startsWith("video/"))
                        "تم التعرف على حاوية VIPXENC مشفّرة. أدخل كلمة المرور التي أنشأتها لها."
                    else "استقبل VIP X مشاركة من تطبيق آخر. لا يتم تفتيش ذاكرة الهاتف."
                )
            }
            val scope = rememberCoroutineScope()
            val sharedUri = sharedUris.firstOrNull()

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF42E3C2),
                background = Color(0xFF07111F),
                surface = Color(0xFF122238)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF07111F)) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("VIP X • استقبال الوسائط", color = Color(0xFF42E3C2), fontSize = 14.sp)
                        Text("من تطبيقات المشاركة", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)

                        if (candidate != null) {
                            Text("رابط HTTPS من مشاركة المستخدم • ${candidate.host}", color = Color.White)
                            Text(SharedMediaCapture.label(candidate.kind), color = Color(0xFF42E3C2))
                            Text(SharedMediaCapture.safeDisplay(candidate.url), color = Color(0xFF9FB2C7), fontSize = 11.sp)
                            Button(onClick = {
                                startActivity(Intent(this@MediaCaptureActivity, PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_STREAM_URL, candidate.url))
                            }, modifier = Modifier.fillMaxWidth()) { Text("جرّب تشغيل الرابط") }
                            OutlinedButton(onClick = {
                                startActivity(Intent(this@MediaCaptureActivity, LiveGridActivity::class.java)
                                    .putExtra(LiveGridActivity.EXTRA_IMPORTED_STREAM_URL, candidate.url))
                            }, modifier = Modifier.fillMaxWidth()) { Text("أضف الرابط إلى شبكة 2×2") }
                        } else if (sharedUri != null) {
                            Text("المصدر: $sourceApp", color = Color.White)
                            Text("عدد العناصر المستلمة: ${sharedUris.size}", color = Color(0xFF9FB2C7))
                            Text("نوع المشاركة: ${if (encryptedMime.isNotBlank()) "VIPXENC مشفّر • $encryptedMime" else sharedMime.ifBlank { "غير محدد" }}",
                                color = Color(0xFF42E3C2))
                            Text(
                                "يصل المحتوى عبر إذن مشاركة مؤقت من التطبيق المصدر. لا نفتح مجلدات التطبيق ولا نحاول استخراج مفاتيح محادثاته.",
                                color = Color(0xFF9FB2C7), fontSize = 12.sp
                            )

                            if (encryptedMime.startsWith("image/") || encryptedMime.startsWith("video/")) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("كلمة مرور VIPXENC") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    enabled = !busy
                                )
                                Button(
                                    onClick = {
                                        val secret = password.toCharArray()
                                        busy = true
                                        message = "يجري فك التشفير والتحقق من سلامة الملف…"
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    val source = contentResolver.openInputStream(sharedUri)
                                                        ?: throw IllegalStateException("تعذر قراءة المشاركة")
                                                    val outputFile = File.createTempFile("vipx-shared-", ".tmp", cacheDir)
                                                    try {
                                                        val mime = source.use { input ->
                                                            outputFile.outputStream().buffered().use { output ->
                                                                VipxEncryptedMedia.decrypt(input, output, secret)
                                                            }
                                                        }
                                                        val extension = android.webkit.MimeTypeMap.getSingleton()
                                                            .getExtensionFromMimeType(mime) ?: "media"
                                                        val finalFile = File(cacheDir, "vipx-shared-${System.nanoTime()}.$extension")
                                                        if (!outputFile.renameTo(finalFile)) {
                                                            outputFile.copyTo(finalFile, overwrite = true)
                                                            outputFile.delete()
                                                        }
                                                        Pair(Uri.fromFile(finalFile), mime)
                                                    } catch (error: Exception) {
                                                        outputFile.delete()
                                                        throw error
                                                    }
                                                }
                                                decryptedPreviewFile?.delete()
                                                decryptedPreviewFile = File(result.first.path.orEmpty())
                                                previewUri = result.first
                                                previewMime = result.second
                                                message = "تم فك التشفير والتحقق. المعاينة من نسخة مؤقتة خاصة بـ VIP X."
                                            } catch (error: Exception) {
                                                message = "فشل فك التشفير أو التحقق: ${error.message ?: error.javaClass.simpleName}"
                                            } finally {
                                                secret.fill('\u0000')
                                                password = ""
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = password.isNotEmpty() && !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (busy) "جارٍ التحقق…" else "فك التشفير وفتح") }
                            } else if (sharedMime.startsWith("video/")) {
                                Button(onClick = { openSharedVideo(sharedUri) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("تشغيل الفيديو المستلم")
                                }
                            } else {
                                Text(
                                    "هذا العنصر ليس صورة أو فيديو واضح النوع، ولم نتعرف عليه كحاوية VIPXENC. لن نخمن طريقة فك تشفيره. إذا كان DRM فالتشغيل يحتاج ترخيص المزود.",
                                    color = Color(0xFFFFD180), fontSize = 12.sp
                                )
                            }

                            if (sharedUris.size > 1) {
                                Text("لضمان عدم خلط الملفات، أرسل كل صورة أو فيديو منفرداً من التطبيق المصدر حالياً.", color = Color(0xFFFFD180), fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                "لم يصل رابط أو ملف مشاركة. افتح الوسيط داخل التطبيق المصدر واختر مشاركة ثم VIP X.",
                                color = Color(0xFF9FB2C7)
                            )
                        }

                        val imageUri = previewUri
                        if (imageUri != null && previewMime.startsWith("image/")) {
                            AndroidView(
                                factory = { context -> ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    adjustViewBounds = true
                                } },
                                update = { it.setImageURI(imageUri) },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        } else if (imageUri != null && previewMime.startsWith("video/")) {
                            Button(onClick = { openSharedVideo(imageUri) }, modifier = Modifier.fillMaxWidth()) {
                                Text("تشغيل الفيديو بعد فك التشفير")
                            }
                        }

                        Text(message, color = Color(0xFF9FB2C7), fontSize = 11.sp)
                        Text(
                            "مشاركة التطبيق لا تعني تجاوز تشفيره. لا يستطيع VIP X فتح محتوى خاص لم يمنح التطبيق المصدر إذن مشاركته.",
                            color = Color.Gray, fontSize = 10.sp
                        )
                        OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
                    }
                }
            }
        }
    }

    private fun openSharedVideo(uri: Uri) {
        val playIntent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_STREAM_URL, uri.toString())
        if (uri.scheme == "content") {
            playIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setClipData(ClipData.newRawUri("VIP X shared media", uri))
        } else if (uri.scheme == "file") {
            // Transfer ownership of a decrypted cache file to PlaybackService.
            // The capture screen may be destroyed while playback continues.
            val candidate = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull()
            val cache = runCatching { cacheDir.canonicalFile }.getOrNull()
            if (candidate != null && cache != null &&
                candidate.parentFile == cache && candidate.name.startsWith("vipx-shared-") &&
                decryptedPreviewFile?.canonicalFile == candidate
            ) {
                decryptedPreviewFile = null
            }
        }
        startActivity(playIntent)
    }

    override fun onDestroy() {
        decryptedPreviewFile?.delete()
        decryptedPreviewFile = null
        super.onDestroy()
    }
}
