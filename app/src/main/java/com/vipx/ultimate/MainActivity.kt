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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

private val Bg = Color(0xFF08111F)
private val Panel = Color(0xFF14243A)
private val Tile = Color(0xFF1B304A)
private val Accent = Color(0xFF43DEC9)
private val Muted = Color(0xFFA7B8CC)
private val Warn = Color(0xFFFFC66D)

private data class AppInfo(val name:String,val pkg:String,val version:String,val apk:String,val bytes:Long,val permissions:List<String>,val installed:Long,val updated:Long,val splits:Int)
private data class PermissionNote(val name:String,val level:String,val reason:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(8,17,31)
        window.navigationBarColor=android.graphics.Color.rgb(8,17,31)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel,onSurface=Color.White)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VipXScreen(
                        scan={ scanApps() },
                        open={ a -> packageManager.getLaunchIntentForPackage(a.pkg)?.let(::startActivity) },
                        settings={ a -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${a.pkg}"))) },
                        cloud={ startActivity(Intent(this,CloudAssistantActivity::class.java)) }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun scanApps():List<AppInfo> {
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches=if(Build.VERSION.SDK_INT>=33) packageManager.queryIntentActivities(intent,PackageManager.ResolveInfoFlags.of(0)) else packageManager.queryIntentActivities(intent,0)
        return matches.mapNotNull { r ->
            val ai=r.activityInfo?.applicationInfo ?: return@mapNotNull null
            if(ai.packageName==packageName) return@mapNotNull null
            try {
                val flags=PackageManager.GET_PERMISSIONS
                val pi=if(Build.VERSION.SDK_INT>=33) packageManager.getPackageInfo(ai.packageName,PackageManager.PackageInfoFlags.of(flags.toLong())) else packageManager.getPackageInfo(ai.packageName,flags)
                AppInfo(packageManager.getApplicationLabel(ai).toString(),ai.packageName,pi.versionName ?: "غير معروف",ai.sourceDir,File(ai.sourceDir).length(),pi.requestedPermissions?.toList() ?: emptyList(),pi.firstInstallTime,pi.lastUpdateTime,ai.splitSourceDirs?.size ?: 0)
            } catch(_:Exception){null}
        }.distinctBy{it.pkg}.sortedBy{it.name.lowercase()}
    }
}

@Composable
private fun VipXScreen(scan:suspend()->List<AppInfo>,open:(AppInfo)->Unit,settings:(AppInfo)->Unit,cloud:()->Unit) {
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    var apps by remember{mutableStateOf<List<AppInfo>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var tab by remember{mutableIntStateOf(0)}
    var query by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<AppInfo?>(null)}
    var status by remember{mutableStateOf("يتم فحص التطبيقات")}
    var sessionSeconds by remember{mutableLongStateOf(0L)}
    var sessionRunning by remember{mutableStateOf(false)}
    var sessionStart by remember{mutableLongStateOf(0L)}
    val export= rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")){uri->
        val app=selected
        if(uri!=null && app!=null) try{
            context.contentResolver.openOutputStream(uri)?.use{out->FileInputStream(File(app.apk)).use{it.copyTo(out)}}
            status="تم حفظ ملف APK الأساسي"
        }catch(e:Exception){status="تعذر التصدير: ${e.localizedMessage ?: "خطأ"}"}
    }
    suspend fun refresh(){
        loading=true
        try{apps=withContext(Dispatchers.IO){scan()};status="اكتمل الفحص المحلي"}
        catch(e:Exception){status="فشل الفحص: ${e.localizedMessage ?: "خطأ"}"}
        finally{loading=false}
    }
    LaunchedEffect(Unit){refresh()}
    LaunchedEffect(sessionRunning){
        while(sessionRunning){ kotlinx.coroutines.delay(1000);sessionSeconds=(System.currentTimeMillis()-sessionStart)/1000 }
    }
    val filtered=remember(apps,query){apps.filter{it.name.contains(query,true)||it.pkg.contains(query,true)}}
    val flagged=remember(apps){apps.count{permissionNotes(it.permissions).any{n->n.level=="حساسة"}}}
    Surface(Modifier.fillMaxSize(),color=Bg){
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("VIP X  /  ULTIMATE",color=Accent,fontSize=13.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.5.sp)
                    Text("مركز التشغيل الذكي",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Text("إدارة • تحليل • أدوات الهاتف",color=Muted,fontSize=11.sp)
                }
                Icon(Icons.Default.Shield,null,tint=Accent,modifier=Modifier.size(38.dp))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StatTile("التطبيقات",apps.size.toString(),Modifier.weight(1f))
                StatTile("صلاحيات للمراجعة",flagged.toString(),Modifier.weight(1f))
                StatTile("حالة المختبر",if(loading)"يفحص" else "جاهز",Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                listOf("الرئيسية","المختبر","الألعاب","المساعد").forEachIndexed{index,label->
                    FilterChip(selected=tab==index,onClick={tab=index},label={Text(label,fontSize=11.sp)})
                }
            }
            when(tab){
                0->{
                    Text("مساحة التحكم",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    ActionCard("فحص الهاتف","تحليل التطبيقات المثبتة ومعلوماتها",Icons.Default.Security){tab=1}
                    ActionCard("وضع الألعاب","مؤقت جلسة اللعب وأدوات سريعة",Icons.Default.SportsEsports){tab=2}
                    ActionCard("المهندس الذكي","مساعد سحابي للمشاريع عند إعداد الخادم",Icons.Default.Cloud){cloud()}
                    ActionCard("إعدادات النظام","فتح إعدادات Android",Icons.Default.Settings){context.startActivity(Intent(Settings.ACTION_SETTINGS))}
                    Text(status,color=Muted,fontSize=10.sp)
                    OutlinedButton(onClick={scope.launch{refresh()}},enabled=!loading,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("تحديث بيانات المختبر")}
                    Text("خصوصيتك أولاً: الفحص محلي ولا تُرسل بيانات التطبيقات تلقائياً.",color=Muted,fontSize=10.sp)
                }
                1->{
                    Text("مختبر التطبيقات",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Text("معلومات الحزم والصلاحيات المعلنة وبصمة APK",color=Muted,fontSize=10.sp)
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("بحث بالاسم أو الحزمة")},leadingIcon={Icon(Icons.Default.Search,null)})
                    Text(status,color=Accent,fontSize=10.sp)
                    if(loading && apps.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Accent)}
                    else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        items(filtered,key={it.pkg}){app->
                            Card(Modifier.fillMaxWidth().clickable{selected=app},colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(15.dp)){
                                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                                    Icon(Icons.Default.Android,null,tint=Accent,modifier=Modifier.size(27.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)){
                                        Text(app.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                                        Text(app.pkg,color=Muted,fontSize=9.sp)
                                        Text("${formatBytes(app.bytes)} • ${app.permissions.size} صلاحية • ${app.splits} ملفات split",color=Muted,fontSize=10.sp)
                                    }
                                    Text("فحص",color=Accent,fontSize=11.sp)
                                }
                            }
                        }
                    }
                }
                2->{
                    Text("وضع الألعاب",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Text("مؤقت جلسة اللعب",color=Muted,fontSize=12.sp)
                            Text(String.format("%02d:%02d:%02d",sessionSeconds/3600,(sessionSeconds/60)%60,sessionSeconds%60),color=Accent,fontSize=34.sp,fontWeight=FontWeight.Bold)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                Button(onClick={if(!sessionRunning){sessionStart=System.currentTimeMillis()-sessionSeconds*1000;sessionRunning=true}},modifier=Modifier.weight(1f),enabled=!sessionRunning){Text("بدء")}
                                OutlinedButton(onClick={sessionRunning=false},modifier=Modifier.weight(1f),enabled=sessionRunning){Text("إيقاف")}
                                OutlinedButton(onClick={sessionRunning=false;sessionSeconds=0},modifier=Modifier.weight(1f)){Text("تصفير")}
                            }
                        }
                    }
                    ActionCard("اختصارات النظام","فتح إعدادات الهاتف",Icons.Default.Settings){context.startActivity(Intent(Settings.ACTION_SETTINGS))}
                    ActionCard("إدارة التخزين","فتح صفحة التخزين",Icons.Default.Storage){context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))}
                    Text("لا يتدخل التطبيق في ذاكرة الألعاب أو يتجاوز حمايتها.",color=Muted,fontSize=10.sp)
                }
                else->{
                    Text("المهندس الذكي",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    ActionCard("فتح المساعد السحابي","يتطلب إعداد الخادم ومفتاح API خارج التطبيق",Icons.Default.Cloud){cloud()}
                    Text("لن نعرض اتصالاً ناجحاً قبل التحقق من الخادم. مفاتيح API لا تُضمّن داخل APK.",color=Muted,fontSize=11.sp)
                }
            }
        }
    }
    selected?.let{app->
        val notes=remember(app){permissionNotes(app.permissions)}
        var hash by remember(app.pkg){mutableStateOf<String?>(null)}
        AlertDialog(onDismissRequest={selected=null},title={Text(app.name)},text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                DetailLine("الحزمة",app.pkg);DetailLine("الإصدار",app.version);DetailLine("حجم APK",formatBytes(app.bytes))
                DetailLine("الصلاحيات",app.permissions.size.toString());DetailLine("ملفات split",app.splits.toString())
                DetailLine("تاريخ التثبيت",formatDate(app.installed));DetailLine("آخر تحديث",formatDate(app.updated))
                Text("مراجعة الصلاحيات",color=Accent,fontWeight=FontWeight.Bold)
                Text("التصنيف إرشادي ولا يثبت أن التطبيق ضار.",color=Warn,fontSize=10.sp)
                if(notes.isEmpty())Text("لا توجد صلاحيات ضمن قائمة المراجعة.",color=Muted,fontSize=10.sp)
                notes.forEach{Text("• ${it.level}: ${it.name.substringAfterLast('.')} — ${it.explanation}",color=Muted,fontSize=10.sp)}
                TextButton(onClick={scope.launch{hash=withContext(Dispatchers.IO){sha256(File(app.apk))}}}){Text(if(hash==null)"حساب SHA-256" else "SHA-256: $hash",fontSize=10.sp)}
            }
        },confirmButton={
            Column(horizontalAlignment=Alignment.End){
                TextButton(onClick={open(app)}){Text("فتح التطبيق")}
                TextButton(onClick={settings(app)}){Text("إعدادات التطبيق")}
                TextButton(onClick={export.launch("${app.pkg}.apk")}){Text("تصدير APK الأساسي")}
                TextButton(onClick={selected=null}){Text("إغلاق")}
            }
        })
    }
}

