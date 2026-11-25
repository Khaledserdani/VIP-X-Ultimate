package com.vipx.ultimate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val MediaBg = Color(0xFF07111F)
private val MediaAccent = Color(0xFF42E3C2)

class MediaLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = MediaAccent, background = MediaBg)) {
            MediaLibraryScreen(
                onPersistPermission = { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                },
                mimeFor = { uri -> contentResolver.getType(uri).orEmpty() }
            )
        } }
    }
}

@Composable
private fun MediaLibraryScreen(
    onPersistPermission: (Uri) -> Unit,
    mimeFor: (Uri) -> String
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMime by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("اختر صورة أو فيديو من ملفات هاتفك.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPersistPermission(uri)
            selectedUri = uri
            selectedMime = mimeFor(uri)
            message = if (selectedMime.startsWith("image/")) "صورة محددة" else
                if (selectedMime.startsWith("video/")) "فيديو محدد" else "نوع الملف غير مدعوم للمعاينة."
        }
    }

    Surface(Modifier.fillMaxSize(), color = MediaBg) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VIP X • مكتبة الوسائط", color = MediaAccent, style = MaterialTheme.typography.titleLarge)
            Text("فتح الصور والفيديوهات محلياً داخل التطبيق", color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Text("اختيار صورة")
                }
                Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) {
                    Text("اختيار فيديو")
                }
            }
            Text(message, color = Color.White)
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
            Text("تُفتح الملفات عبر منتقي Android الرسمي؛ لا يطلب التطبيق صلاحية شاملة لقراءة ملفات الهاتف.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
