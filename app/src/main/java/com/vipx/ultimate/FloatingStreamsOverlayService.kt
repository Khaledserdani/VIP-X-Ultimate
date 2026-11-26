package com.vipx.ultimate

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.ui.PlayerView

/** Floating previews for streams already configured in VIP X; never inspects other apps. */
class FloatingStreamsOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var playbackService: MultiStreamPlaybackService? = null
    private var isBound = false

    private val playbackConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playbackService = (binder as? MultiStreamPlaybackService.LocalBinder)?.service()
            if (playbackService == null) {
                stopSelf()
                return
            }
            playbackService?.setStreamsChangedListener { showOverlay() }
            showOverlay()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService?.setStreamsChangedListener(null)
            removeOverlay()
            playbackService = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isBound) {
            isBound = bindService(Intent(this, MultiStreamPlaybackService::class.java),
                playbackConnection, Context.BIND_AUTO_CREATE)
            if (!isBound) stopSelf()
        } else showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        val engine = playbackService ?: return
        val urls = engine.activeStreamUrls().take(MultiStreamPlaybackService.MAX_STREAMS)
        if (urls.isEmpty()) {
            // Detach PlayerViews immediately; onDestroy may run after the playback
            // service releases the now-removed players.
            removeOverlay()
            stopSelf()
            return
        }
        removeOverlay()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(0xF20A1726.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF42E3C2.toInt())
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "VIP X • معاينات عائمة"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(Button(this).apply {
            text = "إغلاق"
            isAllCaps = false
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(dp(76), dp(42)))
        root.addView(header)

        title.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = overlayRoot?.layoutParams as? WindowManager.LayoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        runCatching { windowManager?.updateViewLayout(overlayRoot, params) }
                        return true
                    }
                }
                return false
            }
        })

        urls.chunked(2).forEachIndexed { rowIndex, rowUrls ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowUrls.forEachIndexed { columnIndex, url ->
                val cell = FrameLayout(this).apply { setBackgroundColor(0xFF172B40.toInt()) }
                val playerView = PlayerView(this).apply {
                    useController = false
                    player = engine.playerFor(url)
                    setBackgroundColor(0xFF000000.toInt())
                }
                cell.addView(playerView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                cell.addView(TextView(this).apply {
                    text = "بث " + (rowIndex * 2 + columnIndex + 1)
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 10f
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                    setBackgroundColor(0x99000000.toInt())
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START))
                row.addView(cell, LinearLayout.LayoutParams(0, dp(104), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            if (rowUrls.size == 1) {
                row.addView(TextView(this).apply {
                    text = "أضف بثاً آخر من شبكة VIP X"
                    setTextColor(0xFF9FB2C7.toInt())
                    textSize = 10f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(0, dp(104), 1f))
            }
            root.addView(row)
        }

        val params = WindowManager.LayoutParams(
            dp(336), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(72) }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayRoot = root
        try {
            windowManager?.addView(root, params)
        } catch (_: SecurityException) {
            // addView failed: the views were already connected to live players.
            // Detach them even though WindowManager never accepted the root.
            detachPlayers(root)
            overlayRoot = null
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            detachPlayers(root)
            overlayRoot = null
            stopSelf()
        }
    }

    private fun detachPlayers(root: LinearLayout? = overlayRoot) {
        root?.let {
            for (index in 0 until it.childCount) {
                val row = it.getChildAt(index) as? LinearLayout ?: continue
                for (cellIndex in 0 until row.childCount) {
                    val cell = row.getChildAt(cellIndex) as? FrameLayout ?: continue
                    for (childIndex in 0 until cell.childCount) {
                        (cell.getChildAt(childIndex) as? PlayerView)?.player = null
                    }
                }
            }
        }
    }

    private fun removeOverlay() {
        detachPlayers()
        overlayRoot?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayRoot = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removeOverlay()
        if (isBound) {
            runCatching { unbindService(playbackConnection) }
            isBound = false
        }
        playbackService?.setStreamsChangedListener(null)
        playbackService = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE = "com.vipx.ultimate.CLOSE_FLOATING_PREVIEWS"
    }
}
