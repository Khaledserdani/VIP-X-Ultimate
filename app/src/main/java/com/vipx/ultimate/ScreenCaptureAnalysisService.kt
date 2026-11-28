package com.vipx.ultimate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat

/**
 * Consent-based, video-only screen analysis.
 *
 * Android supplies frames only after the system MediaProjection consent dialog.
 * Frames are held briefly in RAM, passed to the same local pose processor as
 * CameraX, and immediately released. This service does not record, persist,
 * upload, forward, or capture audio. FLAG_SECURE/protected surfaces remain
 * subject to Android's black-frame protection.
 */
internal class ScreenCaptureAnalysisService : Service() {
    companion object {
        const val ACTION_START = "com.vipx.ultimate.action.START_SCREEN_ANALYSIS"
        const val ACTION_STOP = "com.vipx.ultimate.action.STOP_SCREEN_ANALYSIS"
        const val ACTION_STATUS = "com.vipx.ultimate.action.SCREEN_ANALYSIS_STATUS"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STATUS = "status"
        const val EXTRA_DETECTED = "detected"
        private const val CHANNEL_ID = "vipx_screen_analysis"
        private const val NOTIFICATION_ID = 7302
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var processor: LiveFrameProcessor? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var stopping = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture("أوقف Android مشاركة الشاشة")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture("تم إيقاف تحليل الشاشة")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_START -> startCapture(intent, startId)
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent, startId: Int) {
        if (projection != null) return
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }
        if (resultCode == 0 || resultData == null) {
            stopCapture("لم تصل موافقة مشاركة الشاشة من Android")
            stopSelf(startId)
            return
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = manager.getMediaProjection(resultCode, resultData)
            projection = mediaProjection
            mediaProjection.registerCallback(projectionCallback, Handler(mainLooper))

            // Reuse the exact same on-device pose engine as the live camera path.
            processor = LiveFrameProcessor(
                context = applicationContext,
                onFrame = { frame ->
                    publish(
                        status = if (frame?.hasReliablePose() == true)
                            "تحليل الشاشة يعمل • تم رصد جسم"
                        else
                            "تحليل الشاشة يعمل • لم يظهر جسم موثوق في الإطار",
                        detected = frame?.hasReliablePose() == true
                    )
                },
                onError = { error ->
                    publish("تعذر تحليل إطار الشاشة: ${error.message ?: "خطأ"}", false)
                }
            )

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val density = metrics.densityDpi

            captureThread = HandlerThread("VIPX-ScreenFrames").also { it.start() }
            captureHandler = Handler(captureThread!!.looper)
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: Throwable) { null }
                    ?: return@setOnImageAvailableListener
                consumeFrame(image)
            }, captureHandler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "VIP X body analysis",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                captureHandler
            )
            publish("تحليل الشاشة نشط • الإطارات مؤقتة ولا تُحفظ أو تُرسل", false)
        } catch (error: Throwable) {
            stopCapture("تعذر بدء تحليل الشاشة: ${error.message ?: "خطأ غير معروف"}")
            stopSelf(startId)
        }
    }

    private fun consumeFrame(image: Image) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride <= 0 || rowStride <= 0) return
            val paddedWidth = rowStride / pixelStride
            if (paddedWidth < image.width) return

            val padded = Bitmap.createBitmap(
                paddedWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            try {
                plane.buffer.rewind()
                padded.copyPixelsFromBuffer(plane.buffer)
                val frame = if (paddedWidth == image.width) padded
                    else Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                try {
                    processor?.submit(frame, SystemClock.elapsedRealtime())
                } finally {
                    if (frame !== padded && !frame.isRecycled) frame.recycle()
                }
            } finally {
                if (!padded.isRecycled) padded.recycle()
            }
        } catch (error: Throwable) {
            publish("تعذر تجهيز إطار الشاشة: ${error.message ?: "خطأ"}", false)
        } finally {
            image.close()
        }
    }

    private fun publish(status: String, detected: Boolean) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_DETECTED, detected)
        )
    }

    private fun stopCapture(message: String) {
        if (stopping) return
        stopping = true
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { processor?.close() }
        processor = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        val oldProjection = projection
        projection = null
        runCatching { oldProjection?.stop() }
        captureHandler = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
        publish(message, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopping = false
    }

    override fun onDestroy() {
        stopCapture("توقف تحليل الشاشة")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تحليل الشاشة في VIP X",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "إشعار واضح أثناء تحليل الشاشة بموافقة المستخدم" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureAnalysisService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            7303,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("VIP X • تحليل الشاشة")
            .setContentText("تحليل محلي مؤقت — اضغط إيقاف لإنهائه")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف التحليل", stopPendingIntent)
            .build()
    }
}
