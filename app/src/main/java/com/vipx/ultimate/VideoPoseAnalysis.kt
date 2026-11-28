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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
    val postureFrames: Int,
    val maxTorsoLeanDegrees: Float,
    val maxShoulderTiltDegrees: Float,
    val maxHipTiltDegrees: Float,
    val maxLeftElbowFlexionDegrees: Float,
    val maxRightElbowFlexionDegrees: Float,
    val maxForwardBackBendDegrees: Float?,
    val surfaceComparedFrames: Int,
    val maxContourChangePercent: Float,
    val maxLocalBandChangePercent: Float,
    val samplingIntervalMs: Long,
    val previewFrames: List<Bitmap>
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
    fun render(source: Bitmap, mesh: GarmentOverlayMesh, colorHex: String, garmentStyle: SheerGarmentStyle): Bitmap? {
        if (!mesh.valid || mesh.vertices.size < 11) return null
        val scale = minOf(1f, 720f / maxOf(source.width, source.height))
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createScaledBitmap(source, width, height, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(output)
        val p = mesh.vertices
        fun x(i: Int) = p[i].x * width
        fun y(i: Int) = p[i].y * height
        val base = runCatching { AndroidColor.parseColor(colorHex) }.getOrDefault(AndroidColor.WHITE)
        fun paint(alpha: Int, style: AndroidPaint.Style = AndroidPaint.Style.FILL, stroke: Float = 2f) =
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(alpha, AndroidColor.red(base), AndroidColor.green(base), AndroidColor.blue(base))
                this.style = style
                strokeWidth = stroke
                strokeJoin = AndroidPaint.Join.ROUND
                strokeCap = AndroidPaint.Cap.ROUND
            }
        fun polygon(vararg ids: Int): AndroidPath = AndroidPath().apply {
            moveTo(x(ids[0]), y(ids[0]))
            for (i in 1 until ids.size) lineTo(x(ids[i]), y(ids[i]))
            close()
        }
        fun armSleeve(shoulder: Int, elbow: Int, wrist: Int, widthFactor: Float): AndroidPath {
            val sx=x(shoulder); val sy=y(shoulder)
            val ex=x(elbow); val ey=y(elbow)
            val wx=x(wrist); val wy=y(wrist)
            fun normal(ax: Float, ay: Float, bx: Float, by: Float): Pair<Float,Float> {
                val dx=bx-ax; val dy=by-ay
                val len=kotlin.math.sqrt(dx*dx+dy*dy).coerceAtLeast(1f)
                return Pair(-dy/len, dx/len)
            }
            val n1=normal(sx,sy,ex,ey); val n2=normal(ex,ey,wx,wy)
            val sw=(kotlin.math.abs(x(1)-x(0))*0.105f*widthFactor).coerceAtLeast(5f)
            val ew=sw*0.78f; val ww=sw*0.58f
            return AndroidPath().apply {
                moveTo(sx+n1.first*sw, sy+n1.second*sw)
                lineTo(ex+n1.first*ew, ey+n1.second*ew)
                lineTo(wx+n2.first*ww, wy+n2.second*ww)
                lineTo(wx-n2.first*ww, wy-n2.second*ww)
                lineTo(ex-n1.first*ew, ey-n1.second*ew)
                lineTo(sx-n1.first*sw, sy-n1.second*sw)
                close()
            }
        }
        val shoulderSpan = kotlin.math.abs(x(1)-x(0))
        val hemY = maxOf(y(8), y(9)) + shoulderSpan*0.08f
        val waistLeft = x(0) + (x(8)-x(0))*0.48f
        val waistRight = x(1) + (x(9)-x(1))*0.48f
        val torso = AndroidPath().apply {
            moveTo(x(0)-shoulderSpan*0.035f, y(0))
            lineTo(x(1)+shoulderSpan*0.035f, y(1))
            lineTo(waistRight, y(7))
            lineTo(x(9)+shoulderSpan*0.035f, hemY)
            lineTo(x(8)-shoulderSpan*0.035f, hemY)
            lineTo(waistLeft, y(6))
            close()
        }
        val leftSleeve = armSleeve(0,2,4,1f)
        val rightSleeve = armSleeve(1,3,5,1f)
        val lining = paint(238)
        val sheer = paint(112)
        val seam = paint(225, AndroidPaint.Style.STROKE, (width/300f).coerceAtLeast(1.5f))
        val detail = paint(175, AndroidPaint.Style.STROKE, (width/420f).coerceAtLeast(1f))

        // A modest opaque base layer preserves coverage; the selected fabric is drawn above it.
        canvas.drawPath(torso, lining)
        when (garmentStyle) {
            SheerGarmentStyle.CHIFFON_OVERLAY -> {
                val drape = AndroidPath().apply {
                    moveTo(x(0),y(0)); lineTo(x(1),y(1))
                    lineTo(x(1)+shoulderSpan*.17f,hemY+shoulderSpan*.10f)
                    lineTo(x(0)-shoulderSpan*.17f,hemY+shoulderSpan*.10f); close()
                }
                canvas.drawPath(drape, paint(86))
                canvas.drawPath(leftSleeve, paint(100)); canvas.drawPath(rightSleeve, paint(100))
                canvas.drawPath(drape, seam)
                val fold=AndroidPath().apply {
                    moveTo((x(0)+x(1))/2f,y(1))
                    cubicTo(x(1)+shoulderSpan*.10f,y(0)+shoulderSpan*.35f,
                        x(0)-shoulderSpan*.10f,y(7), (x(0)+x(1))/2f,hemY)
                }
                canvas.drawPath(fold,detail)
            }
            SheerGarmentStyle.TULLE_LAYERED -> {
                canvas.drawPath(torso, paint(95))
                for (layer in 1..3) {
                    val offset=shoulderSpan*(0.035f*layer)
                    val ruffle=AndroidPath().apply {
                        moveTo(x(8)-offset,y(8)+shoulderSpan*(.025f*layer))
                        cubicTo(x(8),hemY-offset,x(9),hemY-offset,
                            x(9)+offset,y(9)+shoulderSpan*(.025f*layer))
                    }
                    canvas.drawPath(ruffle, paint(170- layer*18,AndroidPaint.Style.STROKE, (width/170f).coerceAtLeast(2f)))
                }
                canvas.drawPath(leftSleeve,paint(90)); canvas.drawPath(rightSleeve,paint(90))
                canvas.drawPath(torso,seam)
            }
            SheerGarmentStyle.LACE_OVERLAY -> {
                canvas.drawPath(torso,paint(75))
                canvas.drawPath(leftSleeve,paint(155)); canvas.drawPath(rightSleeve,paint(155))
                canvas.drawPath(torso,seam)
                val lace=paint(185,AndroidPaint.Style.STROKE,(width/260f).coerceAtLeast(1f))
                for (row in 0..5) {
                    val yy=y(0)+(hemY-y(0))*(row/6f)
                    val inset=shoulderSpan*(0.10f+row*0.012f)
                    val path=AndroidPath().apply {
                        moveTo(x(0)+inset,yy); lineTo(x(1)-inset,yy)
                    }
                    canvas.drawPath(path,lace)
                }
            }
            SheerGarmentStyle.ORGANZA_LAYER -> {
                val jacket=AndroidPath().apply {
                    moveTo(x(0)-shoulderSpan*.04f,y(0)); lineTo(x(1)+shoulderSpan*.04f,y(1))
                    lineTo(x(1)+shoulderSpan*.08f,y(7))
                    lineTo(x(0)-shoulderSpan*.08f,y(7)); close()
                }
                canvas.drawPath(jacket,paint(125)); canvas.drawPath(leftSleeve,paint(118)); canvas.drawPath(rightSleeve,paint(118))
                val opening=AndroidPath().apply { moveTo((x(0)+x(1))/2f,y(0)); lineTo((x(0)+x(1))/2f,y(7)) }
                canvas.drawPath(opening,seam); canvas.drawPath(jacket,seam)
            }
            SheerGarmentStyle.MESH_PANEL -> {
                canvas.drawPath(torso,paint(245))
                val panel=AndroidPath().apply {
                    moveTo(x(0)+(x(1)-x(0))*.16f,y(0))
                    lineTo(x(0)+(x(1)-x(0))*.31f,y(0))
                    lineTo(x(8)+(x(9)-x(8))*.31f,hemY)
                    lineTo(x(8)+(x(9)-x(8))*.16f,hemY); close()
                }
                canvas.drawPath(panel,paint(105)); canvas.drawPath(leftSleeve,paint(235)); canvas.drawPath(rightSleeve,paint(235))
                canvas.drawPath(panel,seam); canvas.drawPath(torso,seam)
            }
        }
        canvas.drawPath(leftSleeve,seam)
        canvas.drawPath(rightSleeve,seam)
        return output
    }
}
internal object VideoPoseAnalyzer {
    fun analyze(
        context: Context,
        uri: Uri,
        colorHex: String,
        garmentStyle: SheerGarmentStyle,
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
            var postureFrames = 0
            var maxTorsoLean = 0f
            var maxShoulderTilt = 0f
            var maxHipTilt = 0f
            var maxLeftElbow = 0f
            var maxRightElbow = 0f
            var maxForwardBackBend: Float? = null
            var referenceSilhouette: PersonSilhouette? = null
            var surfaceComparedFrames = 0
            var maxContourChange = 0f
            var maxLocalBandChange = 0f
            var previousGarmentMesh: GarmentOverlayMesh? = null
            val previewFrames = ArrayList<Bitmap>(5)
            val previewTargets = (0..4).map { target -> ((count - 1) * target / 4f).toInt() }.toSet()
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
                    frame?.silhouette?.let { silhouette ->
                        if (referenceSilhouette == null) referenceSilhouette = silhouette
                        else {
                            val surfaceChange = BodySurfaceChangeAnalyzer.compare(referenceSilhouette!!, silhouette)
                            if (surfaceChange.reliable) {
                                surfaceComparedFrames++
                                maxContourChange = maxOf(maxContourChange, surfaceChange.meanContourChangePercent)
                                maxLocalBandChange = maxOf(maxLocalBandChange, surfaceChange.largestBandChangePercent)
                            }
                        }
                    }
                    if (frame != null) {
                        val motion = PoseMotionAnalyzer.analyze(frame)
                        if (motion != null) {
                            postureFrames++
                            maxTorsoLean = maxOf(maxTorsoLean, kotlin.math.abs(motion.torsoLeanDegrees))
                            maxShoulderTilt = maxOf(maxShoulderTilt, kotlin.math.abs(motion.shoulderTiltDegrees))
                            maxHipTilt = maxOf(maxHipTilt, kotlin.math.abs(motion.hipTiltDegrees))
                            motion.leftElbowFlexionDegrees?.let { maxLeftElbow = maxOf(maxLeftElbow, it) }
                            motion.rightElbowFlexionDegrees?.let { maxRightElbow = maxOf(maxRightElbow, it) }
                            motion.forwardBackBendDegrees?.let { maxForwardBackBend = maxOf(maxForwardBackBend ?: 0f, kotlin.math.abs(it)) }
                        }
                        val rawMesh = PoseGarmentRig.build(frame)
                        if (rawMesh.valid) {
                            overlayReady++
                            val stableMesh = GarmentTemporalStabilizer.smooth(previousGarmentMesh, rawMesh)
                            previousGarmentMesh = stableMesh
                            if (index in previewTargets && previewFrames.size < 5) {
                                GarmentOverlayRenderer.render(bitmap, stableMesh, colorHex, garmentStyle)?.let(previewFrames::add)
                            }
                        } else {
                            // Do not bridge across missing/invalid pose frames.
                            previousGarmentMesh = null
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                onProgress(((index + 1) * 100 / count).coerceIn(0, 100))
            }
            return VideoPoseReport(duration, sampled, reliable, masks, overlayReady, postureFrames, maxTorsoLean, maxShoulderTilt, maxHipTilt, maxLeftElbow, maxRightElbow, maxForwardBackBend, surfaceComparedFrames, maxContourChange, maxLocalBandChange, interval, previewFrames)
        } finally {
            tracker?.close()
            retriever.release()
        }
    }
}

