package com.vipx.ultimate

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
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
            if (!closed.get()) onError(error)
            return
        }

        synchronized(lock) {
            if (pending || closed.get()) {
                copy.recycle()
                return
            }
            pending = true
            try {
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
            } catch (rejected: RejectedExecutionException) {
                pending = false
                if (!copy.isRecycled) copy.recycle()
                if (!closed.get()) onError(rejected)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            // Queue engine disposal behind any inference already running. Closing
            // MediaPipe concurrently with detect() can crash native resources.
            runCatching {
                executor.execute {
                    runCatching { engine.close() }
                    gate.reset()
                }
                executor.shutdown()
            }.onFailure {
                executor.shutdownNow()
                runCatching { engine.close() }
                gate.reset()
            }
        }
    }
}
