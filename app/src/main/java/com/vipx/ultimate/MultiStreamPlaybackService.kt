package com.vipx.ultimate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Owns up to four independent stream players.
 * Players are not tied to an Activity lifecycle, so navigation/backgrounding
 * does not destroy active playback. Access remains in-process only.
 */
class MultiStreamPlaybackService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): MultiStreamPlaybackService = this@MultiStreamPlaybackService
    }

    private val binder = LocalBinder()
    private val players = linkedMapOf<String, ExoPlayer>()
    private var audioStreamUrl: String? = null
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            stopAll()
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
        } catch (_: Exception) {
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    @Synchronized
    fun playStream(url: String, drmEnabled: Boolean, licenseUrl: String, authorization: String) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "يجب استخدام رابط بث HTTPS صالح" }
        require(players.size < MAX_STREAMS || players.containsKey(url)) { "الحد الأقصى أربعة بثوث متزامنة" }
        if (players.containsKey(url)) return // keep an already-running stream alive when the grid is reopened
        val license = if (drmEnabled) Uri.parse(licenseUrl) else null
        require(!drmEnabled || (license?.scheme == "https" && !license.host.isNullOrBlank())) {
            "رابط ترخيص Widevine غير صالح"
        }
        val item = if (drmEnabled) {
            val drm = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(license!!)
            if (authorization.isNotBlank()) drm.setLicenseRequestHeaders(mapOf("Authorization" to authorization))
            MediaItem.Builder().setUri(uri).setDrmConfiguration(drm.build()).build()
        } else MediaItem.fromUri(uri)
        val player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                handlePlayerError(url, player)
            }
        })
        try {
            player.volume = 0f // previews stay muted until the user selects one audio stream
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaItem(item)
            players[url] = player
            player.prepare()
            player.playWhenReady = true
            startAsForeground()
            updateNotification()
        } catch (error: Throwable) {
            if (players[url] === player) players.remove(url)
            player.release()
            if (audioStreamUrl == url) audioStreamUrl = null
            if (players.isEmpty()) stopPlaybackService()
            else updateNotification()
            throw error
        }
    }

    @Synchronized
    fun activeStreamUrls(): List<String> = players.keys.toList()

    @Synchronized
    fun playerFor(url: String): ExoPlayer? = players[url]

    /** Route audible output from one selected stream while all four videos continue. */
    @Synchronized
    fun selectAudioStream(url: String?) {
        require(url == null || players.containsKey(url)) { "البث الصوتي غير موجود" }
        audioStreamUrl = url
        players.forEach { (streamUrl, player) -> player.volume = if (streamUrl == url) 1f else 0f }
    }

    @Synchronized
    fun selectedAudioStream(): String? = audioStreamUrl

    @Synchronized
    fun stopStream(url: String) {
        players.remove(url)?.release()
        if (audioStreamUrl == url) audioStreamUrl = null
        if (players.isEmpty()) stopPlaybackService()
        else updateNotification()
    }

    @Synchronized
    fun stopAll() {
        players.values.forEach { it.release() }
        players.clear()
        audioStreamUrl = null
        stopPlaybackService()
    }

    @Synchronized
    private fun handlePlayerError(url: String, failedPlayer: ExoPlayer) {
        if (players[url] !== failedPlayer) return
        players.remove(url)
        if (audioStreamUrl == url) audioStreamUrl = null
        failedPlayer.release()
        if (players.isEmpty()) stopPlaybackService()
        else updateNotification()
    }

    private fun stopPlaybackService() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VIP X • تشغيل البثوث")
            .setContentText("يعمل الآن ${players.size} من 4 بثوث متزامنة")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "تشغيل البثوث", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        players.values.toList().forEach { it.release() }
        players.clear()
        audioStreamUrl = null
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_ALL = "com.vipx.ultimate.STOP_ALL_STREAMS"
        private const val CHANNEL_ID = "vipx_multi_stream"
        private const val NOTIFICATION_ID = 4204
        const val MAX_STREAMS = 4
    }
}
