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

@Composable
private fun LiveGridScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("vipx_stream_library", android.content.Context.MODE_PRIVATE) }
    val streams = remember {
        mutableStateListOf<String>().apply {
            addAll(preferences.getStringSet("stream_urls", emptySet()).orEmpty().take(4))
        }
    }
    fun persistStreams() {
        preferences.edit().putStringSet("stream_urls", streams.toSet()).apply()
    }
    var url by remember { mutableStateOf("") }
    var drmEnabled by remember { mutableStateOf(preferences.getBoolean("drm_enabled", false)) }
    var licenseUrl by remember { mutableStateOf(preferences.getString("license_url", "").orEmpty()) }
    var authorization by remember { mutableStateOf("") }
    fun persistDrmSettings() {
        preferences.edit()
            .putBoolean("drm_enabled", drmEnabled)
            .putString("license_url", licenseUrl)
            .remove("authorization")
            .apply()
    }
    var message by remember { mutableStateOf("أضف روابط بث HTTPS تملك حق الوصول إليها. الحد الحالي 4 معاينات متزامنة.") }

    Column(Modifier.fillMaxSize().background(GridBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("VIP X • شبكة البثوث", color = GridAccent, fontSize = 23.sp)
        Text("معاينات تلقائية أثناء البقاء داخل VIP X. اختر أي نافذة لفتحها في المشغّل الكامل.", color = Color.White, fontSize = 12.sp)
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("رابط بث مباشر HTTPS") }, singleLine = true)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = drmEnabled, onCheckedChange = { drmEnabled = it; persistDrmSettings() })
            Text("هذه البثوث محمية بـ Widevine", color = Color.White, fontSize = 12.sp)
        }
        if (drmEnabled) {
            OutlinedTextField(value = licenseUrl, onValueChange = { licenseUrl = it; persistDrmSettings() }, modifier = Modifier.fillMaxWidth(),
                label = { Text("رابط ترخيص HTTPS") }, singleLine = true)
            OutlinedTextField(value = authorization, onValueChange = { authorization = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Authorization (اختياري)") }, singleLine = true)
            Text("استخدم بيانات الترخيص التي وفرها مزوّد المحتوى لهذا البث. لا تدخل بيانات سرية لا تملك حق استخدامها.", color = Color(0xFF9FB2C7), fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val parsed = Uri.parse(url.trim())
                val license = Uri.parse(licenseUrl.trim())
                val licenseValid = !drmEnabled || (license.scheme == "https" && !license.host.isNullOrBlank())
                if (parsed.scheme == "https" && !parsed.host.isNullOrBlank() && licenseValid && streams.size < 4 && !streams.contains(url.trim())) {
                    streams.add(url.trim()); persistStreams(); url = ""; message = "تمت إضافة البث؛ بدأ التحميل تلقائياً."
                } else {
                    message = when {
                        streams.size >= 4 -> "وصلت إلى الحد الآمن للمعاينات المتزامنة (4)."
                        streams.contains(url.trim()) -> "هذا الرابط موجود بالفعل."
                        !licenseValid -> "أدخل رابط ترخيص Widevine صالحاً يبدأ بـ HTTPS."
                        else -> "الرابط غير صالح؛ استخدم HTTPS."
                    }
                }
            }, enabled = url.isNotBlank()) { Text("إضافة وتشغيل") }
            OutlinedButton(onClick = { streams.clear(); persistStreams(); message = "تم إيقاف جميع المعاينات." }) { Text("إيقاف الكل") }
        }
        Text(message, color = Color(0xFF9FB2C7), fontSize = 11.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(streams, key = { _, item -> item }) { index, stream ->
                Card(colors = CardDefaults.cardColors(containerColor = GridPanel), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("بث ${index + 1}", color = GridAccent, fontSize = 12.sp)
                        StreamPreview(stream, drmEnabled, licenseUrl, authorization, Modifier.fillMaxWidth().height(112.dp))
                        Text(stream, color = Color.White, fontSize = 9.sp, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                context.startActivity(Intent(context, PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_STREAM_URL, stream)
                                    .putExtra(PlayerActivity.EXTRA_DRM_ENABLED, drmEnabled)
                                    .putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL, licenseUrl.trim())
                                    .putExtra(PlayerActivity.EXTRA_DRM_AUTHORIZATION, authorization.trim()))
                            }) { Text("تكبير", fontSize = 11.sp) }
                            TextButton(onClick = { streams.remove(stream); persistStreams() }) { Text("إزالة", fontSize = 11.sp) }
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
    var state by remember(url) { mutableStateOf("جارٍ التحميل…") }
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
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (player.playbackState != Player.STATE_IDLE) player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
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
            modifier = Modifier.fillMaxWidth().height(88.dp), update = { it.player = player })
        Text(state, color = GridAccent, fontSize = 10.sp)
    }
}
