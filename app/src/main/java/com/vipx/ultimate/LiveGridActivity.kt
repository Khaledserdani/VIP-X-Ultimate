package com.vipx.ultimate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val GridBg = Color(0xFF07111F)
private val GridPanel = Color(0xFF122238)
private val GridAccent = Color(0xFF42E3C2)

class LiveGridActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = GridAccent, background = GridBg, surface = GridPanel)) { LiveGridScreen() } }
    }
}

private data class StreamEntry(val url: String, val drmEnabled: Boolean, val licenseUrl: String)

@Composable
private fun LiveGridScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("vipx_stream_library", android.content.Context.MODE_PRIVATE) }
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
        preferences.edit().putString("stream_entries_v2", array.toString()).remove("stream_urls")
            .remove("drm_enabled").remove("license_url").remove("authorization").apply()
    }
    var url by remember { mutableStateOf("") }
    var drmEnabled by remember { mutableStateOf(false) }
    var licenseUrl by remember { mutableStateOf("") }
    var authorization by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("أضف روابط بث HTTPS تملك حق الوصول إليها. إعداد DRM مستقل لكل بث.") }

    Column(Modifier.fillMaxSize().background(GridBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("VIP X • شبكة البثوث", color = GridAccent, fontSize = 23.sp)
        Text("المعاينات صامتة داخل الشبكة. لتشغيل بث واحد بالصوت أثناء التصفح أو خارج التطبيق، افتحه في المشغّل المستقل.", color = Color.White, fontSize = 12.sp)
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("رابط بث مباشر HTTPS") }, singleLine = true)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = drmEnabled, onCheckedChange = { drmEnabled = it })
            Text("هذا البث محمي بـ Widevine", color = Color.White, fontSize = 12.sp)
        }
        if (drmEnabled) {
            OutlinedTextField(value = licenseUrl, onValueChange = { licenseUrl = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("رابط ترخيص HTTPS لهذا البث") }, singleLine = true)
            OutlinedTextField(value = authorization, onValueChange = { authorization = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Authorization لهذا البث (جلسة فقط)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Text("رابط الترخيص يُحفظ مع البث. Authorization يبقى في ذاكرة الجلسة فقط ولا يُكتب في التخزين.", color = Color(0xFF9FB2C7), fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val parsed = Uri.parse(url.trim())
                val license = Uri.parse(licenseUrl.trim())
                val licenseValid = !drmEnabled || (license.scheme == "https" && !license.host.isNullOrBlank())
                if (parsed.scheme == "https" && !parsed.host.isNullOrBlank() && licenseValid && streams.size < 4 && streams.none { it.url == url.trim() }) {
                    val cleanUrl = url.trim()
                    streams.add(StreamEntry(cleanUrl, drmEnabled, if (drmEnabled) licenseUrl.trim() else ""))
                    if (authorization.isNotBlank()) sessionAuthorization[cleanUrl] = authorization.trim()
                    persistStreams(); url = ""; licenseUrl = ""; authorization = ""; drmEnabled = false
                    message = "تمت إضافة البث بإعداداته الخاصة؛ بدأ التحميل تلقائياً."
                } else {
                    message = when {
                        streams.size >= 4 -> "وصلت إلى الحد الحالي للمعاينات (4)."
                        streams.any { it.url == url.trim() } -> "هذا الرابط موجود بالفعل."
                        !licenseValid -> "أدخل رابط ترخيص HTTPS صالحاً لهذا البث."
                        else -> "الرابط غير صالح؛ استخدم HTTPS."
                    }
                }
            }, enabled = url.isNotBlank()) { Text("إضافة وتشغيل") }
            OutlinedButton(onClick = { streams.clear(); sessionAuthorization.clear(); persistStreams(); message = "تم إيقاف جميع المعاينات." }) { Text("إيقاف الكل") }
        }
        Text(message, color = Color(0xFF9FB2C7), fontSize = 11.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(streams, key = { _, item -> item.url }) { index, entry ->
                Card(colors = CardDefaults.cardColors(containerColor = GridPanel), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("بث ${index + 1}", color = GridAccent, fontSize = 12.sp)
                        StreamPreview(entry.url, entry.drmEnabled, entry.licenseUrl, sessionAuthorization[entry.url].orEmpty(), Modifier.fillMaxWidth().height(112.dp))
                        Text(entry.url, color = Color.White, fontSize = 9.sp, maxLines = 1)
                        Text(if (entry.drmEnabled) "Widevine • ترخيص مستقل" else "بدون DRM", color = Color(0xFF9FB2C7), fontSize = 9.sp)
                        if (entry.drmEnabled) {
                            OutlinedTextField(
                                value = sessionAuthorization[entry.url].orEmpty(),
                                onValueChange = { value ->
                                    if (value.isBlank()) sessionAuthorization.remove(entry.url)
                                    else sessionAuthorization[entry.url] = value
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Authorization لهذه الجلسة", fontSize = 9.sp) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                context.startActivity(Intent(context, PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_STREAM_URL, entry.url)
                                    .putExtra(PlayerActivity.EXTRA_DRM_ENABLED, entry.drmEnabled)
                                    .putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL, entry.licenseUrl)
                                    .putExtra(PlayerActivity.EXTRA_DRM_AUTHORIZATION, sessionAuthorization[entry.url].orEmpty()))
                            }) { Text("تشغيل مستقل", fontSize = 11.sp) }
                            TextButton(onClick = { streams.remove(entry); sessionAuthorization.remove(entry.url); persistStreams() }) { Text("إزالة", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamPreview(url: String, drmEnabled: Boolean, licenseUrl: String, authorization: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember(url, drmEnabled, licenseUrl, authorization) { mutableStateOf("جارٍ التحميل…") }
    val player = remember(url, drmEnabled, licenseUrl, authorization) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    state = when (playbackState) {
                        Player.STATE_BUFFERING -> "جارٍ التحميل…"
                        Player.STATE_READY -> "مباشر"
                        Player.STATE_ENDED -> "انتهى البث"
                        else -> "في الانتظار"
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    state = "تعذر التشغيل: ${error.errorCodeName}"
                }
            })
            val item = if (drmEnabled) {
                val drm = MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
                    .setLicenseUri(Uri.parse(licenseUrl.trim()))
                if (authorization.isNotBlank()) drm.setLicenseRequestHeaders(mapOf("Authorization" to authorization.trim()))
                MediaItem.Builder().setUri(Uri.parse(url)).setDrmConfiguration(drm.build()).build()
            } else MediaItem.fromUri(Uri.parse(url))
            setMediaItem(item)
            prepare()
            play()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        var resumeAfterStop = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (resumeAfterStop && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                        player.play()
                    }
                    resumeAfterStop = false
                }
                Lifecycle.Event.ON_STOP -> {
                    resumeAfterStop = player.isPlaying
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxWidth().height(88.dp), update = { it.player = player },
            onRelease = { view -> view.player = null })
        Text(state, color = GridAccent, fontSize = 10.sp)
    }
}