@Composable
internal fun VideoPoseAnalysisPanel(selectedColorHex: String, selectedStyle: SheerGarmentStyle) {
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
                        VideoPoseAnalyzer.analyze(context, uri, selectedColorHex, selectedStyle) { value ->
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
                Text("إطارات تحليل وضعية الجسم: ${result.postureFrames}", color = Color.White)
                Text("إطارات مقارنة محيط الشكل الظاهر: ${result.surfaceComparedFrames}", color = Color.White)
                Text("أكبر تغير متوسط في حدود الشكل: ${result.maxContourChangePercent.toInt()}%", color = Color.White)
                Text("أكبر تغير محلي في منطقة من الشكل: ${result.maxLocalBandChangePercent.toInt()}%", color = Color.White)
                Text("أقصى ميل للجذع: ${result.maxTorsoLeanDegrees.toInt()}°", color = Color.White)
                Text("أقصى ميل للكتفين: ${result.maxShoulderTiltDegrees.toInt()}°", color = Color.White)
                Text("أقصى ميل للوركين: ${result.maxHipTiltDegrees.toInt()}°", color = Color.White)
                Text("أقصى ثني للمرفق الأيسر/الأيمن: ${result.maxLeftElbowFlexionDegrees.toInt()}° / ${result.maxRightElbowFlexionDegrees.toInt()}°", color = Color.White)
                result.maxForwardBackBendDegrees?.let { Text("مؤشر انحناء أمامي/خلفي تقريبي: ${it.toInt()}°", color = Color.White) }
                Text("الفاصل بين اللقطات: ${result.samplingIntervalMs} مللي ثانية", color = Color.White)
                if (result.previewFrames.isNotEmpty()) {
                    Text("لقطات متتابعة للّباس فوق إطارات مختلفة من الفيديو", color = Color(0xFF43E0C6))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        result.previewFrames.forEachIndexed { index, preview ->
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text("لقطة ${index + 1}", color = Color.White)
                                Image(
                                    bitmap = preview.asImageBitmap(),
                                    contentDescription = "معاينة هندسية للّباس في اللقطة ${index + 1}",
                                    modifier = Modifier.width(220.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                Text(
                    "تحليل القناع يقارن حدود الجسم الظاهرة في الصورة على شرائح نسبية؛ قد تتأثر النتائج بالملابس والاحتجاب والمنظور ولا تمثل محيطات حقيقية بالسنتيمتر. زوايا المفاصل مؤشرات وضعية وليست قياسات طبية. اللقطات المعروضة عينات متباعدة من الفيديو وليست فيديو نهائياً متحركاً؛ لا تزال المحاكاة بحاجة إلى ربط القماش بكل إطار وتصدير فيديو.",
                    color = Color(0xFFFFD18A)
                )
            }
        }
    }
}
