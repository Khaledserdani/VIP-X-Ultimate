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
                openCryptoLab = { startActivity(Intent(this, CryptoLabActivity::class.java)) }
            ) }
        }
    }
}

@Composable
private fun VipHome(
    canWriteSettings: () -> Boolean,
    requestSettings: () -> Unit,
    applyBrightness: (Float) -> Unit,
    openCryptoLab: () -> Unit
) {
    var brightness by remember { mutableFloatStateOf(0.75f) }
    var permissionGranted by remember { mutableStateOf(canWriteSettings()) }
    var message by remember { mutableStateOf("تحكم في سطوع الشاشة على مستوى النظام بعد منح الإذن.") }

    Surface(modifier = Modifier.fillMaxSize(), color = VipBg) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VIP X", color = VipAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("مساعد المشاهدة", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("أدوات فعلية ومجانية لتحسين تجربة المشاهدة ضمن صلاحيات Android.", color = VipMuted, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = VipPanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("سطوع الشاشة العام", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("يؤثر على شاشة الهاتف حتى عند الانتقال إلى تطبيق فيديو آخر. لا يغيّر دقة الفيديو أو جودته من الخادم.", color = VipMuted, fontSize = 12.sp)
                    Text("${(brightness * 100).toInt()}%", color = VipAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = 0.08f..1f
                    )
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
                "مهم: VIP X لا يعترض فيديو التطبيقات الأخرى ولا يكسر DRM. كل ميزة تُضاف يجب أن تعمل فعلياً وتُختبر قبل وصفها بأنها تحسين.",
                color = VipMuted, fontSize = 11.sp
            )
        }
    }
}
