package com.vipx.ultimate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val GridBg = Color(0xFF07111F)
private val GridPanel = Color(0xFF122238)
private val GridAccent = Color(0xFF42E3C2)

class LiveGridActivity : ComponentActivity() {
    private var playbackService by mutableStateOf<MultiStreamPlaybackService?>(null)
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playbackService = (binder as? MultiStreamPlaybackService.LocalBinder)?.service()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = GridAccent, background = GridBg, surface = GridPanel)) {
                LiveGridScreen(
                    service = playbackService,
                    startPlaybackService = {
                        val intent = Intent(this, MultiStreamPlaybackService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent)
                        else startService(intent)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bound = bindService(Intent(this, MultiStreamPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        playbackService = null
        // Deliberately do not stop playback: the foreground service owns all four players.
        super.onStop()
    }
}

private data class StreamEntry(val url: String, val drmEnabled: Boolean, val licenseUrl: String)

@Composable
private fun LiveGridScreen(
    service: MultiStreamPlaybackService?,
    startPlaybackService: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("vipx_stream_library", Context.MODE_PRIVATE) }
    val sessionAuthorization = remember { mutableStateMapOf<String, String>() }
    val streams = remember {
        mutableStateListOf<StreamEntry>().apply {
            val saved = preferences.getString("stream_entries_v2", null)
            if (!saved.isNullOrBlank()) {
                runCatching {
                    val array = org.json.JSONArray(saved)
                    for (i in 0 until minOf(array.length(), 4)) {
                        val item = array.getJSONObject(i)
                        val streamUrl = item.optString("url")
                        if (streamUrl.startsWith("https://")) {
                            add(StreamEntry(streamUrl, item.optBoolean("drmEnabled"), item.optString("licenseUrl")))
                        }
                    }
                }
            } else {
                preferences.getStringSet("stream_urls", emptySet()).orEmpty().take(4).forEach { oldUrl ->
                    add(StreamEntry(oldUrl, preferences.getBoolean("drm_enabled", false), preferences.getString("license_url", "").orEmpty()))
                }
            }
        }
    }

    fun persistStreams() {
        val array = org.json.JSONArray()
        streams.forEach { entry ->
            array.put(org.json.JSONObject().put("url", entry.url)
                .put("drmEnabled", entry.drmEnabled).put("licenseUrl", entry.licenseUrl))
        }
        preferences.edit().putString("stream_entries_v2", array.toString())
            .remove("stream_urls").remove("drm_enabled").remove("license_url").remove("authorization").apply()
    }

    var url by remember { mutableStateOf("") }
    var drmEnabled by remember { mutableStateOf(false) }
    var licenseUrl by remember { mutableStateOf("") }
    var authorization by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("أضف حتى أربعة بثوث؛ ستعمل معاً وتستمر عند تصفح التطبيق.") }
    var audibleStream by remember { mutableStateOf<String?>(service?.selectedAudioStream()) }

    LaunchedEffect(service) { audibleStream = service?.selectedAudioStream() }

    LaunchedEffect(service, streams.size) {
        if (streams.isNotEmpty()) {
            startPlaybackService()
            service?.let { engine ->
                streams.forEach { entry ->
                    runCatching {
                        engine.playStream(entry.url, entry.drmEnabled, entry.licenseUrl, sessionAuthorization[entry.url].orEmpty())
                    }.onFailure { message = "تعذر تشغيل أحد البثوث؛ تحقق من الرابط والترخيص والاتصال." }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(GridBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("VIP X • أربعة بثوث متزامنة", color = GridAccent, fontSize = 23.sp)
        Text(
            "تعمل المعاينات الأربع بالتوازي داخل شبكة 2×2. تبقى جلسات التشغيل في خدمة مستقلة عند الانتقال إلى صفحات التطبيق أو تصغيره.",
            color = Color.White, fontSize = 12.sp
        )
        OutlinedButton(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ))
                } else {
                    context.startService(Intent(context, FloatingStreamsOverlayService::class.java))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("عرض المعاينات فوق التطبيقات الأخرى") }
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("رابط بث مباشر HTTPS") }, singleLine = true)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = drmEnabled, onCheckedChange = {
                drmEnabled = it
                if (!it) authorization = ""
            })
            Text("هذا البث محمي بـ Widevine", color = Color.White, fontSize = 12.sp)
        }
        if (drmEnabled) {
            OutlinedTextField(value = licenseUrl, onValueChange = { licenseUrl = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("رابط ترخيص HTTPS لهذا البث") }, singleLine = true)
            OutlinedTextField(value = authorization, onValueChange = { authorization = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Authorization لهذا البث (جلسة فقط)") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Text("كل بث له رابط ترخيص وترويسة مصادقة مستقلان. لا نحفظ Authorization في التخزين.", color = Color(0xFF9FB2C7), fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cleanUrl = url.trim()
                val parsed = Uri.parse(cleanUrl)
                val parsedLicense = Uri.parse(licenseUrl.trim())
                val licenseValid = !drmEnabled || (parsedLicense.scheme == "https" && !parsedLicense.host.isNullOrBlank())
                when {
                    parsed.scheme != "https" || parsed.host.isNullOrBlank() -> message = "الرابط غير صالح؛ استخدم HTTPS."
                    !licenseValid -> message = "أدخل رابط ترخيص Widevine عبر HTTPS."
                    streams.size >= 4 -> message = "الأربعة بثوث مستخدمة بالفعل. احذف بثاً قبل إضافة آخر."
                    streams.any { it.url == cleanUrl } -> message = "هذا البث موجود بالفعل."
                    else -> {
                        val entry = StreamEntry(cleanUrl, drmEnabled, if (drmEnabled) licenseUrl.trim() else "")
                        streams.add(entry)
                        if (authorization.isNotBlank()) sessionAuthorization[cleanUrl] = authorization.trim()
                        persistStreams()
                        startPlaybackService()
                        var startError: String? = null
                        service?.let {
                            runCatching { it.playStream(entry.url, entry.drmEnabled, entry.licenseUrl, sessionAuthorization[entry.url].orEmpty()) }
                                .onFailure { startError = "تعذر بدء البث؛ تحقق من الرابط والترخيص والاتصال." }
                        }
                        url = ""; licenseUrl = ""; authorization = ""; drmEnabled = false
                        message = startError ?: "تمت إضافة البث؛ يجري تشغيله مع البثوث الأخرى."
                    }
                }
            }, enabled = url.isNotBlank()) { Text("إضافة وتشغيل") }

            OutlinedButton(onClick = {
                service?.stopAll()
                audibleStream = null
                streams.clear()
                sessionAuthorization.clear()
                persistStreams()
                message = "تم إيقاف البثوث الأربعة."
            }) { Text("إيقاف الكل") }
        }

        Text(message, color = Color(0xFF9FB2C7), fontSize = 11.sp)
        Text(if (audibleStream == null) "الصوت: مكتوم • اختر بثاً لسماع صوته" else "الصوت يخرج من: ${streams.indexOfFirst { it.url == audibleStream } + 1}", color = GridAccent, fontSize = 11.sp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(streams, key = { _, item -> item.url }) { index, entry ->
                Card(colors = CardDefaults.cardColors(containerColor = GridPanel), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("بث ${index + 1}", color = GridAccent, fontSize = 12.sp)
                        StreamPreview(entry, sessionAuthorization[entry.url].orEmpty(), service, Modifier.fillMaxWidth().height(116.dp))
                        Text(entry.url, color = Color.White, fontSize = 9.sp, maxLines = 1)
                        Text(if (entry.drmEnabled) "Widevine • ترخيص مستقل" else "بث مباشر", color = Color(0xFF9FB2C7), fontSize = 9.sp)
                        TextButton(onClick = {
                            val nextAudio = if (audibleStream == entry.url) null else entry.url
                            service?.selectAudioStream(nextAudio)
                            audibleStream = nextAudio
                        }) {
                            Text(if (audibleStream == entry.url) "كتم هذا البث" else "اسمع هذا البث", fontSize = 11.sp)
                        }
                        TextButton(onClick = {
                            // Move this entry out of the preview grid before opening the full player.
                            val sessionKey = entry.url
                            val sessionAuth = sessionAuthorization[sessionKey].orEmpty()
                            service?.stopStream(sessionKey)
                            if (audibleStream == sessionKey) audibleStream = null
                            streams.remove(entry)
                            sessionAuthorization.remove(sessionKey)
                            persistStreams()
                            context.startActivity(
                                Intent(context, PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_STREAM_URL, sessionKey)
                                    .putExtra(PlayerActivity.EXTRA_DRM_ENABLED, entry.drmEnabled)
                                    .putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL, entry.licenseUrl)
                                    .putExtra(PlayerActivity.EXTRA_DRM_AUTHORIZATION, sessionAuth)
                            )
                        }) { Text("فتح المشغّل الكامل", fontSize = 11.sp) }
                        TextButton(onClick = {
                            service?.stopStream(entry.url)
                            if (audibleStream == entry.url) audibleStream = null
                            streams.remove(entry)
                            sessionAuthorization.remove(entry.url)
                            persistStreams()
                        }) { Text("إزالة وإيقاف", fontSize = 11.sp) }
                    }
                }
            }
        }
        Text(
            "ملاحظة: معاينات الفيديو الأربع صامتة افتراضياً لتجنب خلط أربعة مسارات صوتية؛ اختر «اسمع هذا البث» لتفعيل صوت بث واحد مع استمرار بقية الفيديوهات. عدد الفيديوهات الفعلي يعتمد على قدرة الجهاز وتراخيص البث.",
            color = Color.Gray, fontSize = 10.sp
        )
    }
}

