package com.vipx.ultimate

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val MediaBg = Color(0xFF07111F)
private val MediaAccent = Color(0xFF42E3C2)

class MediaLibraryActivity : ComponentActivity() {
    private var decryptedPreviewFile: File? = null

    private fun readSharedMedia(source: Intent): List<Pair<Uri, String>> {
        val sharedUris = when (source.action) {
            Intent.ACTION_SEND -> {
                val single = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                listOfNotNull(single ?: source.clipData?.getItemAt(0)?.uri ?: source.data)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val multiple = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    source.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                val fromClip = source.clipData?.let { clip ->
                    (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri }
                }.orEmpty()
                (multiple.orEmpty() + fromClip).distinct()
            }
            else -> emptyList()
        }

        return sharedUris.asSequence()
            .filter { it.scheme == "content" }
            .take(20)
            .mapNotNull { uri ->
                val resolvedMime = contentResolver.getType(uri).orEmpty()
                val mime = when {
                    resolvedMime.startsWith("image/") || resolvedMime.startsWith("video/") -> resolvedMime
                    resolvedMime.isBlank() -> source.type?.takeIf {
                        it.startsWith("image/") || it.startsWith("video/")
                    }.orEmpty()
                    else -> ""
                }
                if (mime.startsWith("image/") || mime.startsWith("video/")) uri to mime else null
            }
            .toList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activity destruction is not guaranteed after process death. Prune only
        // old decrypted previews so recent files from another Activity remain safe.
        val staleBefore = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        cacheDir.listFiles()
            ?.filter {
                it.isFile && it.name.startsWith("vipx-dec-") &&
                    it.lastModified() < staleBefore
            }
            ?.forEach { it.delete() }
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
        val sharedMedia = readSharedMedia(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = MediaAccent, background = MediaBg)) {
                MediaLibraryScreen(
                    initialSharedMedia = sharedMedia,
                    onPersistPermission = { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) { }
                    },
                    mimeFor = { uri -> contentResolver.getType(uri).orEmpty() },
                    inspectEncrypted = { uri ->
                        contentResolver.openInputStream(uri)?.use { VipxEncryptedMedia.inspect(it).mimeType }
                            ?: throw IllegalStateException("تعذر قراءة الملف")
                    },
                    decryptToPrivateCache = { uri, password ->
                        val input = contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("تعذر فتح الملف المشفر")
                        val outputFile = File.createTempFile("vipx-dec-", ".tmp", cacheDir)
                        var incompleteFinalFile: File? = null
                        try {
                            val mime = input.use { source ->
                                outputFile.outputStream().buffered().use { sink ->
                                    VipxEncryptedMedia.decrypt(source, sink, password)
                                }
                            }
                            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "media"
                            val finalFile = File(cacheDir, "vipx-dec-${System.nanoTime()}.$extension")
                            incompleteFinalFile = finalFile
                            if (!outputFile.renameTo(finalFile)) {
                                try {
                                    outputFile.copyTo(finalFile, overwrite = true)
                                    outputFile.delete()
                                } catch (copyError: Exception) {
                                    finalFile.delete()
                                    throw copyError
                                }
                            }
                            decryptedPreviewFile?.delete()
                            decryptedPreviewFile = finalFile
                            incompleteFinalFile = null
                            Pair(Uri.fromFile(finalFile), mime)
                        } catch (error: Exception) {
                            outputFile.delete()
                            incompleteFinalFile?.delete()
                            throw error
                        }
                    },
                    encryptToDocument = { sourceUri, destinationUri, password ->
                        val mime = contentResolver.getType(sourceUri)
                            ?: throw IllegalStateException("تعذر تحديد نوع الصورة أو الفيديو")
                        require(mime.startsWith("image/") || mime.startsWith("video/")) {
                            "يمكن تشفير الصور والفيديوهات فقط"
                        }
                        try {
                            val input = contentResolver.openInputStream(sourceUri)
                                ?: throw IllegalStateException("تعذر قراءة الملف الأصلي")
                            input.use { source ->
                                val output = contentResolver.openOutputStream(destinationUri, "wt")
                                    ?: throw IllegalStateException("تعذر إنشاء ملف VIPXENC")
                                output.use { sink ->
                                    VipxEncryptedMedia.encrypt(source, sink, password, mime)
                                }
                            }
                        } catch (error: Exception) {
                            // SAF providers may leave a partial destination after a failed write.
                            runCatching { DocumentsContract.deleteDocument(contentResolver, destinationUri) }
                            throw error
                        }
                    },
                    clearDecryptedCache = {
                        decryptedPreviewFile?.delete()
                        decryptedPreviewFile = null
                    },
                    onShowFloatingMedia = { uri, mime ->
                        if (!android.provider.Settings.canDrawOverlays(this)) {
                            startActivity(Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            ))
                        } else {
                            val transferDecryptedFile = uri.scheme == "file" &&
                                decryptedPreviewFile?.absolutePath == uri.path
                            val overlayIntent = Intent(this, FloatingMediaOverlayService::class.java)
                                .putExtra(FloatingMediaOverlayService.EXTRA_URI, uri.toString())
                                .putExtra(FloatingMediaOverlayService.EXTRA_MIME, mime)
                                .putExtra(FloatingMediaOverlayService.EXTRA_TRANSFER_FILE_OWNERSHIP, transferDecryptedFile)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .apply {
                                    clipData = android.content.ClipData.newRawUri("VIP X media", uri)
                                }
                            try {
                                if (mime.startsWith("video/") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(overlayIntent)
                                } else {
                                    startService(overlayIntent)
                                }
                                if (transferDecryptedFile) decryptedPreviewFile = null
                            } catch (_: Exception) {
                                // Keep Activity ownership so its normal cleanup remains responsible.
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        decryptedPreviewFile?.delete()
        decryptedPreviewFile = null
        super.onDestroy()
    }
}

@Composable
private fun MediaLibraryScreen(
    initialSharedMedia: List<Pair<Uri, String>>,
    onPersistPermission: (Uri) -> Unit,
    mimeFor: (Uri) -> String,
    inspectEncrypted: (Uri) -> String,
    decryptToPrivateCache: (Uri, CharArray) -> Pair<Uri, String>,
    encryptToDocument: (Uri, Uri, CharArray) -> Unit,
    clearDecryptedCache: () -> Unit,
    onShowFloatingMedia: (Uri, String) -> Unit
) {
    var selectedUri by remember(initialSharedMedia) { mutableStateOf(initialSharedMedia.firstOrNull()?.first) }
    var selectedMime by remember(initialSharedMedia) { mutableStateOf(initialSharedMedia.firstOrNull()?.second.orEmpty()) }
    var encryptedSource by remember { mutableStateOf<Uri?>(null) }
    var encryptedMime by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var encryptPassword by remember { mutableStateOf("") }
    var pendingEncryptSource by remember { mutableStateOf<Uri?>(null) }
    var pendingEncryptPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember(initialSharedMedia) {
        mutableStateOf(
            if (initialSharedMedia.isNotEmpty()) "تم استقبال " + initialSharedMedia.size + " عنصر مشاركة مع VIP X."
            else "اختر صورة أو فيديو، أو افتح ملف VIPXENC مشفّراً."
        )
    }
    val scope = rememberCoroutineScope()

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            clearDecryptedCache()
            encryptedSource = null
            encryptedMime = ""
            password = ""
            onPersistPermission(uri)
            selectedUri = uri
            selectedMime = mimeFor(uri)
            message = if (selectedMime.startsWith("image/")) "صورة محددة" else
                if (selectedMime.startsWith("video/")) "فيديو محدد" else "نوع الملف غير مدعوم للمعاينة."
        }
    }

