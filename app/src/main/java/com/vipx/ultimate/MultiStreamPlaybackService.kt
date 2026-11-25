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
        startAsForeground()
        return START_STICKY
    }

    @Synchronized
    fun playStream(url: String, drmEnabled: Boolean, licenseUrl: String, authorization: String) {
        require(url.startsWith("https://")) { "يجب استخدام HTTPS للبث" }
        require(players.size < MAX_STREAMS || players.containsKey(url)) { "الحد الأقصى أربعة بثوث متزامنة" }
        if (players.containsKey(url)) return // keep an already-running stream alive when the grid is reopened
        val player = ExoPlayer.Builder(this).build().also { players[url] = it }
        player.volume = 0f // four concurrent previews; audio focus is reserved for explicit foreground player
        player.repeatMode = Player.REPEAT_MODE_OFF
        val uri = Uri.parse(url)
        val item = if (drmEnabled) {
            val license = Uri.parse(licenseUrl)
            require(license.scheme == "https" && !license.host.isNullOrBlank()) { "رابط ترخيص Widevine غير صالح" }
            val drm = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(license)
            if (authorization.isNotBlank()) drm.setLicenseRequestHeaders(mapOf("Authorization" to authorization))
            MediaItem.Builder().setUri(uri).setDrmConfiguration(drm.build()).build()
        } else MediaItem.fromUri(uri)
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        startAsForeground()
        updateNotification()
    }

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
        if (players.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else updateNotification()
    }

    @Synchronized
    fun stopAll() {
        players.values.forEach { it.release() }
        players.clear()
        audioStreamUrl = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        players.values.forEach { it.release() }
        players.clear()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_ALL = "com.vipx.ultimate.STOP_ALL_STREAMS"
        private const val CHANNEL_ID = "vipx_multi_stream"
        private const val NOTIFICATION_ID = 4204
        const val MAX_STREAMS = 4
    }
}