@Composable private fun StatTile(label:String,value:String,modifier:Modifier){Surface(modifier,color=Tile,shape=RoundedCornerShape(13.dp)){Column(Modifier.padding(10.dp)){Text(value,color=Accent,fontSize=16.sp,fontWeight=FontWeight.Bold);Text(label,color=Muted,fontSize=9.sp)}}}
@Composable private fun ActionCard(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(15.dp)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Accent,modifier=Modifier.size(26.dp));Spacer(Modifier.width(12.dp));Column{Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,fontSize=10.sp)}}}}
@Composable private fun DetailLine(label:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted,fontSize=10.sp);Text(value,color=Color.White,fontSize=10.sp)}}

private fun permissionNotes(list:List<String>):List<PermissionNote>{
    val rules=listOf(
        "android.permission.CAMERA" to ("حساسة" to "تتيح استخدام الكاميرا"),
        "android.permission.RECORD_AUDIO" to ("حساسة" to "تتيح تسجيل الصوت"),
        "android.permission.ACCESS_FINE_LOCATION" to ("حساسة" to "تتيح تحديد الموقع بدقة"),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to ("حساسة" to "تتيح الوصول للموقع في الخلفية"),
        "android.permission.READ_CONTACTS" to ("حساسة" to "تتيح قراءة جهات الاتصال"),
        "android.permission.READ_CALL_LOG" to ("حساسة" to "تتيح قراءة سجل المكالمات"),
        "android.permission.READ_SMS" to ("حساسة" to "تتيح قراءة الرسائل النصية"),
        "android.permission.READ_EXTERNAL_STORAGE" to ("مراجعة" to "تتيح قراءة ملفات مشتركة وفق إصدار Android"),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to ("حساسة" to "تتيح إدارة واسعة للملفات"),
        "android.permission.SYSTEM_ALERT_WINDOW" to ("مراجعة" to "قد تسمح بعرض نوافذ فوق التطبيقات")
    )
    return list.mapNotNull{p->rules.firstOrNull{it.first==p}?.let{PermissionNote(p,it.second.first,it.second.second)}}
}
private fun sha256(file:File):String=try{val d=MessageDigest.getInstance("SHA-256");FileInputStream(file).use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<=0)break;d.update(b,0,n)}};d.digest().joinToString(""){"%02x".format(it)}}catch(_:Exception){"تعذر الحساب"}
private fun formatBytes(n:Long):String=when{n>=1048576->String.format("%.2f MB",n/1048576.0);n>=1024->String.format("%.1f KB",n/1024.0);else->"$n B"}
private fun formatDate(t:Long):String=if(t<=0)"غير متاح" else DateFormat.getDateTimeInstance().format(Date(t))