    val encryptedSavePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        val source = pendingEncryptSource
        if (destination != null && source != null) {
            val secret = pendingEncryptPassword.toCharArray()
            // Drop UI-held password strings as soon as the operation takes ownership.
            pendingEncryptPassword = ""
            encryptPassword = ""
            busy = true
            message = "يجري تشفير الملف وحفظه بصيغة VIPXENC…"
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { encryptToDocument(source, destination, secret) }
                    message = "نجح تشفير الملف وحفظه. احتفظ بكلمة المرور لاستعادته لاحقاً."
                } catch (error: Exception) {
                    message = "فشل التشفير: ${error.message ?: error.javaClass.simpleName}"
                } finally {
                    secret.fill('\u0000')
                    pendingEncryptPassword = ""
                    pendingEncryptSource = null
                    busy = false
                }
            }
        } else {
            pendingEncryptPassword = ""
            pendingEncryptSource = null
            encryptPassword = ""
        }
    }

    val encryptedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            clearDecryptedCache()
            selectedUri = null
            selectedMime = ""
            encryptedSource = uri
            password = ""
            onPersistPermission(uri)
            busy = true
            message = "يجري فحص ترويسة VIPXENC في الخلفية…"
            scope.launch {
                encryptedMime = withContext(Dispatchers.IO) {
                    runCatching { inspectEncrypted(uri) }.getOrDefault("")
                }
                message = if (encryptedMime.startsWith("image/") || encryptedMime.startsWith("video/")) {
                    "ملف VIPXENC صالح مبدئياً • النوع: $encryptedMime. أدخل كلمة المرور لفكّه."
                } else {
                    "لم يتم التعرف على حاوية VIPXENC صالحة أو أن ترويسة الملف تالفة."
                }
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MediaBg) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VIP X • مكتبة الوسائط", color = MediaAccent, style = MaterialTheme.typography.titleLarge)
            Text("فتح الصور والفيديوهات العادية وملفات VIPXENC التي تملك كلمة مرورها.", color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f)) {
                    Text("صور وفيديو")
                }
                OutlinedButton(onClick = { encryptedPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("ملف مشفّر")
                }
            }

            if (initialSharedMedia.size > 1) {
                Text("العناصر المستلمة (" + initialSharedMedia.size + ") — اختر ما تريد معاينته:", color = Color.LightGray)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    initialSharedMedia.forEachIndexed { index, item ->
                        OutlinedButton(
                            onClick = {
                                if (selectedUri?.scheme == "file") clearDecryptedCache()
                                encryptedSource = null
                                encryptedMime = ""
                                password = ""
                                selectedUri = item.first
                                selectedMime = item.second
                                message = if (item.second.startsWith("image/")) "الصورة " + (index + 1) + " محددة"
                                    else "الفيديو " + (index + 1) + " محدد"
                            },
                            enabled = !busy
                        ) {
                            Text((index + 1).toString() + ". " + if (item.second.startsWith("image/")) "صورة" else "فيديو")
                        }
                    }
                }
            }

            if (selectedUri != null && (selectedMime.startsWith("image/") || selectedMime.startsWith("video/"))) {
                OutlinedTextField(
                    value = encryptPassword,
                    onValueChange = { encryptPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة مرور جديدة للتشفير") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy
                )
                OutlinedButton(
                    onClick = {
                        pendingEncryptSource = selectedUri
                        pendingEncryptPassword = encryptPassword
                        val extension = if (selectedMime.startsWith("image/")) "image" else "video"
                        encryptedSavePicker.launch("VIPX-$extension-${System.currentTimeMillis()}.vipxenc")
                    },
                    enabled = encryptPassword.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تشفير الملف وحفظ VIPXENC") }
            }

            if (encryptedSource != null) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة مرور ملف VIPXENC") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy
                )
                Button(
                    onClick = {
                        val source = encryptedSource ?: return@Button
                        val secret = password.toCharArray()
                        busy = true
                        message = "يجري فك التشفير والتحقق من سلامة جميع القطع…"
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { decryptToPrivateCache(source, secret) }
                                selectedUri = result.first
                                selectedMime = result.second
                                encryptedSource = null
                                encryptedMime = ""
                                message = "نجح فك التشفير والتحقق. تمت المعاينة من نسخة مؤقتة داخل مساحة التطبيق الخاصة."
                            } catch (error: Exception) {
                                message = when (error) {
                                    is javax.crypto.AEADBadTagException -> "فشل التحقق: كلمة المرور خاطئة أو الملف تم العبث به."
                                    is java.io.EOFException -> "الملف مبتور أو علامة اكتماله غير موجودة."
                                    is IllegalArgumentException -> "ملف مشفّر غير صالح أو بياناته تالفة."
                                    else -> "تعذر فك الملف: ${error.message ?: error.javaClass.simpleName}"
                                }
                            } finally {
                                secret.fill('\u0000')
                                password = ""
                                busy = false
                            }
                        }
                    },
                    enabled = password.isNotEmpty() && encryptedMime.startsWith("image/").or(encryptedMime.startsWith("video/")) && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "جارٍ التحقق وفك التشفير…" else "فك التشفير وفتح الوسائط")
                }
            }

            Text(message, color = Color.White)
            val floatingUri = selectedUri
            if (floatingUri != null && (selectedMime.startsWith("image/") || selectedMime.startsWith("video/"))) {
                OutlinedButton(
                    onClick = { onShowFloatingMedia(floatingUri, selectedMime) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("عرض في نافذة عائمة فوق التطبيقات") }
            }
            val uri = selectedUri
            if (uri != null && selectedMime.startsWith("image/")) {
                AndroidView(
                    factory = { context -> ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                    } },
                    update = { view -> view.setImageURI(uri) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (uri != null && selectedMime.startsWith("video/")) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val player = remember(uri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(uri))
                        repeatMode = Player.REPEAT_MODE_OFF
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(player) {
                    onDispose { player.release() }
                }
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = true; this.player = player } },
                    update = { it.player = player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF122238)),
                    contentAlignment = Alignment.Center) {
                    Text("ستظهر معاينة الصورة أو مشغّل الفيديو هنا", color = Color.LightGray)
                }
            }
            Text(
                "تُفتح الملفات عبر منتقي Android الرسمي. الملفات المفكوكة تحفظ مؤقتاً في مساحة التطبيق الخاصة وتحذف عند إغلاق شاشة المكتبة.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
