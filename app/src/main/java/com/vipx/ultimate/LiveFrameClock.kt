package com.vipx.ultimate

/**
 * Utilities shared by CameraX and MediaProjection frame sources.
 * Timestamps are monotonic milliseconds as required by MediaPipe VIDEO mode.
 */
internal class LiveFrameClock(
    private val minimumIntervalMs: Long = 100L
) {
    init {
        require(minimumIntervalMs >= 0L)
    }

    private var lastAcceptedMs = -1L

    @Synchronized
    fun accept(timestampMs: Long): Boolean {
        if (timestampMs < 0L) return false
        if (lastAcceptedMs >= 0L && timestampMs - lastAcceptedMs < minimumIntervalMs) {
            return false
        }
        lastAcceptedMs = timestampMs
        return true
    }

    @Synchronized
    fun nextAfter(timestampMs: Long): Long {
        val candidate = timestampMs.coerceAtLeast(0L)
        val next = if (lastAcceptedMs < 0L) candidate else maxOf(candidate, lastAcceptedMs + 1L)
        lastAcceptedMs = next
        return next
    }

    @Synchronized
    fun reset() {
        lastAcceptedMs = -1L
    }

    @Synchronized
    fun lastTimestampMs(): Long? = lastAcceptedMs.takeIf { it >= 0L }
}

/** Latest-frame queue policy: never accumulate stale camera/screen frames. */
internal class LatestFrameGate(
    private val minimumIntervalMs: Long = 100L
) {
    private val clock = LiveFrameClock(minimumIntervalMs)

    fun shouldAnalyze(timestampMs: Long): Boolean = clock.accept(timestampMs)

    fun reset() = clock.reset()
}
