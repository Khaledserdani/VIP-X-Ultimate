package com.vipx.ultimate

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class LocalAgentFinding(val title: String, val result: String)
data class LocalApkReport(
    val appName: String,
    val packageName: String,
    val sha256: String,
    val version: String,
    val minSdk: Int,
    val targetSdk: Int,
    val apkBytes: Long,
    val dexFiles: List<String>,
    val nativeLibraries: List<String>,
    val layoutResources: Int,
    val drawableResources: Int,
    val permissions: List<String>,
    val grantedPermissions: List<String>,
    val signerSha256: String,
    val agents: List<LocalAgentFinding>
) {
    fun asArabicText(): String = buildString {
        append("تحليل محلي على الهاتف — دون إرسال ملفات أو بيانات\n")
        append("التطبيق: ").append(appName).append("\n")
        append("الحزمة: ").append(packageName).append("\n")
        append("الإصدار: ").append(version).append("\n")
        append("حجم APK الأساسي: ").append(apkBytes).append(" بايت\n")
        append("SHA-256 للملف الأساسي: ").append(sha256).append("\n")
        append("بصمة شهادة التوقيع: ").append(signerSha256).append("\n")
        append("Android الأدنى/المستهدف: ").append(minSdk).append(" / ").append(targetSdk).append("\n")
        append("ملفات DEX: ").append(dexFiles.size).append(" | المكتبات الأصلية: ").append(nativeLibraries.size).append("\n")
        append("موارد الواجهات: ").append(layoutResources).append(" | الصور والرسوم: ").append(drawableResources).append("\n")
        append("الصلاحيات المطلوبة: ").append(permissions.size).append(" | الممنوحة: ").append(grantedPermissions.size).append("\n\n")
        agents.forEach { append("◆ ").append(it.title).append("\n").append(it.result).append("\n\n") }
        append("ملاحظة: هذا فحص بنيوي محلي بقواعد واضحة، وليس نموذج ذكاء اصطناعي توليدي أو تدقيقاً أمنياً كاملاً.")
    }
}

object LocalApkAnalyzer {
    private val sensitivePermissions = listOf(
        "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS", "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG", "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS", "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.QUERY_ALL_PACKAGES"
    )

    @Suppress("DEPRECATION")
    fun analyze(context: Context, packageName: String): LocalApkReport {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
        val appInfo = info.applicationInfo ?: error("تعذر قراءة معلومات التطبيق")
        val baseApk = File(appInfo.sourceDir)
        require(baseApk.isFile && baseApk.canRead()) { "ملف APK الأساسي غير قابل للقراءة" }

        val apkFiles = buildList {
            add(baseApk)
            appInfo.splitSourceDirs?.map(::File)?.filter { it.isFile && it.canRead() }?.let { addAll(it) }
        }
        val entries = apkFiles.flatMap { file ->
            ZipFile(file).use { zip ->
                val names = mutableListOf<String>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) names += enumeration.nextElement().name
                names
            }
        }.distinct()
        val dex = entries.filter { it.matches(Regex("classes(\\d*)\\.dex")) }.sorted()
        val native = entries.filter { it.startsWith("lib/") && it.endsWith(".so") }.sorted()
        val layouts = entries.count { it.startsWith("res/layout") && it.endsWith(".xml") }
        val drawables = entries.count { it.startsWith("res/drawable") }
        val requested = info.requestedPermissions?.toList() ?: emptyList()
        val granted = requested.filterIndexed { index, _ ->
            val permissionFlags = info.requestedPermissionsFlags
            permissionFlags != null && index < permissionFlags.size &&
                (permissionFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }
        val signer = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let(::sha256Bytes) ?: "غير متاح"
        } else "غير متاح في إصدار Android هذا"
        val digest = sha256File(baseApk)
        val version = info.versionName ?: "غير معروف"
        val minSdk = appInfo.minSdkVersion
        val targetSdk = appInfo.targetSdkVersion
        val bytes = apkFiles.sumOf { it.length() }
        val risky = requested.filter { it in sensitivePermissions }

        val agents = listOf(
            LocalAgentFinding("مساعد البنية", buildString {
                append("تم فحص ").append(apkFiles.size).append(" ملف APK (أساسي/منفصل). ")
                append("عُثر على ").append(dex.size).append(" ملف DEX و").append(native.size).append(" مكتبة أصلية. ")
                append(if (entries.contains("resources.arsc")) "جدول الموارد موجود." else "جدول الموارد غير ظاهر في الملفات المفحوصة.")
            }),
            LocalAgentFinding("مساعد الأمان", buildString {
                append("رُصدت ").append(risky.size).append(" صلاحية تستحق المراجعة من قائمة الصلاحيات الحساسة المحددة.")
                if (risky.isNotEmpty()) append("\n").append(risky.joinToString("\n"))
                else append(" لم تُرصد صلاحيات من هذه القائمة.")
                append("\nهذه إشارة مراجعة وليست حكماً بأن التطبيق ضار.")
            }),
            LocalAgentFinding("مساعد التصميم", "عُثر على $layouts مورد تخطيط XML و$drawables مورداً للصور/الرسوم. ملفات APK المترجمة لا تكشف دائماً المصدر الأصلي للواجهات؛ لذلك لا يصدر هذا الفحص حكماً بصرياً على جودة التصميم."),
            LocalAgentFinding("مساعد الهندسة", buildString {
                append("الحد الأدنى Android ").append(minSdk).append("، والمستهدف ").append(targetSdk).append(". ")
                append("الحجم الإجمالي للملفات المفحوصة ").append(bytes).append(" بايت. ")
                append(if (dex.isEmpty()) "لم يُعثر على DEX قياسي؛ يلزم فحص يدوي." else "بنية DEX موجودة.")
                append(" لا يثبت هذا التحليل إمكانية إعادة بناء التطبيق أو توافقه مع كل الأجهزة.")
            })
        )
        return LocalApkReport(
            appName = pm.getApplicationLabel(appInfo).toString(),
            packageName = packageName,
            sha256 = digest,
            version = version,
            minSdk = minSdk,
            targetSdk = targetSdk,
            apkBytes = bytes,
            dexFiles = dex,
            nativeLibraries = native,
            layoutResources = layouts,
            drawableResources = drawables,
            permissions = requested,
            grantedPermissions = granted,
            signerSha256 = signer,
            agents = agents
        )
    }

    fun saveLastReport(context: Context, packageName: String, report: String) {
        context.getSharedPreferences("vipx_local_analysis", Context.MODE_PRIVATE)
            .edit().putString("last_report_$packageName", report.take(24000))
            .putLong("last_report_at_$packageName", System.currentTimeMillis()).apply()
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
