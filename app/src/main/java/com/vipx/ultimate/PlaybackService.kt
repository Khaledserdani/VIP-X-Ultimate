package com.vipx.ultimate

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null

        // Decrypted shared media belongs to this playback service after hand-off.
        // Remove only files created by the share-capture flow in this app's cache.
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("vipx-shared-") }
            ?.forEach { runCatching { it.delete() } }

        super.onDestroy()
    }
}
