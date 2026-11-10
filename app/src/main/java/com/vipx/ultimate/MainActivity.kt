package com.vipx.ultimate

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

private val Navy = Color(0xFF081421)
private val Panel = Color(0xFF122437)
private val Accent = Color(0xFF36D6C5)
private val Muted = Color(0xFF9EB1C4)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val version: String,
    val apkPath: String,
    val apkBytes: Long,
    val sha256: String,
    val firstInstalled: Long,
    val lastUpdated: Long,
    val permissions: List<String>,
    val splitCount: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VipXHome(
                        loadLaunchableApps(),
                        onRefresh = { loadLaunchableApps() },
                        onOpen = { app -> packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity) },
                        onSettings = { app ->
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                        },
                        onCloud = { startActivity(Intent(this, CloudAssistantActivity::class.java)) }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
        }
        return matches.mapNotNull { info ->
            val ai = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == packageName) return@mapNotNull null
            try {
                val pi = if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getPackageInfo(ai.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    packageManager.getPackageInfo(ai.packageName, PackageManager.GET_PERMISSIONS)
                }
                val apk = File(ai.sourceDir)
                InstalledApp(
                    label = packageManager.getApplicationLabel(ai).toString(),
                    packageName = ai.packageName,
                    version = pi.versionName ?: "غير معروف",
                    apkPath = ai.sourceDir,
                    apkBytes = apk.length(),
                    sha256 = sha256(apk),
                    firstInstalled = pi.firstInstallTime,
                    lastUpdated = pi.lastUpdateTime,
                    permissions = pi.requestedPermissions?.toList() ?: emptyList(),
                    splitCount = ai.splitSourceDirs?.size ?: 0
                )
            } catch (_: Exception) { null }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun sha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "تعذر الحساب" }
    }
}

@Composable
private fun VipXHome(
    initialApps: List<InstalledApp>,
    onRefresh: () -> List<InstalledApp>,
    onOpen: (InstalledApp) -> Unit,
    onSettings: (InstalledApp) -> Unit,
    onCloud: () -> Unit
) {
    var apps by remember { mutableStateOf(initialApps) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var showPermissions by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("جاهز لفحص التطبيقات المثبتة") }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        val app = selected
        if (uri != null && app != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(File(app.apkPath)).use { input -> input.copyTo(output) }
                }
                message = "تم تصدير ملف APK الأساسي"
            } catch (e: Exception) {
                message = "تعذر التصدير: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    Surface(Modifier.fillMaxSize(), color = Navy) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIP X ULTIMATE", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("مدير ومحلّل التطبيقات على هاتفك", color = Muted, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCloud) { Text("الذكاء السحابي", color = Accent) }
                    Icon(Icons.Default.Security, null, tint = Accent, modifier = Modifier.size(28.dp))
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("مركز فحص التطبيقات", style = MaterialTheme.typography.titleLarge)
                    Text("عرض معلومات الحزمة، حجم APK، بصمة SHA-256 والصلاحيات المعلنة.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("ابحث بالاسم أو اسم الحزمة") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("التطبيقات: ${filtered.size}", color = Accent, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            apps = onRefresh()
                            message = "تم تحديث قائمة التطبيقات"
                        }) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(4.dp))
                            Text("تحديث")
                        }
                    }
                }
            }

            Text(message, color = Muted, fontSize = 11.sp)
            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد نتائج مطابقة", color = Muted)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Card(
                            Modifier.fillMaxWidth().clickable { selected = app },
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Apps, null, tint = Accent, modifier = Modifier.size(27.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(app.packageName, color = Muted, fontSize = 10.sp)
                                    Text("الإصدار ${app.version} • ${formatSize(app.apkBytes)}", color = Muted, fontSize = 11.sp)
                                }
                                Text("تفاصيل", color = Accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Text("أداة محلية • لا تتجاوز اشتراكات التطبيقات أو حمايتها", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null; showPermissions = false },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("اسم الحزمة", app.packageName)
                    InfoLine("الإصدار", app.version)
                    InfoLine("حجم APK الأساسي", formatSize(app.apkBytes))
                    InfoLine("عدد الصلاحيات المعلنة", app.permissions.size.toString())
                    InfoLine("ملفات split الإضافية", app.splitCount.toString())
                    InfoLine("تاريخ التثبيت", formatDate(app.firstInstalled))
                    InfoLine("آخر تحديث", formatDate(app.lastUpdated))
                    Text("SHA-256", color = Accent, fontWeight = FontWeight.Bold)
                    Text(app.sha256, color = Muted, fontSize = 10.sp)
                    if (app.splitCount > 0) Text("تنبيه: هذا التطبيق يستخدم ملفات split؛ التصدير أدناه يحفظ APK الأساسي فقط.", color = Color(0xFFFFC66D), fontSize = 11.sp)
                    if (showPermissions) {
                        Divider()
                        Text("الصلاحيات المعلنة", fontWeight = FontWeight.Bold)
                        app.permissions.forEach { Text("• $it", fontSize = 10.sp, color = Muted) }
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showPermissions = !showPermissions }) { Text(if (showPermissions) "إخفاء الصلاحيات" else "عرض الصلاحيات") }
                    TextButton(onClick = { onOpen(app); selected = null }) { Text("فتح التطبيق") }
                    TextButton(onClick = { onSettings(app) }) { Text("إعدادات التطبيق") }
                    TextButton(onClick = { exportLauncher.launch("${app.packageName}.apk") }) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(4.dp))
                        Text("تصدير APK الأساسي")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null; showPermissions = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatDate(time: Long): String =
    if (time <= 0L) "غير متاح" else DateFormat.getDateTimeInstance().format(Date(time))
