package com.vipx.ultimate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MediaCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val candidate = SharedMediaCapture.fromIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF42E3C2),
                background = Color(0xFF07111F),
                surface = Color(0xFF122238)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF07111F)) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("VIP X • استقبال الوسائط", color = Color(0xFF42E3C2), fontSize = 14.sp)
                        Text("استيراد رابط تمت مشاركته", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        if (candidate == null) {
                            Text("لم يصل رابط ويب قابل للاستيراد. ارجع إلى التطبيق الأصلي واختر مشاركة الرابط أو انسخه ثم أرسله إلى VIP X.", color = Color(0xFF9FB2C7))
                            Button(onClick = { finish() }) { Text("رجوع") }
                        } else {
                            Text("المضيف: ${candidate.host}", color = Color.White)
                            Text("التعرّف: ${SharedMediaCapture.label(candidate.kind)}", color = Color(0xFF42E3C2))
                            Text(candidate.url, color = Color(0xFF9FB2C7), fontSize = 11.sp)
                            Text(
                                "هذا رابط شاركه التطبيق أو المستخدم. لا يعني ذلك أنه رابط البث الداخلي أو أن المحتوى غير محمي. لا يستخرج VIP X مفاتيح DRM أو يتجاوز قيود التطبيق.",
                                color = Color(0xFF9FB2C7), fontSize = 12.sp
                            )
                            Button(
                                onClick = {
                                    startActivity(Intent(this@MediaCaptureActivity, PlayerActivity::class.java)
                                        .putExtra(PlayerActivity.EXTRA_STREAM_URL, candidate.url))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("جرّب تشغيل الرابط") }
                            OutlinedButton(
                                onClick = {
                                    startActivity(Intent(this@MediaCaptureActivity, LiveGridActivity::class.java)
                                        .putExtra(LiveGridActivity.EXTRA_IMPORTED_STREAM_URL, candidate.url))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("أضف الرابط إلى شبكة 2×2") }
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
                        }
                    }
                }
            }
        }
    }
}
