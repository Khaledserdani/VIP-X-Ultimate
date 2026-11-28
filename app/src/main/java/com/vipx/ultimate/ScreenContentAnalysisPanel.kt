package com.vipx.ultimate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Android's system consent is mandatory for every screen-capture session.
 * This panel never attempts to start capture invisibly or reuse an old grant.
 */
@Composable
internal fun ScreenContentAnalysisPanel() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var running by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("تحليل محتوى الشاشة متوقف") }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(context, ScreenCaptureAnalysisService::class.java).apply {
                action = ScreenCaptureAnalysisService.ACTION_START
                putExtra(ScreenCaptureAnalysisService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureAnalysisService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            running = true
            status = "تمت الموافقة • جارٍ بدء التحليل المحلي"
        } else {
            running = false
            status = "ألغيت موافقة مشاركة الشاشة؛ لم يبدأ أي التقاط"
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ScreenCaptureAnalysisService.ACTION_STATUS) return
                status = intent.getStringExtra(ScreenCaptureAnalysisService.EXTRA_STATUS)
                    ?: "حالة تحليل الشاشة غير معروفة"
                detected = intent.getBooleanExtra(ScreenCaptureAnalysisService.EXTRA_DETECTED, false)
                if (status.contains("إيقاف") || status.contains("أوقف") || status.contains("توقف") || status.contains("ألغى")) {
                    running = false
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ScreenCaptureAnalysisService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF173A3D))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("تحليل الأجسام على الشاشة • بث مباشر", color = Color(0xFF43E0C6))
            Text(
                "يحلل VIP X إطارات الشاشة التي يسمح بها Android، ويستخدم محرك الجسم نفسه الخاص بالكاميرا. لا يسجل الشاشة أو الصوت، ولا يحفظ الإطارات أو يرفعها أو يعيد إرسال البث. قد تمنع التطبيقات المحمية التقاط محتواها.",
                color = Color(0xFFA8B9CA)
            )
            Button(
                onClick = {
                    val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    consentLauncher.launch(manager.createScreenCaptureIntent())
                },
                enabled = activity != null && !running,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("مشاركة الشاشة وتحليل الأجسام")
            }
            OutlinedButton(
                onClick = {
                    context.startService(
                        Intent(context, ScreenCaptureAnalysisService::class.java).apply {
                            action = ScreenCaptureAnalysisService.ACTION_STOP
                        }
                    )
                    running = false
                    detected = false
                    status = "تم طلب إيقاف تحليل الشاشة"
                },
                enabled = running,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إيقاف تحليل الشاشة فوراً")
            }
            Text(status, color = Color.White)
            if (detected) {
                Text("تم رصد جسم في الإطار الحالي", color = Color(0xFF43E0C6))
            }
        }
    }
}
