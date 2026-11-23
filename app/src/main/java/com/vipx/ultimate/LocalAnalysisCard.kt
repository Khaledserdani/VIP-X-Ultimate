package com.vipx.ultimate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LocalAnalysisCard(
    appName: String?,
    packageName: String?,
    onResult: (String) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var localStatus by remember { mutableStateOf("لا يحتاج إلى إنترنت أو حساب أو مفتاح API") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12352F)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("2 • المختبر المحلي المجاني", color = Color(0xFF43E0C6), fontWeight = FontWeight.Bold)
            Text(
                "يفحص APK المثبّت على الهاتف ويشغّل أربعة محللات متخصصة بقواعد محلية. لا يرسل ملف APK أو بياناته إلى أي خادم.",
                color = Color(0xFFA8B9CA), fontSize = 11.sp
            )
            Text(localStatus, color = Color(0xFF43E0C6), fontSize = 10.sp)
            Button(
                onClick = {
                    val pkg = packageName
                    if (pkg.isNullOrBlank()) {
                        localStatus = "اختر تطبيقاً أولاً"
                    } else {
                        busy = true
                        localStatus = "جارٍ تحليل ملفات التطبيق على الهاتف..."
                        scope.launch {
                            try {
                                val report = withContext(Dispatchers.IO) {
                                    LocalApkAnalyzer.analyze(context, pkg).asArabicText()
                                }
                                withContext(Dispatchers.IO) {
                                    LocalApkAnalyzer.saveLastReport(context, pkg, report)
                                }
                                onResult(report)
                                localStatus = "اكتمل الفحص محلياً وحُفظ آخر تقرير على الهاتف"
                                onStatus(localStatus)
                            } catch (error: Exception) {
                                localStatus = "تعذر الفحص المحلي: ${error.message ?: "خطأ غير معروف"}"
                                onStatus(localStatus)
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy && !packageName.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("جارٍ الفحص...")
                } else {
                    Text("حلّل التطبيق وشغّل المحللين الأربعة دون إنترنت")
                }
            }
            OutlinedButton(
                onClick = {
                    val pkg = packageName
                    if (pkg.isNullOrBlank()) {
                        localStatus = "اختر تطبيقاً أولاً"
                    } else {
                        val saved = context.getSharedPreferences("vipx_local_analysis", 0)
                            .getString("last_report_$pkg", null)
                        if (saved.isNullOrBlank()) {
                            localStatus = "لا يوجد تقرير محفوظ لهذا التطبيق بعد"
                        } else {
                            onResult(saved)
                            localStatus = "تم فتح آخر تقرير محفوظ من ذاكرة الهاتف"
                            onStatus(localStatus)
                        }
                    }
                },
                enabled = !busy && !packageName.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("اعرض آخر تقرير محفوظ محلياً") }
        }
    }
}
