package com.vipx.ultimate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val AiMint = Color(0xFF42E3C2)
private val AiMuted = Color(0xFF9FB2C7)

@Composable
internal fun VipLocalAiPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("الذكاء المحلي لم يُحمّل بعد.") }
    var progress by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(VipLocalAgentTeam.lastReport(context).orEmpty()) }
    var modelReady by remember { mutableStateOf(LocalLlmRuntime.isReady()) }

    LaunchedEffect(Unit) {
        while (true) {
            val infos = withContext(Dispatchers.IO) {
                runCatching {
                    WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(ModelDownloadWorker.UNIQUE_WORK_NAME).get()
                }.getOrDefault(emptyList())
            }
            val active = infos.lastOrNull { it.state == androidx.work.WorkInfo.State.RUNNING ||
                it.state == androidx.work.WorkInfo.State.ENQUEUED ||
                it.state == androidx.work.WorkInfo.State.BLOCKED } ?: infos.lastOrNull()
            if (active != null) {
                progress = active.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, progress)
                when (active.state) {
                    androidx.work.WorkInfo.State.RUNNING -> status = "تنزيل Qwen3 في الخلفية: $progress%"
                    androidx.work.WorkInfo.State.ENQUEUED,
                    androidx.work.WorkInfo.State.BLOCKED -> status = "التنزيل ينتظر اتصال الإنترنت."
                    androidx.work.WorkInfo.State.SUCCEEDED -> status = "اكتمل تنزيل النموذج والتحقق من ملفه."
                    androidx.work.WorkInfo.State.FAILED -> status =
                        active.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "فشل التنزيل؛ يمكن استكمال المحاولة."
                    else -> Unit
                }
            }
            modelReady = LocalLlmRuntime.isReady()
            delay(1200)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF122238))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الذكاء المحلي • أربعة مساعدين", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Qwen3 0.6B يعمل على الهاتف دون اشتراك أو API. حجم النموذج نحو 586 MiB ويُنزّل منفصلاً عن APK.", color = AiMuted, fontSize = 12.sp)
                Text(if (modelReady) "حالة النموذج: جاهز" else "حالة النموذج: غير مهيأ", color = if (modelReady) AiMint else AiMuted)
                LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                Text(status, color = AiMuted, fontSize = 12.sp)
                Button(
                    enabled = !busy && !modelReady,
                    onClick = {
                        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            ModelDownloadWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request
                        )
                        status = "أُضيف التنزيل إلى قائمة العمل؛ سيستكمل الجزء المحفوظ عند إعادة المحاولة."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تنزيل / استكمال النموذج") }
                OutlinedButton(
                    enabled = !busy && !modelReady,
                    onClick = {
                        busy = true
                        scope.launch {
                            status = "التحقق من SHA-256 وتهيئة محرك الاستدلال..."
                            try {
                                withContext(Dispatchers.IO) { LocalLlmRuntime.initialize(context) }
                                modelReady = true
                                status = "نجح اختبار الاستدلال المحلي؛ النموذج جاهز دون إنترنت."
                            } catch (e: Exception) {
                                status = "تعذرت التهيئة: ${e.message ?: "خطأ غير معروف"}"
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("التحقق وتفعيل الذكاء المحلي") }
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اكتب المهمة للفريق") },
                    minLines = 2
                )
                Button(
                    enabled = modelReady && !busy && goal.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            status = "المساعدون الأربعة يعملون بالتتابع على الهاتف..."
                            try {
                                val result = withContext(Dispatchers.IO) { VipLocalAgentTeam.run(context, goal.trim()) }
                                report = result.asArabicText()
                                status = "اكتمل تشغيل المساعدين الأربعة والمنسّق محلياً."
                            } catch (e: Exception) {
                                status = "توقف التحليل: ${e.message ?: "خطأ غير معروف"}"
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "جارٍ العمل..." else "شغّل المساعدين الأربعة") }
                OutlinedButton(
                    enabled = report.isNotBlank() && !busy,
                    onClick = {
                        VipLocalAgentTeam.confirmCurrentResults(context)
                        status = "حُفظت تقارير هذه الجولة كمرجع معتمد بناءً على تأكيدك."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اعتماد نتائج الجولة في الذاكرة") }
                if (report.isNotBlank()) {
                    Text("آخر تقرير للفريق", color = AiMint, fontWeight = FontWeight.Bold)
                    Text(
                        report,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
