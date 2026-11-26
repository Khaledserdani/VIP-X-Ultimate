package com.vipx.ultimate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

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
    // Every UI PlayerView is registered here so it can be detached synchronously
    // before its ExoPlayer is released, including Compose grid previews.
    private val attachedPlayerViews = linkedMapOf<String, MutableSet<PlayerView>>()
    private var audioStreamUrl: String? = null
    private var isForeground = false
    private var streamsChangedListener: (() -> Unit)? = null
    private var audioSelectionChangedListener: ((String?) -> Unit)? = null

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
        // Promote only after playStream has installed a real player; a bound/empty service
        // must not leave a misleading foreground notification running.
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
        // Four simultaneous previews must not each use the default long buffer.
        // Bound each player's media buffer to 12 MiB and 12 seconds to reduce
        // aggregate memory pressure while retaining a short startup buffer.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1_500,  // minimum buffer
                12_000, // maximum buffer
                500,    // buffer required to start playback
                1_500   // buffer required after rebuffering
            )
            .setTargetBufferBytes(12 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
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
            notifyStreamsChanged()
        } catch (error: Throwable) {
            if (players[url] === player) players.remove(url)
            player.release()
            if (audioStreamUrl == url) audioStreamUrl = null
            if (players.isEmpty()) stopPlaybackService()
            else updateNotification()
            notifyStreamsChanged()
            throw error
        }
    }

    @Synchronized
    fun setStreamsChangedListener(listener: (() -> Unit)?) {
        streamsChangedListener = listener
    }

    private fun notifyStreamsChanged() {
        streamsChangedListener?.invoke()
    }

    private fun notifyAudioSelectionChanged() {
        audioSelectionChangedListener?.invoke(audioStreamUrl)
    }

    @Synchronized
    fun setAudioSelectionChangedListener(listener: ((String?) -> Unit)?) {
        audioSelectionChangedListener = listener
    }

    @Synchronized
    fun activeStreamUrls(): List<String> = players.keys.toList()

    @Synchronized
    fun playerFor(url: String): ExoPlayer? = players[url]

    @Synchronized
    fun attachPlayerView(url: String, view: PlayerView) {
        attachedPlayerViews.getOrPut(url) { linkedSetOf() }.add(view)
        view.player = players[url]
    }

    @Synchronized
    fun detachPlayerView(url: String, view: PlayerView) {
        attachedPlayerViews[url]?.let { views ->
            views.remove(view)
            if (views.isEmpty()) attachedPlayerViews.remove(url)
        }
        view.player = null
    }

    private fun detachPlayerViews(url: String) {
        attachedPlayerViews.remove(url)?.forEach { view -> view.player = null }
    }

    private fun detachAllPlayerViews() {
        attachedPlayerViews.values.flatten().forEach { view -> view.player = null }
        attachedPlayerViews.clear()
    }

    /** Route audible output from one selected stream while all four videos continue. */
    @Synchronized
    fun selectAudioStream(url: String?) {
        require(url == null || players.containsKey(url)) { "البث الصوتي غير موجود" }
        audioStreamUrl = url
        players.forEach { (streamUrl, player) -> player.volume = if (streamUrl == url) 1f else 0f }
        notifyAudioSelectionChanged()
    }

    @Synchronized
    fun selectedAudioStream(): String? = audioStreamUrl

    @Synchronized
    fun stopStream(url: String) {
        val removedPlayer = players.remove(url)
        if (audioStreamUrl == url) audioStreamUrl = null
        if (players.isEmpty()) stopPlaybackService()
        else updateNotification()
        notifyAudioSelectionChanged()
        // Detach grid and overlay PlayerViews before releasing their shared player.
        detachPlayerViews(url)
        notifyStreamsChanged()
        removedPlayer?.release()
    }

    @Synchronized
    fun stopAll() {
        val removedPlayers = players.values.toList()
        players.clear()
        audioStreamUrl = null
        stopPlaybackService()
        notifyAudioSelectionChanged()
        // Detach every registered grid/overlay view while players are still valid.
        detachAllPlayerViews()
        notifyStreamsChanged()
        removedPlayers.forEach { it.release() }
    }

    @Synchronized
    private fun handlePlayerError(url: String, failedPlayer: ExoPlayer) {
        if (players[url] !== failedPlayer) return
        players.remove(url)
        if (audioStreamUrl == url) audioStreamUrl = null
        if (players.isEmpty()) stopPlaybackService()
        else updateNotification()
        notifyAudioSelectionChanged()
        // Detach every registered view before releasing the failed player's instance.
        detachPlayerViews(url)
        notifyStreamsChanged()
        failedPlayer.release()
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

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, MultiStreamPlaybackService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VIP X • تشغيل البثوث")
            .setContentText("يعمل الآن ${players.size} من 4 بثوث متزامنة")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الكل", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "تشغيل البثوث", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        val remainingPlayers = players.values.toList()
        players.clear()
        audioStreamUrl = null
        streamsChangedListener = null
        audioSelectionChangedListener = null
        detachAllPlayerViews()
        remainingPlayers.forEach { it.release() }
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
