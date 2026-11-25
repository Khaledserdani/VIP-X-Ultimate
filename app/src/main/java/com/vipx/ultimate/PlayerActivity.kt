package com.vipx.ultimate

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var playbackMessage by mutableStateOf("جارٍ الاتصال بمشغّل الوسائط…")

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            playbackMessage = when (playbackState) {
                Player.STATE_BUFFERING -> "جارٍ تحميل البث…"
                Player.STATE_READY -> "البث جاهز"
                Player.STATE_ENDED -> "انتهى البث"
                else -> "في انتظار البث…"
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackMessage = "تعذر تشغيل البث: ${error.errorCodeName}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)?.trim().orEmpty()
        val parsedUri = Uri.parse(streamUrl)
        if (parsedUri.scheme != "https" || parsedUri.host.isNullOrBlank()) {
            playbackMessage = "الرابط غير صالح؛ يلزم رابط HTTPS مباشر متوافق."
            finish()
            return
        }

        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                controller.addListener(playerListener)
                controller.setMediaItem(MediaItem.fromUri(parsedUri))
                controller.prepare()
                controller.play()
            } catch (_: Exception) {
                playbackMessage = "تعذر الاتصال بخدمة تشغيل الوسائط."
            }
        }, mainExecutor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true) }
                .build()
            setPictureInPictureParams(params)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val playerView = remember {
                    PlayerView(this).apply {
                        useController = true
                        keepScreenOn = true
                    }
                }
                DisposableEffect(mediaController) {
                    playerView.player = mediaController
                    onDispose { playerView.player = null }
                }
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { playerView },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.player = mediaController }
                    )
                    if (playbackMessage != "البث جاهز") {
                        Text(
                            text = playbackMessage,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .background(Color(0xCC101820))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mediaController?.isPlaying == true) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onDestroy() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STREAM_URL = "com.vipx.ultimate.STREAM_URL"
    }
}
