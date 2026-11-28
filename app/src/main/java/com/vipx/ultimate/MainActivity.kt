package com.vipx.ultimate

import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object SecureTokenStore {
    private const val alias = "vipx_access_token_key"
    private const val prefs = "vipx_secure_settings"
    private const val field = "access_token"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    fun save(context: Context, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
            .putString(field, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }
    fun load(context: Context): String {
        return try {
            val stored = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(field, null)
            if (stored.isNullOrBlank()) "" else {
                val bytes = Base64.decode(stored, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            }
        } catch (_: Exception) { "" }
    }
}

private val Navy = Color(0xFF071321)
private val Panel = Color(0xFF12263B)
private val Mint = Color(0xFF43E0C6)
private val Soft = Color(0xFFA8B9CA)
private data class InstalledApp(val name:String,val packageName:String)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(7,19,33)
        window.navigationBarColor=android.graphics.Color.rgb(7,19,33)
        setContent{
            MaterialTheme(colorScheme=darkColorScheme(primary=Mint,background=Navy,surface=Panel)){
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
                    VipBuilderScreen(::installedApps)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedApps():List<InstalledApp>{
        val list=if(Build.VERSION.SDK_INT>=33)
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else packageManager.getInstalledApplications(0)
        return list.mapNotNull{ai->
            if(ai.packageName==packageName)return@mapNotNull null
            val label=runCatching{packageManager.getApplicationLabel(ai).toString()}.getOrDefault(ai.packageName)
            InstalledApp(label,ai.packageName)
        }.distinctBy{it.packageName}.sortedBy{it.name.lowercase()}
    }
}

@Composable
private fun VipBuilderScreen(loadApps:()->List<InstalledApp>){
    val context = LocalContext.current
    val scope= rememberCoroutineScope()
    var apps by remember{mutableStateOf(emptyList<InstalledApp>())}
    var selected by remember{mutableStateOf<InstalledApp?>(null)}
    var query by remember{mutableStateOf("")}
    var goal by remember{mutableStateOf("أنشئ تصوراً احترافياً لنسخة VIP متوافقة، بواجهة راقية وميزات عملية دون تعطيل التطبيق الأصلي.") }
    var endpoint by remember{mutableStateOf("")}
    var token by remember{mutableStateOf(SecureTokenStore.load(context))}
    var answer by remember{mutableStateOf("")}
    var memoryInfo by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("اختر تطبيقاً من هاتفك للبدء")}
    var busy by remember{mutableStateOf(false)}
    var showModelPrompt by remember { mutableStateOf(false) }
    var modelDownloading by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableIntStateOf(0) }
    var modelReady by remember { mutableStateOf(LocalLlmRuntime.isReady()) }
    var modelStatus by remember { mutableStateOf("") }
    var selectedGarmentColorHex by remember { mutableStateOf("#FFFFFF") }
    var selectedGarmentStyle by remember { mutableStateOf(SheerGarmentStyle.CHIFFON_OVERLAY) }

    fun startLocalModelSetup() {
        if (modelDownloading || modelReady) return
        context.getSharedPreferences("vipx_model_setup", Context.MODE_PRIVATE)
            .edit().putBoolean("first_prompt_shown", true).apply()
        showModelPrompt = false
        modelDownloading = true
        modelStatus = "جارٍ تجهيز الذكاء المحلي..."
        scope.launch {
            try {
                LocalLlmRuntime.downloadModel(context) { modelProgress = it }
                modelStatus = "تم التحقق من النموذج؛ جارٍ تشغيله لأول مرة..."
                LocalLlmRuntime.initialize(context)
                modelReady = true
                modelStatus = "الذكاء المحلي جاهز ويعمل دون إنترنت"
            } catch (error: Exception) {
                modelStatus = "تعذر تجهيز الذكاء المحلي: " + (error.message ?: "خطأ غير معروف")
            } finally {
                modelDownloading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps() }
        status = "تم العثور على ${apps.size} تطبيقاً"
        if (LocalLlmRuntime.isModelDownloaded(context)) {
            modelStatus = "النموذج موجود؛ جارٍ تهيئته..."
            try {
                LocalLlmRuntime.initialize(context)
                modelReady = true
                modelStatus = "الذكاء المحلي جاهز ويعمل دون إنترنت"
            } catch (error: Exception) {
                modelStatus = "تعذر تهيئة النموذج المحلي: " + (error.message ?: "خطأ")
            }
        } else if (!context.getSharedPreferences("vipx_model_setup", Context.MODE_PRIVATE)
                .getBoolean("first_prompt_shown", false)) {
            showModelPrompt = true
        }
    }
    val visible=remember(apps,query){apps.filter{it.name.contains(query,true)||it.packageName.contains(query,true)}}
    Surface(Modifier.fillMaxSize(),color=Navy){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("VIP X ULTIMATE",color=Mint,fontSize=13.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.8.sp)
                    Text("AI VIP Builder",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold)
                    Text("فحص APK الحقيقي • تحليل المساعدين • حماية التطبيق الأصلي",color=Soft,fontSize=12.sp)
                }
                Icon(Icons.Default.Android,null,tint=Mint,modifier=Modifier.size(34.dp))
            }
            SheerGarmentPicker(onConfirm = { garmentName, colorName, colorHex, style ->
                selectedGarmentColorHex = colorHex
                selectedGarmentStyle = style
                status = "تم اعتماد $garmentName باللون $colorName كاختيار للمعاينة"
            })
            VideoPoseAnalysisPanel(selectedGarmentColorHex, selectedGarmentStyle)
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("1 • اختر التطبيق المثبّت",color=Color.White,fontWeight=FontWeight.Bold)
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("ابحث في تطبيقاتك")},leadingIcon={Icon(Icons.Default.Search,null)})
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        Text("المحدد: ${selected?.name?:"لم تختر تطبيقاً بعد"}",color=Mint,fontSize=12.sp)
                        TextButton(onClick={
                            scope.launch{
                                status="جارٍ تحديث قائمة التطبيقات..."
                                apps=withContext(Dispatchers.IO){loadApps()}
                                status="تم العثور على ${apps.size} تطبيقاً"
                            }
                        }){Text("تحديث القائمة")}
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text("الذكاء الاصطناعي المحلي",color=Mint,fontWeight=FontWeight.Bold)
                    Text(
                        when {
                            modelReady -> "جاهز • يعمل على الهاتف دون إنترنت"
                            modelDownloading -> "تنزيل وتجهيز النموذج: $modelProgress%"
                            LocalLlmRuntime.isModelDownloaded(context) -> "النموذج محفوظ • جارٍ تهيئته عند فتح التطبيق"
                            else -> "نموذج Qwen3 مجاني بحجم يقارب 614 ميغابايت. يُنزّل مرة واحدة بعد موافقتك."
                        },
                        color=Soft,fontSize=11.sp
                    )
                    if(modelDownloading) LinearProgressIndicator(progress=modelProgress/100f,modifier=Modifier.fillMaxWidth())
                    if(!modelReady && !modelDownloading) {
                        Button(onClick={startLocalModelSetup()},modifier=Modifier.fillMaxWidth()) {
                            Text(if(LocalLlmRuntime.isModelDownloaded(context)) "تشغيل الذكاء المحلي" else "تنزيل وتشغيل الذكاء المحلي")
                        }
                    }
                    if(modelStatus.isNotBlank()) Text(modelStatus,color=if(modelReady) Mint else Soft,fontSize=10.sp)
                }
            }
            if(showModelPrompt) {
                AlertDialog(
                    onDismissRequest={
                        context.getSharedPreferences("vipx_model_setup", Context.MODE_PRIVATE)
                            .edit().putBoolean("first_prompt_shown", true).apply()
                        showModelPrompt=false
                    },
                    title={Text("جهّز ذكاء VIP X المحلي")},
                    text={Text("يمكن تنزيل نموذج Qwen3 المجاني مرة واحدة (حوالي 614 ميغابايت). بعد التحقق وتشغيله سيعمل داخل هاتفك دون إنترنت أو رمز API. ابدأ الآن إذا كنت تستخدم Wi-Fi وتتوفر مساحة فارغة كافية، أو اختر لاحقاً.")},
                    confirmButton={TextButton(onClick={::startLocalModelSetup}){Text("تنزيل الآن")}},
                    dismissButton={TextButton(onClick={
                        context.getSharedPreferences("vipx_model_setup", Context.MODE_PRIVATE)
                            .edit().putBoolean("first_prompt_shown", true).apply()
                        showModelPrompt=false
                    }){Text("لاحقاً")}}
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(min=90.dp,max=220.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                if(visible.isEmpty()){
                    item{
                        Text(
                            if(query.isBlank()) "لم تظهر تطبيقات في القائمة. اضغط «تحديث القائمة» أو تحقق من صلاحية عرض التطبيقات."
                            else "لا توجد تطبيقات تطابق بحثك.",
                            color=Soft,fontSize=13.sp,modifier=Modifier.fillMaxWidth().padding(18.dp)
                        )
                    }
                }
                items(visible,key={it.packageName}){app->
                    Card(Modifier.fillMaxWidth().clickable{selected=app;answer="";status="تم اختيار ${app.name}"},colors=CardDefaults.cardColors(containerColor=if(selected?.packageName==app.packageName) Color(0xFF17463F) else Panel),shape=RoundedCornerShape(12.dp)){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Default.Android,null,tint=Mint)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){
                                Text(app.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                                Text(app.packageName,color=Soft,fontSize=10.sp)
                            }
                            if(selected?.packageName==app.packageName) Text("محدد",color=Mint,fontSize=11.sp)
                        }
                    }
                }
            }
            LocalAnalysisCard(
                appName = selected?.name,
                packageName = selected?.packageName,
                onResult = { answer = it },
                onStatus = { status = it }
            )
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF173A3D)),shape=RoundedCornerShape(18.dp)){
                Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("جلسة VIP X داخل التطبيقات",color=Mint,fontWeight=FontWeight.Bold)
                    Text("فعّل صلاحية الظهور فوق التطبيقات وخدمة المساعدة من إعدادات Android. الخدمة تقرأ النص الظاهر فقط، وتتجاهل حقول كلمات المرور.",color=Soft,fontSize=11.sp)
                    OutlinedButton(onClick={
                        if(!Settings.canDrawOverlays(context)){
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+context.packageName)))
                        } else {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },modifier=Modifier.fillMaxWidth()){
                        Text("إعداد صلاحيات جلسة VIP X")
                    }
                    Button(onClick={
                        val app=selected
                        if(app==null){status="اختر تطبيقاً أولاً"} else {
                            val launch=context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if(launch==null) status="هذا التطبيق لا يوفّر واجهة تشغيل مباشرة"
                            else runCatching{context.startActivity(launch)}.onFailure{status="تعذر فتح التطبيق: ${it.message}"}
                        }
                    },enabled=selected!=null,modifier=Modifier.fillMaxWidth()){
                        Text("افتح التطبيق وابدأ جلسة المساعدة")
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("3 • خدمات الخادم الاختيارية",color=Color.White,fontWeight=FontWeight.Bold)
                    OutlinedTextField(goal,{goal=it},Modifier.fillMaxWidth(),label={Text("ما الذي تريد تحسينه؟")},minLines=2,maxLines=3)
                    OutlinedTextField(endpoint,{endpoint=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("رابط خادم اختياري (اتركه فارغاً للمختبر المحلي)")},placeholder={Text("https://your-server.example")})
                    OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("رمز ربط خادم VIP X")},visualTransformation=PasswordVisualTransformation())
                    OutlinedButton(
                        onClick = {
                            if (endpoint.isBlank()) { status = "أدخل رابط الخادم فقط إذا أردت استعمال الخدمة السحابية؛ التحليل المحلي لا يحتاجه"; return@OutlinedButton }
                            if (token.isBlank()) { status = "أدخل رمز الربط أولاً"; return@OutlinedButton }
                            busy = true
                            status = "جارٍ التحقق من رمز الربط والخادم..."
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val healthConn = (URL(endpoint.trimEnd('/') + "/health").openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 15000
                                            readTimeout = 20000
                                        }
                                        val health = try {
                                            val body = healthConn.inputStream.bufferedReader().use { it.readText() }
                                            JSONObject(body)
                                        } finally { healthConn.disconnect() }
                                        val authConn = (URL(endpoint.trimEnd('/') + "/api/session").openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 15000
                                            readTimeout = 20000
                                            setRequestProperty("Authorization", "Bearer " + token.trim())
                                        }
                                        try {
                                            val stream = if (authConn.responseCode in 200..299) authConn.inputStream else authConn.errorStream
                                            val body = stream.bufferedReader().use { it.readText() }
                                            if (authConn.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("error", "HTTP ${authConn.responseCode}"))
                                            JSONObject(body).put("aiConfigured", health.optBoolean("aiConfigured"))
                                                .put("memoryConfigured", health.optBoolean("memoryConfigured"))
                                        } finally { authConn.disconnect() }
                                    }
                                    if (!result.optBoolean("authenticated", false)) throw IllegalStateException("الخادم لم يؤكد المصادقة")
                                    SecureTokenStore.save(context, token.trim())
                                    val ai = if (result.optBoolean("aiConfigured")) "الذكاء الاصطناعي مهيأ" else "مفتاح الذكاء الاصطناعي غير مهيأ"
                                    val db = if (result.optBoolean("memoryConfigured")) "الذاكرة الدائمة مهيأة" else "قاعدة الذاكرة غير مهيأة"
                                    status = "اتصال موثّق ناجح • $ai • $db"
                                } catch (e: Exception) {
                                    status = "فشل التحقق: ${e.message ?: "رمز غير صحيح أو الخادم غير متاح"}"
                                } finally { busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("اختبر الاتصال ورمز الربط") }
                    Text(status,color=Mint,fontSize=10.sp)
                }
            }
            Button(
                onClick = {
                    val app = selected
                    if (app == null) { status = "اختر تطبيقاً أولاً"; return@Button }
                    if (endpoint.isBlank()) { status = "التحليل المحلي لا يحتاج خادماً؛ أدخل رابطه فقط للفحص السحابي"; return@Button }
                    if (token.isBlank()) { status = "أدخل رمز ربط الخادم أولاً"; return@Button }
                    busy = true
                    answer = ""
                    status = "جارٍ استخراج APK المثبّت وفحصه..."
                    scope.launch {
                        try {
                            val report = withContext(Dispatchers.IO) {
                                val info = context.packageManager.getApplicationInfo(app.packageName, 0)
                                val apk = File(info.sourceDir)
                                if (!apk.isFile || !apk.canRead()) throw IllegalStateException("تعذر قراءة ملف APK لهذا التطبيق")
                                val conn = (URL(endpoint.trimEnd('/') + "/api/apk/analyze").openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    connectTimeout = 20000
                                    readTimeout = 180000
                                    doOutput = true
                                    setFixedLengthStreamingMode(apk.length())
                                    setRequestProperty("Content-Type", "application/vnd.android.package-archive")
                                    setRequestProperty("Authorization", "Bearer " + token.trim())
                                }
                                val response = try {
                                    conn.outputStream.use { out -> apk.inputStream().use { input -> input.copyTo(out, 64 * 1024) } }
                                    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                                    val body = stream.bufferedReader().use { it.readText() }
                                    if (conn.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("error", "HTTP ${conn.responseCode}"))
                                    JSONObject(body).getJSONObject("analysis")
                                } finally { conn.disconnect() }
                                val evidence = response.toString()
                                val team = (URL(endpoint.trimEnd('/') + "/api/vip-team").openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    connectTimeout = 20000
                                    readTimeout = 180000
                                    doOutput = true
                                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                    setRequestProperty("Authorization", "Bearer " + token.trim())
                                }
                                try {
                                    val payload = JSONObject().put("appName", app.name).put("packageName", app.packageName)
                                        .put("goal", goal).put("evidence", evidence)
                                    team.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                    val stream = if (team.responseCode in 200..299) team.inputStream else team.errorStream
                                    val body = stream.bufferedReader().use { it.readText() }
                                    if (team.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("error", "HTTP ${team.responseCode}"))
                                    val result = JSONObject(body)
                                    buildString {
                                        append("نتيجة فحص APK الفعلي\n")
                                        append("الحزمة: ").append(response.optString("packageName", app.packageName)).append("\n")
                                        append("SHA-256: ").append(response.getJSONObject("file").optString("sha256")).append("\n")
                                        append("التوقيع: ").append(response.optString("signature")).append("\n")
                                        append("إصدار Android الأدنى: ").append(response.optString("minSdk", "غير معروف")).append("\n")
                                        append("Android المستهدف: ").append(response.optString("targetSdk", "غير معروف")).append("\n")
                                        val structure = response.optJSONObject("structure") ?: JSONObject()
                                        append("ملفات DEX: ").append(structure.optJSONArray("dexFiles")?.length() ?: 0).append("\n")
                                        append("المكتبات الأصلية: ").append(structure.optJSONArray("nativeLibraries")?.length() ?: 0).append("\n")
                                        append("الصلاحيات المكتشفة: ").append(response.optJSONArray("permissions")?.length() ?: 0).append("\n\n")
                                        val agents = result.optJSONArray("team") ?: JSONArray()
                                        for (i in 0 until agents.length()) {
                                            val a = agents.getJSONObject(i)
                                            append("◆ ").append(a.optString("name")).append("\n")
                                            append(a.optString("result")).append("\n\n")
                                        }
                                        append("قرار الفريق\n").append(result.optString("decision"))
                                    }
                                } finally { team.disconnect() }
                            }
                            answer = report
                            status = "اكتمل فحص APK وتشغيل المساعدين على أدلة فعلية"
                        } catch (e: Exception) {
                            status = "تعذر الفحص: ${e.message ?: "تحقق من الخادم والتطبيق"}"
                        } finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF146B62))
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("افحص عبر الخادم الاختياري وشغّل فريق AI")
            }
            OutlinedButton(
                onClick = {
                    if (token.isBlank()) { status = "أدخل رمز ربط الخادم أولاً"; return@OutlinedButton }
                    busy = true
                    scope.launch {
                        try {
                            val text = withContext(Dispatchers.IO) {
                                val ids = listOf("structure", "security", "design", "engineering")
                                buildString {
                                    append("ذاكرة المساعدين والإصدارات\n\n")
                                    for (id in ids) {
                                        val conn = (URL(endpoint.trimEnd('/') + "/api/agent-memory/" + id).openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 15000
                                            readTimeout = 30000
                                            setRequestProperty("Authorization", "Bearer " + token.trim())
                                        }
                                        try {
                                            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                                            val body = stream.bufferedReader().use { it.readText() }
                                            if (conn.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("error", "HTTP ${conn.responseCode}"))
                                            val mem = JSONObject(body)
                                            append("• ").append(id).append("\n")
                                            val verified = mem.optJSONObject("lastVerified")
                                            val candidate = mem.optJSONObject("latestCandidate")
                                            append("آخر نتيجة مثبتة: ").append(verified?.optLong("versionId")?.toString() ?: "لا توجد").append("\n")
                                            append("آخر مرشح: ").append(candidate?.optLong("versionId")?.toString() ?: "لا يوجد").append("\n\n")
                                        } finally { conn.disconnect() }
                                    }
                                }
                            }
                            memoryInfo = text
                            answer = text
                            status = "تم استرجاع ذاكرة المساعدين من قاعدة البيانات"
                        } catch (e: Exception) {
                            status = "تعذر قراءة الذاكرة: ${e.message ?: "تحقق من الخادم"}"
                        } finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("اعرض ذاكرة الفريق السحابية (اختياري)") }
            if(answer.isNotBlank()){
                Card(Modifier.fillMaxWidth().heightIn(max=190.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102F32))){
                    Column(Modifier.padding(12.dp)){
                        Text("نتيجة تحليل VIP",color=Mint,fontWeight=FontWeight.Bold)
                        LazyColumn{item{Text(answer,color=Color.White,fontSize=12.sp)}}
                    }
                }
            }
            Text("يعمل VIP X كمساعد داخل التطبيق المحدد: قراءة سياق الشاشة، النقر على عنصر ظاهر، التمرير والرجوع. لا يحذف التطبيق الأصلي ولا يتجاوز كلمات المرور أو الدفع أو الحماية.",color=Soft,fontSize=9.sp)
        }
    }
}


