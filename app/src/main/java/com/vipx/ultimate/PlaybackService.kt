package com.vipx.ultimate

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val sessionCallback = object : MediaSession.Callback {
        override fun onPlayerInteractionFinished(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommands: Player.Commands
        ) {
            // A standalone STOP ends the session. A stream replacement also clears
            // media items, but PlayerActivity no longer sends STOP during replacement.
            if (playerCommands.contains(Player.COMMAND_STOP)) {
                session.player.clearMediaItems()
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        val session = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()
        session.setMediaButtonPreferences(
            listOf(
                CommandButton.Builder(CommandButton.ICON_STOP)
                    .setDisplayName("إيقاف")
                    .setPlayerCommand(Player.COMMAND_STOP)
                    .build()
            )
        )
        mediaSession = session
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.stop()
            player.clearMediaItems()
            player.release()
            release()
        }
        mediaSession = null
        // These copies are transferred exclusively to this single-player service.
        // Delete them when the playback session ends; stale-cache cleanup is the
        // fallback if Android kills the process without delivering onDestroy().
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("vipx-player-") }
            ?.forEach { runCatching { it.delete() } }
        super.onDestroy()
    }
}
