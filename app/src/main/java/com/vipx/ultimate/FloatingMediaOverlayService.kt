package com.vipx.ultimate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/** Shows one user-selected local media item in a movable overlay. */
class FloatingMediaOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var player: ExoPlayer? = null
    private var copiedFile: File? = null
    private var transferredSourceFile: File? = null
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }
        val incomingUri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        transferredSourceFile?.delete()
        val transferablePath = incomingUri
            ?.takeIf { it.scheme == "file" && !it.path.isNullOrBlank() }
            ?.path
        transferredSourceFile = if (
            intent?.getBooleanExtra(EXTRA_TRANSFER_FILE_OWNERSHIP, false) == true
        ) transferablePath?.let(::File) else null

        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val uri = incomingUri
        val mime = intent?.getStringExtra(EXTRA_MIME).orEmpty()
        if (uri == null || !(mime.startsWith("image/") || mime.startsWith("video/"))) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeOverlay()
        releasePlayer()
        copiedFile?.delete()
        copiedFile = null
        try {
            if (mime.startsWith("video/")) startPlaybackForeground()
            showMedia(uri, mime)
        } catch (_: Exception) {
            removeOverlay()
            releasePlayer()
            copiedFile?.delete()
            copiedFile = null
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startPlaybackForeground() {
        if (isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "نافذة وسائط VIP X", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VIP X • تشغيل عائم")
            .setContentText("الفيديو يعمل في النافذة العائمة")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun showMedia(source: Uri, mime: String) {
        val mediaUri = if (source.scheme == "file") {
            val original = File(source.path ?: run { stopSelf(); return })
            if (!original.isFile || !original.canRead()) { stopSelf(); return }
            val copy = File(cacheDir, "vipx-floating-${System.nanoTime()}${original.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".media"}")
            try {
                original.copyTo(copy, overwrite = true)
                copiedFile = copy
                Uri.fromFile(copy)
            } catch (_: Exception) {
                copy.delete()
                stopSelf()
                return
            }
        } else source

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(7))
            background = GradientDrawable().apply {
                setColor(0xF20A1726.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF42E3C2.toInt())
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = if (mime.startsWith("image/")) "VIP X • صورة عائمة" else "VIP X • فيديو عائم"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        val close = TextView(this).apply {
            text = "إغلاق"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { stopSelf() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(64), dp(42)))
        container.addView(header)

        title.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = root?.layoutParams as? WindowManager.LayoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        runCatching { windowManager?.updateViewLayout(root, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })

        val content = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        if (mime.startsWith("image/")) {
            content.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(mediaUri)
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val activePlayer = ExoPlayer.Builder(this).build()
            player = activePlayer
            activePlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            activePlayer.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer.prepare()
            activePlayer.playWhenReady = true
            content.addView(PlayerView(this).apply {
                useController = true
                player = activePlayer
            }, FrameLayout.LayoutParams(-1, -1))
        }
        container.addView(content, LinearLayout.LayoutParams(-1, dp(214)))

        val params = WindowManager.LayoutParams(
            dp(320), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(90) }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        root = container
        try {
            windowManager?.addView(container, params)
            transferredSourceFile?.delete()
            transferredSourceFile = null
        } catch (_: SecurityException) {
            root = null
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            root = null
            stopSelf()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun removeOverlay() {
        root?.let { view -> runCatching { windowManager?.removeView(view) } }
        root = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removeOverlay()
        releasePlayer()
        copiedFile?.delete()
        copiedFile = null
        transferredSourceFile?.delete()
        transferredSourceFile = null
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE = "com.vipx.ultimate.CLOSE_FLOATING_MEDIA"
        const val EXTRA_URI = "com.vipx.ultimate.FLOATING_MEDIA_URI"
        const val EXTRA_MIME = "com.vipx.ultimate.FLOATING_MEDIA_MIME"
        const val EXTRA_TRANSFER_FILE_OWNERSHIP = "com.vipx.ultimate.FLOATING_MEDIA_TRANSFER_FILE_OWNERSHIP"
        private const val CHANNEL_ID = "vipx_floating_media"
        private const val NOTIFICATION_ID = 4205
    }
}
