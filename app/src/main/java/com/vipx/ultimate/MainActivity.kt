package com.vipx.ultimate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VipBg = Color(0xFF07111F)
private val VipPanel = Color(0xFF122238)
private val VipAccent = Color(0xFF42E3C2)
private val VipMuted = Color(0xFF9FB2C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = VipAccent, background = VipBg, surface = VipPanel,
                    onBackground = Color.White, onSurface = Color.White
                )) { VipHome(
                    canWriteSettings = { Settings.System.canWrite(this) },
                    requestSettings = {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:$packageName"))
                        startActivity(intent)
                    },
                    applyBrightness = { value ->
                        Settings.System.putInt(
                            contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            (value * 255f).toInt().coerceIn(20, 255)
                        )
                    },
                    openCryptoLab = { startActivity(Intent(this, CryptoLabActivity::class.java)) },
                    openLiveGrid = { startActivity(Intent(this, LiveGridActivity::class.java)) },
                    openStream = { url, drm, license, authorization ->
                        startActivity(Intent(this, PlayerActivity::class.java)
                            .putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
                            .putExtra(PlayerActivity.EXTRA_DRM_ENABLED, drm)
                            .putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL, license)
                            .putExtra(PlayerActivity.EXTRA_DRM_AUTHORIZATION, authorization))
                    }
                ) }
            }
        }
    }
}

@Composable
private fun VipHome(
    canWriteSettings: () -> Boolean,
    requestSettings: () -> Unit,
    applyBrightness: (Float) -> Unit,
    openCryptoLab: () -> Unit,
    openLiveGrid: () -> Unit,
    openStream: (String, Boolean, String, String) -> Unit
) {
    var brightness by remember { mutableFloatStateOf(0.75f) }
    var permissionGranted by remember { mutableStateOf(canWriteSettings()) }
    var message by remember { mutableStateOf("تحكم في سطوع الشاشة على مستوى النظام بعد منح الإذن.") }
    var streamUrl by remember { mutableStateOf("") }
    var streamMessage by remember { mutableStateOf("ألصق رابط بث تملك حق مشاهدته.") }
    var drmEnabled by remember { mutableStateOf(false) }
    var licenseUrl by remember { mutableStateOf("") }
    var authorization by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = VipBg) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VIP X", color = VipAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("مساعد المشاهدة", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("مشغّل بث مستقل وأدوات مجانية ضمن صلاحيات Android.", color = VipMuted, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = VipPanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("شبكة البثوث والمعاينات التلقائية", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("أضف حتى 4 روابط بث متاحة لك لتشغيل معايناتها داخل شبكة واحدة.", color = VipMuted, fontSize = 12.sp)
                    Button(onClick = openLiveGrid, modifier = Modifier.fillMaxWidth()) {
                        Text("فتح شبكة البثوث", fontWeight = FontWeight.Bold)
                    }
                    Text("مشغّل البث • تشغيل أثناء التصفح", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("يدعم روابط وسائط مباشرة وصيغ HLS/DASH المتوافقة. بعد بدء التشغيل يمكنك الرجوع لتطبيق آخر ويستمر الصوت عبر خدمة الوسائط وإشعار التحكم.", color = VipMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = streamUrl,
                        onValueChange = { streamUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رابط البث المصرّح به") },
                        placeholder = { Text("https://…") },
                        singleLine = true
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = drmEnabled, onCheckedChange = { drmEnabled = it })
                        Text("تشغيل محتوى محمي بترخيص Widevine", color = Color.White, fontSize = 12.sp)
                    }
                    if (drmEnabled) {
                        OutlinedTextField(value = licenseUrl, onValueChange = { licenseUrl = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("رابط خادم الترخيص HTTPS") },
                            placeholder = { Text("https://license-provider.example/...") }, singleLine = true)
                        OutlinedTextField(value = authorization, onValueChange = { authorization = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Authorization header (اختياري)") },
                            placeholder = { Text("Bearer …") }, singleLine = true)
                        Text("أدخل بيانات ترخيص صادرة لك من مزوّد المحتوى. لا تُحفظ هذه القيم في التطبيق أو المستودع، ولا يمكن تشغيل المحتوى المحمي دون خادم ترخيص صالح.", color = VipMuted, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val parsed = Uri.parse(streamUrl.trim())
                            val license = Uri.parse(licenseUrl.trim())
                            if (parsed.scheme == "https" && !parsed.host.isNullOrBlank() &&
                                (!drmEnabled || (license.scheme == "https" && !license.host.isNullOrBlank()))) {
                                streamMessage = "يتم فتح المشغّل…"
                                openStream(streamUrl.trim(), drmEnabled, licenseUrl.trim(), authorization.trim())
                            } else {
                                streamMessage = if (drmEnabled) "تحقق من رابط البث ورابط ترخيص HTTPS." else "أدخل رابطاً صحيحاً يبدأ بـ https://"
                            }
                        },
                        enabled = streamUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VipAccent, contentColor = VipBg)
                    ) { Text("تشغيل البث", fontWeight = FontWeight.Bold) }
                    Text(streamMessage, color = VipMuted, fontSize = 12.sp)
                    Text("البث المحمي يحتاج ترخيص DRM صالحاً من مزوّد المحتوى؛ لا يتم تجاوز الحماية أو استخراج مفاتيح التطبيقات الأخرى.", color = VipMuted, fontSize = 11.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = VipPanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("سطوع الشاشة العام", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("يؤثر على شاشة الهاتف حتى عند الانتقال إلى تطبيق فيديو آخر. لا يغيّر دقة الفيديو أو جودته من الخادم.", color = VipMuted, fontSize = 12.sp)
                    Text("${(brightness * 100).toInt()}%", color = VipAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.08f..1f)
                    Button(
                        onClick = {
                            permissionGranted = canWriteSettings()
                            if (permissionGranted) {
                                try {
                                    applyBrightness(brightness)
                                    message = "تم تطبيق السطوع على مستوى النظام."
                                } catch (e: Exception) {
                                    message = "تعذر تطبيق السطوع: ${e.message ?: "خطأ غير معروف"}"
                                }
                            } else {
                                requestSettings()
                                message = "فعّل السماح بتعديل إعدادات النظام، ثم ارجع إلى VIP X واضغط تطبيق."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VipAccent, contentColor = VipBg)
                    ) { Text(if (permissionGranted) "تطبيق السطوع" else "منح إذن السطوع", fontWeight = FontWeight.Bold) }
                    Text(message, color = VipMuted, fontSize = 12.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = VipPanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مختبر التشفير المحلي", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("اختبار AES-GCM وفك بيانات اختبار نملك مفتاحها، مع كشف العبث.", color = VipMuted, fontSize = 12.sp)
                    OutlinedButton(onClick = openCryptoLab, modifier = Modifier.fillMaxWidth()) {
                        Text("فتح مختبر التشفير", color = VipAccent)
                    }
                }
            }

            Text(
                "مهم: VIP X لا يعترض فيديو التطبيقات الأخرى ولا يكسر DRM. التشغيل الخلفي يعمل للمحتوى الذي يفتحه المشغّل نفسه، وفق قيود Android ومزوّد البث.",
                color = VipMuted, fontSize = 11.sp
            )
        }
    }
}
