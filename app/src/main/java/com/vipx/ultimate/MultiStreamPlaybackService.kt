package com.vipx.ultimate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
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
    private val gridPlayerViews = linkedMapOf<String, MutableSet<PlayerView>>()
    private var gridVisible = false
    private var audioStreamUrl: String? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var focusRequestActive = false
    private var audioDucked = false
    private var isForeground = false
    private var streamsChangedListener: (() -> Unit)? = null
    private var audioSelectionChangedListener: ((String?) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener(::onAudioFocusChanged, Handler(Looper.getMainLooper()))
            .setWillPauseWhenDucked(true)
            .build()
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
        // Each of the four independent players gets a smaller target buffer.
        // This is a LoadControl target, not a hard process-memory cap.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1_000, // minimum buffer
                8_000, // maximum buffer
                500,   // buffer required to start playback
                1_000  // buffer required after rebuffering
            )
            .setTargetBufferBytes(8 * 1024 * 1024)
            .setBackBuffer(0, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            // The service arbitrates focus for the single audible preview; muted
            // players must not independently compete for system audio focus.
            .setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false
            )
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
            // A bound-only service may have lost its started state after the last
            // stream stopped or failed. Re-start it before foreground promotion so
            // restored streams and manual retries share a valid service lifecycle.
            startService(Intent(this, MultiStreamPlaybackService::class.java))
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
        // A Media3 video renderer has one active output surface per player. When the
        // floating window takes ownership, detach the grid surface rather than
        // competing with it; the grid surface is restored when the overlay detaches.
        gridPlayerViews[url].orEmpty().forEach { gridView -> gridView.player = null }
        attachedPlayerViews[url].orEmpty().filter { it !== view }.forEach { other ->
            if (other !in gridPlayerViews[url].orEmpty()) other.player = null
        }
        view.player = players[url]
        updatePlaybackState(url)
    }

    @Synchronized
    fun attachGridPlayerView(url: String, view: PlayerView) {
        gridPlayerViews.getOrPut(url) { linkedSetOf() }.add(view)
        attachedPlayerViews.getOrPut(url) { linkedSetOf() }.add(view)
        if (hasFloatingPlayerView(url)) {
            view.player = null
        } else {
            gridPlayerViews[url].orEmpty().filter { it !== view }.forEach { other -> other.player = null }
            view.player = if (gridVisible) players[url] else null
        }
        updatePlaybackState(url)
    }

    private fun hasFloatingPlayerView(url: String): Boolean =
        attachedPlayerViews[url].orEmpty().any { view -> view !in gridPlayerViews[url].orEmpty() }

    private fun restoreGridOutputIfAvailable(url: String) {
        if (!gridVisible || hasFloatingPlayerView(url)) return
        val player = players[url]
        gridPlayerViews[url].orEmpty().forEachIndexed { index, view ->
            view.player = if (index == 0) player else null
        }
    }

    @Synchronized
    fun detachGridPlayerView(url: String, view: PlayerView) {
        gridPlayerViews[url]?.let { views ->
            views.remove(view)
            if (views.isEmpty()) gridPlayerViews.remove(url)
        }
        attachedPlayerViews[url]?.let { views ->
            views.remove(view)
            if (views.isEmpty()) attachedPlayerViews.remove(url)
        }
        view.player = null
        restoreGridOutputIfAvailable(url)
        updatePlaybackState(url)
    }

    @Synchronized
    fun setGridVisible(visible: Boolean) {
        if (gridVisible == visible) return
        gridVisible = visible
        players.keys.toList().forEach { url ->
            if (visible) restoreGridOutputIfAvailable(url)
            else gridPlayerViews[url].orEmpty().forEach { view -> view.player = null }
        }
        updateAllPlaybackStates()
    }

    @Synchronized
    fun detachPlayerView(url: String, view: PlayerView) {
        attachedPlayerViews[url]?.let { views ->
            views.remove(view)
            if (views.isEmpty()) attachedPlayerViews.remove(url)
        }
        view.player = null
        restoreGridOutputIfAvailable(url)
        updatePlaybackState(url)
    }

    private fun detachPlayerViews(url: String) {
        attachedPlayerViews.remove(url)?.forEach { view -> view.player = null }
        gridPlayerViews.remove(url)
        updatePlaybackState(url)
    }

    private fun updatePlaybackState(url: String) {
        val player = players[url] ?: return
        val hasVisibleOutput = gridPlayerViews[url]?.isNotEmpty() == true && gridVisible ||
            attachedPlayerViews[url].orEmpty().any { view ->
                // Floating overlay PlayerViews remain active independently of the grid Activity.
                view !in gridPlayerViews[url].orEmpty()
            }
        val selectedAudibleStream = url == audioStreamUrl && hasAudioFocus
        // Keep visible previews running and preserve explicitly selected audio.
        // Pause hidden, muted previews to avoid needless decoding/network buffering.
        player.playWhenReady = hasVisibleOutput || selectedAudibleStream
    }

    private fun updateAllPlaybackStates() {
        players.keys.toList().forEach(::updatePlaybackState)
    }

    private fun detachAllPlayerViews() {
        attachedPlayerViews.values.flatten().forEach { view -> view.player = null }
        attachedPlayerViews.clear()
        gridPlayerViews.clear()
    }

    /** Route audible output from one selected stream while visible previews continue. */
    @Synchronized
    fun selectAudioStream(url: String?): Boolean {
        require(url == null || players.containsKey(url)) { "البث الصوتي غير موجود" }
        if (url == null) {
            audioStreamUrl = null
            applyPreviewVolumes()
            abandonPreviewAudioFocus()
            updateAllPlaybackStates()
            notifyAudioSelectionChanged()
            return true
        }
        if (!requestPreviewAudioFocus()) {
            audioStreamUrl = null
            applyPreviewVolumes()
            abandonPreviewAudioFocus()
            updateAllPlaybackStates()
            notifyAudioSelectionChanged()
            return false
        }
        audioStreamUrl = url
        applyPreviewVolumes()
        updateAllPlaybackStates()
        notifyAudioSelectionChanged()
        return true
    }

    private fun requestPreviewAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val request = audioFocusRequest ?: return false
        if (focusRequestActive) return hasAudioFocus
        val result = manager.requestAudioFocus(request)
        focusRequestActive = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasAudioFocus = focusRequestActive
        return hasAudioFocus
    }

    private fun abandonPreviewAudioFocus() {
        if (focusRequestActive) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        focusRequestActive = false
        hasAudioFocus = false
        audioDucked = false
    }

    @Synchronized
    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusRequestActive = true
                hasAudioFocus = true
                audioDucked = false
                applyPreviewVolumes()
                updateAllPlaybackStates()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioDucked = true
                applyPreviewVolumes()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                applyPreviewVolumes()
                updateAllPlaybackStates()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                focusRequestActive = false
                audioStreamUrl = null
                audioDucked = false
                applyPreviewVolumes()
                abandonPreviewAudioFocus()
                updateAllPlaybackStates()
                notifyAudioSelectionChanged()
            }
        }
    }

    private fun applyPreviewVolumes() {
        players.forEach { (streamUrl, player) ->
            player.volume = when {
                streamUrl != audioStreamUrl || !hasAudioFocus -> 0f
                audioDucked -> 0.2f
                else -> 1f
            }
        }
    }

    @Synchronized
    fun selectedAudioStream(): String? = audioStreamUrl

    @Synchronized
    fun stopStream(url: String) {
        val removedPlayer = players.remove(url)
        if (audioStreamUrl == url) {
            audioStreamUrl = null
            abandonPreviewAudioFocus()
        }
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
        abandonPreviewAudioFocus()
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
        if (audioStreamUrl == url) {
            audioStreamUrl = null
            abandonPreviewAudioFocus()
        }
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
        abandonPreviewAudioFocus()
        audioFocusRequest = null
        audioManager = null
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
