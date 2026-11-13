package com.vipx.ultimate

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val Navy = Color(0xFF081421)
private val Panel = Color(0xFF122437)
private val Accent = Color(0xFF36D6C5)
private val Muted = Color(0xFF9EB1C4)
private val Warning = Color(0xFFFFC66D)

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val version: String,
    val apkPaths: List<String>,
    val apkBytes: Long,
    val firstInstalled: Long,
    val lastUpdated: Long,
    val permissions: List<String>
) {
    val splitCount: Int get() = (apkPaths.size - 1).coerceAtLeast(0)
}

private data class AppDetails(val app: InstalledApp, val sha256: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 20, 33)
        window.navigationBarColor = android.graphics.Color.rgb(8, 20, 33)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Navy, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VipXHome(
                        loadApps = { loadLaunchableApps() },
                        onOpen = { app -> packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity) },
                        onSettings = { app ->
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName)))
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
                val flags = PackageManager.GET_PERMISSIONS
                val pi = if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getPackageInfo(ai.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    packageManager.getPackageInfo(ai.packageName, flags)
                }
                val paths = mutableListOf<String>()
                if (ai.sourceDir.isNotBlank()) paths.add(ai.sourceDir)
                ai.splitSourceDirs?.filter { it.isNotBlank() }?.let { paths.addAll(it) }
                val bytes = paths.sumOf { File(it).length() }
                InstalledApp(
                    label = packageManager.getApplicationLabel(ai).toString(),
                    packageName = ai.packageName,
                    version = pi.versionName ?: "غير معروف",
                    apkPaths = paths,
                    apkBytes = bytes,
                    firstInstalled = pi.firstInstallTime,
                    lastUpdated = pi.lastUpdateTime,
                    permissions = pi.requestedPermissions?.toList() ?: emptyList()
                )
            } catch (_: Exception) { null }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}

@Composable
private fun VipXHome(
    loadApps: suspend () -> List<InstalledApp>,
    onOpen: (InstalledApp) -> Unit,
    onSettings: (InstalledApp) -> Unit,
    onCloud: () -> Unit
) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var details by remember { mutableStateOf<AppDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("يجري تجهيز قائمة التطبيقات…") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        message = "يجري فحص التطبيقات…"
        try {
            apps = withContext(Dispatchers.IO) { loadApps() }
            message = "اكتمل الفحص • " + apps.size + " تطبيق"
        } catch (e: Exception) {
            message = "تعذر فحص التطبيقات: " + (e.localizedMessage ?: "خطأ غير معروف")
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val app = selected
        if (uri != null && app != null) {
            scope.launch {
                busy = true
                message = "يجري إنشاء النسخة الاحتياطية…"
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            ZipOutputStream(output).use { zip ->
                                app.apkPaths.forEachIndexed { index, path ->
                                    val suffix = if (index == 0) "base.apk" else "split-" + index + ".apk"
                                    zip.putNextEntry(ZipEntry(suffix))
                                    FileInputStream(File(path)).use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                                zip.putNextEntry(ZipEntry("VIPX-backup-info.txt"))
                                zip.write(("Package: " + app.packageName + "\nVersion: " + app.version + "\nAPK files: " + app.apkPaths.size).toByteArray(Charsets.UTF_8))
                                zip.closeEntry()
                            }
                        } ?: error("تعذر فتح ملف الحفظ")
                    }
                    message = "اكتمل تصدير النسخة الاحتياطية"
                } catch (e: Exception) {
                    message = "فشل التصدير: " + (e.localizedMessage ?: "خطأ غير معروف")
                } finally { busy = false }
            }
        }
    }

    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    Surface(Modifier.fillMaxSize(), color = Navy) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIP X ULTIMATE", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("مركز إدارة وفحص التطبيقات", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Default.Security, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فاحص التطبيقات", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("المعلومات تُحمّل في الخلفية حتى تبقى الواجهة سريعة.", color = Muted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("ابحث بالاسم أو اسم الحزمة") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("النتائج: " + filtered.size, color = Accent, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(4.dp))
                            Text("تحديث")
                        }
                        TextButton(onClick = onCloud) { Text("المساعد الذكي", color = Accent) }
                    }
                }
            }
            Text(message, color = Muted, fontSize = 11.sp)
            if (loading && apps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Accent)
                        Text("نجهّز التطبيقات دون تجميد الشاشة", color = Muted)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد نتائج مطابقة", color = Muted)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Card(
                            Modifier.fillMaxWidth().clickable { selected = app; details = null },
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Apps, null, tint = Accent, modifier = Modifier.size(27.dp))
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(app.packageName, color = Muted, fontSize = 10.sp)
                                    Text("الإصدار " + app.version + " • " + formatSize(app.apkBytes), color = Muted, fontSize = 11.sp)
                                }
                                Text("فحص", color = Accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Text("فحص محلي ضمن صلاحيات أندرويد • دون الوصول إلى بيانات التطبيقات الخاصة", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null; details = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("اسم الحزمة", app.packageName)
                    InfoLine("الإصدار", app.version)
                    InfoLine("الحجم الإجمالي لملفات APK", formatSize(app.apkBytes))
                    InfoLine("عدد ملفات APK", app.apkPaths.size.toString())
                    InfoLine("الصلاحيات المعلنة", app.permissions.size.toString())
                    InfoLine("تاريخ التثبيت", formatDate(app.firstInstalled))
                    InfoLine("آخر تحديث", formatDate(app.lastUpdated))
                    if (details == null) {
                        TextButton(onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val hash = withContext(Dispatchers.IO) { sha256(File(app.apkPaths.first())) }
                                    details = AppDetails(app, hash)
                                } catch (_: Exception) { message = "تعذر حساب البصمة" }
                                finally { busy = false }
                            }
                        }, enabled = !busy) { Text(if (busy) "يجري الحساب…" else "حساب بصمة SHA-256 عند الطلب") }
                    } else {
                        Text("SHA-256 للملف الأساسي", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(details!!.sha256, color = Muted, fontSize = 9.sp)
                    }
                    Text("الصلاحيات المعلنة", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    app.permissions.take(8).forEach { Text("• " + it.substringAfterLast("."), fontSize = 10.sp, color = Muted) }
                    if (app.permissions.size > 8) Text("و " + (app.permissions.size - 8) + " صلاحيات أخرى", color = Muted, fontSize = 10.sp)
                    if (app.splitCount > 0) Text("هذا التطبيق متعدد الملفات؛ النسخة الاحتياطية تجمع APK الأساسي وملفات split.", color = Warning, fontSize = 10.sp)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onOpen(app); selected = null }) { Text("فتح التطبيق") }
                    TextButton(onClick = { onSettings(app) }) { Text("إعدادات التطبيق") }
                    TextButton(onClick = { exportLauncher.launch(app.packageName + "-backup.zip") }, enabled = !busy) {
                        Icon(Icons.Default.Archive, null)
                        Spacer(Modifier.width(4.dp))
                        Text("نسخ احتياطي كامل APK")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null; details = null }) { Text("إغلاق") } }
        )
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(16384)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> bytes.toString() + " B"
}

private fun formatDate(time: Long): String =
    if (time <= 0L) "غير متاح" else DateFormat.getDateTimeInstance().format(Date(time))
