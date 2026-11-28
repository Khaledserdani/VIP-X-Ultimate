package com.vipx.ultimate

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared local inference stage for CameraX and consented MediaProjection frames.
 * The source owns incoming frame buffers; this processor makes a bounded copy
 * before queuing work and never stores or uploads frames.
 */
internal class LiveFrameProcessor(
    context: Context,
    private val onFrame: (TrackedBodyFrame?) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) : AutoCloseable {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val gate = LatestFrameGate(100L)
    private val engine = HumanBodyTrackingEngine(context.applicationContext)
    private val lock = Any()
    private var pending = false

    fun submit(frame: Bitmap, timestampMs: Long) {
        if (closed.get() || !gate.shouldAnalyze(timestampMs)) return
        val copy = try {
            frame.copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: Throwable) {
            onError(error)
            return
        }
        synchronized(lock) {
            if (pending || closed.get()) {
                copy.recycle()
                return
            }
            pending = true
        }
        executor.execute {
            try {
                if (!closed.get()) {
                    val result = engine.detect(copy, timestampMs)
                    if (!closed.get()) onFrame(result)
                }
            } catch (error: Throwable) {
                if (!closed.get()) onError(error)
            } finally {
                if (!copy.isRecycled) copy.recycle()
                synchronized(lock) { pending = false }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
        runCatching { engine.close() }
        gate.reset()
    }
}
