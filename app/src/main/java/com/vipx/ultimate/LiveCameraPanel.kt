package com.vipx.ultimate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * User-started, on-device CameraX preview and pose inference.
 * Frames remain in memory and are discarded immediately after analysis.
 */
@Composable
internal fun LiveCameraPanel() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("الكاميرا متوقفة. شغّلها عند رغبتك.") }
    var detected by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) running = true else status = "لم تمنح إذن الكاميرا."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12263B))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("الكاميرا المباشرة • تحليل محلي", color = Color(0xFF43E0C6))
            Text("يبدأ التحليل فقط بعد ضغطك ومنح إذن الكاميرا. لا نحفظ الإطارات ولا نرفعها.", color = Color(0xFFA8B9CA))
            Button(
                onClick = {
                    if (!permissionGranted) permissionRequest.launch(Manifest.permission.CAMERA)
                    else running = !running
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (running) "إيقاف الكاميرا" else "تشغيل الكاميرا وتحليل الجسم")
            }
            if (running && activity != null) {
                CameraXLivePreview(
                    activity = activity,
                    onStatus = { status = it },
                    onDetection = { detected = it }
                )
            }
            Text(status, color = Color.White)
            if (detected) Text("تم رصد وضعية جسم • 33 نقطة هيكلية محتملة", color = Color(0xFF43E0C6))
        }
    }
}

@Composable
private fun CameraXLivePreview(
    activity: ComponentActivity,
    onStatus: (String) -> Unit,
    onDetection: (Boolean) -> Unit
) {
    val context = activity.applicationContext
    val previewView = remember { PreviewView(activity).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val processor = remember {
        LiveFrameProcessor(
            context = context,
            onFrame = { frame ->
                activity.runOnUiThread {
                    onDetection(frame?.hasReliablePose() == true)
                    onStatus(
                        if (frame?.hasReliablePose() == true) "الكاميرا تعمل • تم تتبع الجسم"
                        else "الكاميرا تعمل • لم تُكتشف وضعية موثوقة في هذا الإطار"
                    )
                }
            },
            onError = { error ->
                activity.runOnUiThread { onStatus("خطأ في تحليل الكاميرا: ${error.message ?: "غير معروف"}") }
            }
        )
    }

    DisposableEffect(activity) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        onStatus("جارٍ تشغيل الكاميرا...")
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        val bitmap = image.toBitmap()
                        try {
                            // CameraX timestamps are monotonic nanoseconds.
                            processor.submit(bitmap, image.imageInfo.timestamp / 1_000_000L)
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    } catch (error: Throwable) {
                        activity.runOnUiThread {
                            onStatus("تعذر تجهيز إطار الكاميرا: ${error.message ?: "خطأ"}")
                        }
                    } finally {
                        image.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                onStatus("الكاميرا تعمل • وجّهها نحو الجسم")
            } catch (error: Throwable) {
                onStatus("تعذر تشغيل الكاميرا: ${error.message ?: "خطأ"}")
            }
        }, ContextCompat.getMainExecutor(activity))

        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
            cameraExecutor.shutdown()
            processor.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(300.dp)
    )
}
