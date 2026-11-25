package com.vipx.ultimate

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)?.trim().orEmpty()
        if (streamUrl.isBlank()) {
            finish()
            return
        }

        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                controller.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                controller.prepare()
                controller.play()
            } catch (_: Exception) {
                finish()
            }
        }, mainExecutor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n            val params = PictureInPictureParams.Builder()\n                .setAspectRatio(Rational(16, 9))\n                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true) }\n                .build()\n            setPictureInPictureParams(params)\n        }\n\n        setContent {
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
                AndroidView(
                    factory = { playerView },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.player = mediaController }
                )
            }
        }
    }

    override fun onUserLeaveHint() {\n        super.onUserLeaveHint()\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mediaController?.isPlaying == true) {\n            enterPictureInPictureMode(\n                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()\n            )\n        }\n    }\n\n    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        controllerFuture?.cancel(false)
        controllerFuture = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STREAM_URL = "com.vipx.ultimate.STREAM_URL"
    }
}