@Composable
private fun SheerGarmentPicker(onConfirm: (String, String, String, SheerGarmentStyle) -> Unit) {
    val presets = SheerGarmentCatalog.presets
    var selectedId by remember { mutableStateOf(presets.first().id) }
    var selectedColor by remember { mutableStateOf(ModernGarmentColor.WHITE) }
    val selected = presets.firstOrNull { it.id == selectedId } ?: presets.first()
    val colors = ModernGarmentColor.values().toList()
    fun colorOf(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("معرض الألبسة الشفافة المظهر", color = Mint, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("اختر التصميم أولاً، ثم اللون، وشاهد المعاينة قبل اعتماد الاختيار.", color = Soft, fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(presets, key = { it.id }) { garment ->
                    val active = garment.id == selectedId
                    Card(Modifier.width(142.dp).clickable { selectedId = garment.id },
                        colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFF17463F) else Color(0xFF0B1B2A)),
                        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Mint) else null,
                        shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GarmentShapePreview(garment.style, colorOf(selectedColor.hex))
                            Text(garment.displayNameArabic, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (active) "محدد للمعاينة" else "اضغط للاختيار", color = if (active) Mint else Soft, fontSize = 9.sp)
                        }
                    }
                }
            }
            Divider(color = Color(0xFF294158))
            Text("التصميم المختار: ${selected.displayNameArabic}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(selected.modernCut, color = Soft, fontSize = 11.sp)
            Text("اختر اللون", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(colors, key = { it.name }) { option ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { selectedColor = option }.padding(2.dp)) {
                        Surface(Modifier.size(29.dp), shape = RoundedCornerShape(50), color = colorOf(option.hex),
                            border = androidx.compose.foundation.BorderStroke(if (selectedColor == option) 3.dp else 1.dp,
                                if (selectedColor == option) Mint else Color(0xFF60758A))) {}
                        Text(when(option) {
                            ModernGarmentColor.WHITE -> "أبيض"
                            ModernGarmentColor.IVORY -> "عاجي"
                            ModernGarmentColor.BLACK -> "أسود"
                            ModernGarmentColor.CHAMPAGNE -> "شامبانيا"
                            ModernGarmentColor.DUSTY_ROSE -> "وردي"
                            ModernGarmentColor.BURGUNDY -> "عنابي"
                            ModernGarmentColor.EMERALD -> "زمردي"
                            ModernGarmentColor.SAPPHIRE -> "أزرق"
                            ModernGarmentColor.LAVENDER -> "لافندر"
                            ModernGarmentColor.POWDER_BLUE -> "سماوي"
                            ModernGarmentColor.SILVER_GREY -> "فضي"
                            ModernGarmentColor.MOCHA -> "موكا"
                            ModernGarmentColor.TERRACOTTA -> "طوبي"
                        }, color = Soft, fontSize = 8.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بطانة ساترة • لا تغيير لشكل الجسم", color = Mint, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Button(onClick = { onConfirm(selected.displayNameArabic, selectedColor.name, selectedColor.hex, selected.style) }) { Text("اعتماد الاختيار") }
            }
        }
    }
}

