package com.vipx.ultimate

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class VideoPoseReport(
    val durationMs: Long,
    val sampledFrames: Int,
    val reliablePoseFrames: Int,
    val segmentationFrames: Int,
    val overlayReadyFrames: Int,
    val samplingIntervalMs: Long,
    val previewFrame: Bitmap?
)

internal object VideoPoseSampling {
    const val MAX_FRAMES = 1800

    fun intervalMs(durationMs: Long): Long {
        require(durationMs > 0L) { "duration must be positive" }
        val target = when {
            durationMs <= 30_000L -> 125L
            durationMs <= 120_000L -> 250L
            else -> 500L
        }
        return maxOf(target, (durationMs / MAX_FRAMES).coerceAtLeast(1L))
    }

    fun frameCount(durationMs: Long): Int {
        val interval = intervalMs(durationMs)
        return ((durationMs + interval - 1L) / interval).coerceIn(1L, MAX_FRAMES.toLong()).toInt()
    }
}


/**
 * Draws a simple translucent garment silhouette over the selected source frame.
 * It paints only garment pixels; it never warps, scales, or edits the source body.
 * This is a 2D proof-of-pipeline preview, not a fitted cloth simulation.
 */
internal object GarmentOverlayRenderer {
    fun render(source: Bitmap, mesh: GarmentOverlayMesh): Bitmap? {
        if (!mesh.valid || mesh.vertices.size < 11) return null
        val scale = minOf(1f, 720f / maxOf(source.width, source.height))
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createScaledBitmap(source, width, height, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(output)
        val points = mesh.vertices
        fun px(index: Int) = points[index].x * width
        fun py(index: Int) = points[index].y * height

        val garment = AndroidPath().apply {
            moveTo(px(0), py(0))
            lineTo(px(1), py(1))
            lineTo(px(7), py(7))
            lineTo(px(9), py(9))
            lineTo(px(10), py(10))
            lineTo(px(8), py(8))
            lineTo(px(6), py(6))
            close()
        }
        val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(112, 55, 185, 170)
            style = AndroidPaint.Style.FILL
        }
        val stroke = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(235, 210, 255, 246)
            style = AndroidPaint.Style.STROKE
            strokeWidth = (width / 220f).coerceAtLeast(2f)
        }
        canvas.drawPath(garment, fill)
        canvas.drawPath(garment, stroke)
        return output
    }
}

internal object VideoPoseAnalyzer {
    fun analyze(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): VideoPoseReport {
        val retriever = MediaMetadataRetriever()
        var tracker: HumanBodyTrackingEngine? = null
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("تعذر قراءة مدة الفيديو")
            require(duration > 0L) { "الفيديو فارغ أو غير صالح" }
            tracker = HumanBodyTrackingEngine(context)
            // Dense sampling for short clips, bounded for long clips.
            val interval = VideoPoseSampling.intervalMs(duration)
            val count = VideoPoseSampling.frameCount(duration)
            var reliable = 0
            var masks = 0
            var overlayReady = 0
            var previewFrame: Bitmap? = null
            var sampled = 0
            for (index in 0 until count) {
                val timeMs = if (count == 1) 0L else index * interval
                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue
                try {
                    val frame = tracker.detect(bitmap, timeMs)
                    sampled++
                    if (frame?.hasReliablePose() == true) reliable++
                    if (frame?.segmentationMaskAvailable == true) masks++
                    if (frame != null) {
                        val mesh = PoseGarmentRig.build(frame)
                        if (mesh.valid) {
                            overlayReady++
                            if (previewFrame == null) previewFrame = GarmentOverlayRenderer.render(bitmap, mesh)
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                onProgress(((index + 1) * 100 / count).coerceIn(0, 100))
            }
            return VideoPoseReport(duration, sampled, reliable, masks, overlayReady, interval, previewFrame)
        } finally {
            tracker?.close()
            retriever.release()
        }
    }
}

@Composable
internal fun VideoPoseAnalysisPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اختر فيديو من هاتفك لتحليل الحركة محلياً") }
    var report by remember { mutableStateOf<VideoPoseReport?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            busy = true
            report = null
            progress = 0
            status = "جارٍ تحليل إطارات الفيديو على الهاتف..."
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        VideoPoseAnalyzer.analyze(context, uri) { value ->
                            Handler(Looper.getMainLooper()).post { progress = value }
                        }
                    }
                    report = result
                    status = if (result.reliablePoseFrames > 0) {
                        "اكتمل تتبع الحركة الأولي"
                    } else {
                        "لم تُكتشف وضعية جسم موثوقة في الإطارات المختارة"
                    }
                } catch (error: Exception) {
                    status = "تعذر تحليل الفيديو: ${error.message ?: "خطأ غير معروف"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12263B))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("تحليل فيديو الحركة • محلياً", color = Color(0xFF43E0C6))
            Text(
                "يختار المستخدم الفيديو بنفسه. كثافة التحليل 2–8 إطارات/ثانية حسب طول الفيديو، حتى 1800 إطار. لا يوجد تسجيل خفي ولا رفع للفيديو.",
                color = Color(0xFFA8B9CA)
            )
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "جارٍ التحليل..." else "اختر فيديو لتحليل الحركة") }
            if (busy) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("تقدم التحليل: $progress%", color = Color(0xFF43E0C6))
            }
            Text(status, color = Color.White)
            report?.let { result ->
                Text("مدة الفيديو: ${result.durationMs / 1000} ثانية", color = Color.White)
                Text("الإطارات المفحوصة: ${result.sampledFrames}", color = Color.White)
                Text("إطارات بوضعية موثوقة: ${result.reliablePoseFrames}", color = Color.White)
                Text("إطارات توفرت فيها خريطة فصل الجسم: ${result.segmentationFrames}", color = Color.White)
                Text("إطارات صالحة لقالب اللباس الهندسي: ${result.overlayReadyFrames}", color = Color.White)
                Text("الفاصل بين اللقطات: ${result.samplingIntervalMs} مللي ثانية", color = Color.White)
                result.previewFrame?.let { preview ->
                    Text("معاينة أولية لقالب اللباس فوق إطار حقيقي من الفيديو", color = Color(0xFF43E0C6))
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "معاينة هندسية أولية للملابس فوق إطار الفيديو",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    "قالب اللباس الهندسي مرحلة أولية فقط؛ لم يُرسم اللباس فوق الفيديو بعد، وهذه ليست قياسات محيط الجسم.",
                    color = Color(0xFFFFD18A)
                )
            }
        }
    }
}