@Composable
private fun StreamPreview(
    entry: StreamEntry,
    authorization: String,
    service: MultiStreamPlaybackService?,
    modifier: Modifier = Modifier
) {
    var player by remember(entry.url, service) { mutableStateOf<ExoPlayer?>(null) }
    var state by remember(entry.url, service) { mutableStateOf("بانتظار خدمة التشغيل…") }

    LaunchedEffect(entry.url, service) {
        if (service != null) {
            runCatching {
                service.playStream(entry.url, entry.drmEnabled, entry.licenseUrl, authorization)
                player = service.playerFor(entry.url)
                state = "يعمل ضمن الشبكة"
            }.onFailure {
                state = "تعذر تشغيل البث؛ تحقق من الرابط والترخيص والاتصال."
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state = when (playbackState) {
                    Player.STATE_BUFFERING -> "جارٍ التحميل…"
                    Player.STATE_READY -> "يعمل • مباشر"
                    Player.STATE_ENDED -> "انتهى البث"
                    else -> "في الانتظار"
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // The service releases terminally failed players; never keep a stale view reference.
                player = null
                state = "خطأ: ${error.errorCodeName}"
            }
        }
        player?.let { activePlayer ->
            activePlayer.addListener(listener)
            // A player may already be READY before this composable attaches.
            state = when {
                activePlayer.playerError != null -> "خطأ: ${activePlayer.playerError?.errorCodeName}"
                activePlayer.playbackState == Player.STATE_BUFFERING -> "جارٍ التحميل…"
                activePlayer.playbackState == Player.STATE_READY && activePlayer.isPlaying -> "يعمل • مباشر"
                activePlayer.playbackState == Player.STATE_READY -> "جاهز • ينتظر التشغيل"
                activePlayer.playbackState == Player.STATE_ENDED -> "انتهى البث"
                else -> "في الانتظار"
            }
        }
        onDispose { player?.removeListener(listener) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.startsWith("خطأ:") || state.startsWith("تعذر التشغيل:")) {
            TextButton(onClick = {
                runCatching {
                    service?.playStream(entry.url, entry.drmEnabled, entry.licenseUrl, authorization)
                    player = service?.playerFor(entry.url)
                    state = if (player == null) "تعذر بدء المحاولة" else "إعادة المحاولة…"
                }.onFailure {
                    state = "تعذر تشغيل البث؛ تحقق من الرابط والترخيص والاتصال."
                }
            }, enabled = service != null) {
                Text("إعادة المحاولة", fontSize = 10.sp)
            }
        }
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxWidth().height(88.dp),
            update = { it.player = player },
            onRelease = { it.player = null }
        )
        Text(state, color = GridAccent, fontSize = 10.sp)
    }
}
