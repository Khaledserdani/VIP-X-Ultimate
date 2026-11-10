package com.vipx.ultimate

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF081421)
private val Panel = Color(0xFF122437)
private val Accent = Color(0xFF36D6C5)
private val Muted = Color(0xFF9EB1C4)

data class InstalledApp(val label: String, val packageName: String, val version: String, val launchIntent: Intent?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VipXHome(loadLaunchableApps()) { app -> app.launchIntent?.let(::startActivity) }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { info ->
            val ai = info.activityInfo ?: return@mapNotNull null
            val pi = try { packageManager.getPackageInfo(ai.packageName, 0) } catch (_: Exception) { null }
            InstalledApp(info.loadLabel(packageManager).toString(), ai.packageName, pi?.versionName ?: "غير معروف", packageManager.getLaunchIntentForPackage(ai.packageName))
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}

@Composable
private fun VipXHome(apps: List<InstalledApp>, onOpen: (InstalledApp) -> Unit) {
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    Surface(Modifier.fillMaxSize(), color = Navy) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIP X", fontSize = 30.sp, color = Color.White)
                    Text("ULTIMATE • منصة تحليل وتحسين التطبيقات", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Default.AutoAwesome, null, tint = Accent, modifier = Modifier.size(34.dp))
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مختبر التطبيقات", style = MaterialTheme.typography.titleLarge)
                    Text("اختر تطبيقاً لعرض المعلومات المتاحة عنه.", color = Muted)
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("ابحث عن تطبيق") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureChip(Icons.Default.Apps, "تطبيقات الهاتف")
                        FeatureChip(Icons.Default.Security, "تحليل آمن")
                    }
                }
            }
            Text("التطبيقات المتاحة (${filtered.size})", style = MaterialTheme.typography.titleMedium)
            if (filtered.isEmpty()) Text("لم يتم العثور على تطبيقات مطابقة.", color = Muted)
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(Modifier.fillMaxWidth().clickable { selected = app }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = Color.White)
                                Text(app.packageName, color = Muted, fontSize = 11.sp)
                            }
                            Text("فحص", color = Accent)
                        }
                    }
                }
            }
            selected?.let { app ->
                AlertDialog(onDismissRequest = { selected = null }, title = { Text("بطاقة معلومات أولية") },
                    text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الاسم: ${app.label}")
                        Text("الحزمة: ${app.packageName}")
                        Text("الإصدار: ${app.version}")
                        Text("هذه بيانات تعريفية فقط، وليست وصولاً إلى ملفات التطبيق الخاصة أو شيفرته المصدرية.")
                    } },
                    confirmButton = { TextButton(onClick = { onOpen(app); selected = null }) { Text("فتح التطبيق") } },
                    dismissButton = { TextButton(onClick = { selected = null }) { Text("إغلاق") } })
            }
            Text("النواة التأسيسية • لا تعدّل التطبيقات ولا تتجاوز اشتراكاتها", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun FeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(color = Color(0xFF1A354A), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(15.dp))
            Text(label, color = Color.White, fontSize = 11.sp)
        }
    }
}