@Composable
private fun GarmentShapePreview(style: SheerGarmentStyle, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(104.dp)) {
        val cx = size.width / 2f
        val top = size.height * 0.08f
        val bottom = size.height * 0.94f
        val shoulder = size.width * 0.19f
        val waist = size.width * 0.14f
        val hem = when(style) {
            SheerGarmentStyle.CHIFFON_OVERLAY -> size.width * 0.39f
            SheerGarmentStyle.TULLE_LAYERED -> size.width * 0.43f
            SheerGarmentStyle.LACE_OVERLAY -> size.width * 0.31f
            SheerGarmentStyle.ORGANZA_LAYER -> size.width * 0.34f
            SheerGarmentStyle.MESH_PANEL -> size.width * 0.32f
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - shoulder, top + 8f)
            lineTo(cx - shoulder * 1.55f, top + 24f)
            lineTo(cx - waist, top + 48f)
            lineTo(cx - hem, bottom)
            lineTo(cx + hem, bottom)
            lineTo(cx + waist, top + 48f)
            lineTo(cx + shoulder * 1.55f, top + 24f)
            lineTo(cx + shoulder, top + 8f)
            close()
        }
        drawPath(path, color.copy(alpha = 0.78f))
        drawPath(path, Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        if (style == SheerGarmentStyle.LACE_OVERLAY || style == SheerGarmentStyle.MESH_PANEL || style == SheerGarmentStyle.TULLE_LAYERED) {
            for (i in 0..4) {
                val y = top + 34f + i * 11f
                drawLine(Color.White.copy(alpha = 0.28f), androidx.compose.ui.geometry.Offset(cx - waist, y), androidx.compose.ui.geometry.Offset(cx + waist, y), strokeWidth = 1f)
            }
        }
        if (style == SheerGarmentStyle.CHIFFON_OVERLAY || style == SheerGarmentStyle.ORGANZA_LAYER) {
            drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(cx, top + 12f), androidx.compose.ui.geometry.Offset(cx, bottom - 4f), strokeWidth = 2f)
        }
    }
}
