package com.vipx.ultimate

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.lifecycleScope
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
        VipxMediaCacheMaintenance.cleanupStale(cacheDir)
        val candidate = SharedMediaCapture.fromIntent(intent)
        val sharedUris = SharedMediaCapture.sharedContentUris(intent)
        val sourceApp = referrer?.host ?: "تطبيق مشاركة على Android"
        setContent {
            var previewUri by remember { mutableStateOf<Uri?>(null) }
            var previewMime by remember { mutableStateOf("") }
            var itemMimes by remember { mutableStateOf<List<String>>(emptyList()) }
            var encryptedItems by remember { mutableStateOf<List<Boolean>>(emptyList()) }
            var selectedIndex by remember { mutableIntStateOf(0) }
            var checkingItems by remember { mutableStateOf(sharedUris.isNotEmpty()) }
            var password by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var message by remember {
                mutableStateOf("استقبل VIP X مشاركة من تطبيق آخر. لا يتم تفتيش ذاكرة الهاتف.")
            }
            val scope = rememberCoroutineScope()
            val sharedUri = sharedUris.getOrNull(selectedIndex)
            val currentMime = itemMimes.getOrNull(selectedIndex).orEmpty()
            val currentIsEncrypted = encryptedItems.getOrNull(selectedIndex) == true

            LaunchedEffect(sharedUris) {
                val inspected = withContext(Dispatchers.IO) {
                    sharedUris.map { uri ->
                        val providerMime = contentResolver.getType(uri).orEmpty()
                        val encryptedMime = runCatching {
                            contentResolver.openInputStream(uri)?.use { VipxEncryptedMedia.inspect(it).mimeType }
                        }.getOrNull().orEmpty()
                        if (encryptedMime.startsWith("image/") || encryptedMime.startsWith("video/")) {
                            Pair(encryptedMime, true)
                        } else {
                            Pair(providerMime, false)
                        }
                    }
                }
                itemMimes = inspected.map { it.first }
                encryptedItems = inspected.map { it.second }
                previewUri = if (inspected.firstOrNull()?.first?.startsWith("image/") == true &&
                    inspected.firstOrNull()?.second == false) sharedUris.firstOrNull() else null
                previewMime = if (previewUri != null) inspected.first().first else ""
                checkingItems = false
                message = if (sharedUris.size > 1) {
                    "استُقبلت ${sharedUris.size} عناصر. افحصها بالتتابع من هنا."
                } else {
                    "استقبل VIP X مشاركة من تطبيق آخر. لا يتم تفتيش ذاكرة الهاتف."
                }
            }

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
                        } else if (sharedUri != null) {
                            Text("المصدر: $sourceApp", color = Color.White)
                            Text("العنصر ${selectedIndex + 1} من ${sharedUris.size}", color = Color(0xFF9FB2C7))
                            if (checkingItems) {
                                Text("يجري فحص عناصر المشاركة بالتتابع في الخلفية…", color = Color(0xFF9FB2C7), fontSize = 12.sp)
                            }
                            if (sharedUris.size > 1) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val nextIndex = (selectedIndex - 1).coerceAtLeast(0)
                                            selectedIndex = nextIndex
                                            clearCurrentPreview()
                                            previewUri = sharedUris.getOrNull(nextIndex)?.takeIf {
                                                itemMimes.getOrNull(nextIndex)?.startsWith("image/") == true &&
                                                    encryptedItems.getOrNull(nextIndex) != true
                                            }
                                            previewMime = itemMimes.getOrNull(nextIndex).orEmpty()
                                            password = ""
                                        },
                                        enabled = selectedIndex > 0 && !busy && !checkingItems
                                    ) { Text("السابق") }
                                    OutlinedButton(
                                        onClick = {
                                            val nextIndex = (selectedIndex + 1).coerceAtMost(sharedUris.lastIndex)
                                            selectedIndex = nextIndex
                                            clearCurrentPreview()
                                            previewUri = sharedUris.getOrNull(nextIndex)?.takeIf {
                                                itemMimes.getOrNull(nextIndex)?.startsWith("image/") == true &&
                                                    encryptedItems.getOrNull(nextIndex) != true
                                            }
                                            previewMime = itemMimes.getOrNull(nextIndex).orEmpty()
                                            password = ""
                                        },
                                        enabled = selectedIndex < sharedUris.lastIndex && !busy && !checkingItems
                                    ) { Text("التالي") }
                                }
                            }
                            Text("نوع المشاركة: ${if (currentIsEncrypted) "VIPXENC مشفّر • $currentMime" else currentMime.ifBlank { "غير محدد" }}",
                                color = Color(0xFF42E3C2))
                            Text(
                                when {
                                    checkingItems -> "حالة العنصر: قيد الفحص"
                                    currentIsEncrypted -> "حالة العنصر: مشفّر • يتطلب كلمة المرور"
                                    currentMime.startsWith("image/") -> "حالة العنصر: صورة جاهزة للمعاينة"
                                    currentMime.startsWith("video/") -> "حالة العنصر: فيديو جاهز للتشغيل"
                                    else -> "حالة العنصر: النوع غير مدعوم أو غير معروف"
                                },
                                color = Color(0xFF9FB2C7), fontSize = 12.sp
                            )
                            Text(
                                "يصل المحتوى عبر إذن مشاركة مؤقت من التطبيق المصدر. لا نفتح مجلدات التطبيق ولا نحاول استخراج مفاتيح محادثاته.",
                                color = Color(0xFF9FB2C7), fontSize = 12.sp
                            )

                            if (currentIsEncrypted && (currentMime.startsWith("image/") || currentMime.startsWith("video/"))) {
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
                                                    var finalFile: File? = null
                                                    try {
                                                        val mime = source.use { input ->
                                                            outputFile.outputStream().buffered().use { output ->
                                                                VipxEncryptedMedia.decrypt(input, output, secret)
                                                            }
                                                        }
                                                        val extension = android.webkit.MimeTypeMap.getSingleton()
                                                            .getExtensionFromMimeType(mime) ?: "media"
                                                        finalFile = File(cacheDir, "vipx-shared-${System.nanoTime()}.$extension")
                                                        if (!outputFile.renameTo(finalFile)) {
                                                            outputFile.copyTo(finalFile, overwrite = true)
                                                            outputFile.delete()
                                                        }
                                                        Pair(Uri.fromFile(finalFile), mime)
                                                    } catch (error: Exception) {
                                                        // Never leave a partially decrypted plaintext file behind.
                                                        outputFile.delete()
                                                        finalFile?.delete()
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
                            } else if (currentMime.startsWith("video/")) {
                                Button(onClick = { openSharedVideo(sharedUri) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("تشغيل الفيديو المستلم")
                                }
                            } else {
                                Text(
                                    "هذا العنصر ليس صورة أو فيديو واضح النوع، ولم نتعرف عليه كحاوية VIPXENC. لن نخمن طريقة فك تشفيره. إذا كان DRM فالتشغيل يحتاج ترخيص المزود.",
                                    color = Color(0xFFFFD180), fontSize = 12.sp
                                )
                            }

                            if (sharedUris.size > 1 && !checkingItems) {
                                Text("كل عنصر يُعرض ويُعالج بشكل مستقل؛ انتقل بين العناصر دون خلط ملفاتها.", color = Color(0xFF9FB2C7), fontSize = 11.sp)
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
                                update = { loadSampledImage(it, imageUri) },
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

    private fun clearCurrentPreview() {
        decryptedPreviewFile?.delete()
        decryptedPreviewFile = null
    }


    private fun loadSampledImage(view: ImageView, uri: Uri) {
        if (view.tag == uri) return
        view.tag = uri
        view.setImageDrawable(null)
        view.post {
            val targetWidth = view.width.coerceAtLeast(1024)
            val targetHeight = view.height.coerceAtLeast(1024)
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        }
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                        var sample = 1
                        while (bounds.outWidth / sample > targetWidth * 2 ||
                            bounds.outHeight / sample > targetHeight * 2) {
                            sample *= 2
                        }
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                    }.getOrNull()
                }
                if (view.tag == uri) view.setImageBitmap(bitmap)
            }
        }
    }

    private fun openSharedVideo(uri: Uri) {
        if (uri.scheme == "file") {
            val candidate = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull()
            val cache = runCatching { cacheDir.canonicalFile }.getOrNull()
            val isOwnedPreview = candidate != null && cache != null &&
                candidate.parentFile == cache && candidate.name.startsWith("vipx-shared-") &&
                decryptedPreviewFile?.canonicalFile == candidate
            if (isOwnedPreview) {
                // Give PlaybackService its own copy. Keep the preview owned by this
                // Activity until it is destroyed, so a failed launch cannot orphan it.
                lifecycleScope.launch {
                    var playbackFile: File? = null
                    try {
                        playbackFile = withContext(Dispatchers.IO) {
                            val source = candidate!!
                            val extension = source.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".media"
                            val target = File(cacheDir, "vipx-player-${System.nanoTime()}$extension")
                            try {
                                source.copyTo(target, overwrite = false)
                                target
                            } catch (error: Exception) {
                                target.delete()
                                throw error
                            }
                        }
                        startActivity(
                            Intent(this@MediaCaptureActivity, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_STREAM_URL, Uri.fromFile(playbackFile).toString())
                        )
                    } catch (error: Exception) {
                        playbackFile?.delete()
                        android.widget.Toast.makeText(
                            this@MediaCaptureActivity,
                            "تعذر تجهيز الفيديو للتشغيل: ${error.message ?: "خطأ غير معروف"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return
            }
        }

        val playIntent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_STREAM_URL, uri.toString())
        if (uri.scheme == "content") {
            playIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setClipData(ClipData.newRawUri("VIP X shared media", uri))
        }
        runCatching { startActivity(playIntent) }.onFailure { error ->
            android.widget.Toast.makeText(
                this,
                "تعذر فتح مشغّل الفيديو: ${error.message ?: "خطأ غير معروف"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        decryptedPreviewFile?.delete()
        decryptedPreviewFile = null
        super.onDestroy()
    }
}
