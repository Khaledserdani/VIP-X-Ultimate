package com.vipx.ultimate

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

private val Bg = Color(0xFF08111F)
private val Panel = Color(0xFF14243A)
private val Tile = Color(0xFF1B304A)
private val Accent = Color(0xFF43DEC9)
private val Muted = Color(0xFFA7B8CC)

private data class AppEntry(val name:String,val pkg:String,val apk:String,val bytes:Long,val isGame:Boolean)

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
                        launchApp={ app -> packageManager.getLaunchIntentForPackage(app.pkg)?.let(::startActivity) },
                        cloud={ startActivity(Intent(this,CloudAssistantActivity::class.java)) }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun scanApps():List<AppEntry> {
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches=if(Build.VERSION.SDK_INT>=33) packageManager.queryIntentActivities(intent,PackageManager.ResolveInfoFlags.of(0)) else packageManager.queryIntentActivities(intent,0)
        return matches.mapNotNull { result ->
            val info=result.activityInfo?.applicationInfo ?: return@mapNotNull null
            if(info.packageName==packageName) return@mapNotNull null
            AppEntry(
                packageManager.getApplicationLabel(info).toString(),
                info.packageName,
                info.sourceDir,
                File(info.sourceDir).length(),
                info.category==ApplicationInfo.CATEGORY_GAME
            )
        }.distinctBy{it.pkg}.sortedBy{it.name.lowercase()}
    }
}

@Composable
private fun VipXScreen(scan:suspend()->List<AppEntry>,launchApp:(AppEntry)->Unit,cloud:()->Unit) {
    val context=LocalContext.current
    val scope= rememberCoroutineScope()
    var apps by remember{mutableStateOf<List<AppEntry>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var tab by remember{mutableIntStateOf(0)}
    var search by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<AppEntry?>(null)}
    var status by remember{mutableStateOf("جارٍ تجهيز قائمة التصدير والألعاب")}
    var sessionSeconds by remember{mutableLongStateOf(0L)}
    var sessionRunning by remember{mutableStateOf(false)}
    var sessionStart by remember{mutableLongStateOf(0L)}
    var pendingExport by remember{mutableStateOf<AppEntry?>(null)}

    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")){uri->
        val app=pendingExport
        if(uri!=null && app!=null){
            scope.launch {
                status=try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            FileInputStream(File(app.apk)).use { input -> input.copyTo(output) }
                        } ?: error("تعذر فتح ملف الحفظ")
                    }
                    "تم تصدير ملف APK الأساسي: ${app.name}"
                } catch(e:Exception) {
                    "فشل التصدير: ${e.localizedMessage ?: "خطأ غير معروف"}"
                }
                pendingExport=null
            }
        } else pendingExport=null
    }

    suspend fun refresh(){
        loading=true
        status="جارٍ تحميل التطبيقات القابلة للتشغيل"
        try{
            apps=withContext(Dispatchers.IO){scan()}
            status="تم تجهيز ${apps.size} تطبيقاً"
        }catch(e:Exception){
            status="تعذر تحميل التطبيقات: ${e.localizedMessage ?: "خطأ"}"
        }finally{loading=false}
    }

    LaunchedEffect(Unit){refresh()}
    LaunchedEffect(sessionRunning){
        while(sessionRunning){
            kotlinx.coroutines.delay(1000)
            sessionSeconds=(System.currentTimeMillis()-sessionStart)/1000
        }
    }

    val games=remember(apps){apps.filter{it.isGame}}
    val exportList=remember(apps,search){apps.filter{it.name.contains(search,true)||it.pkg.contains(search,true)}}

    Surface(Modifier.fillMaxSize(),color=Bg){
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("VIP X  /  ULTIMATE",color=Accent,fontSize=13.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.5.sp)
                    Text("مساحة الأدوات الذكية",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Text("ألعابك • تصدير تطبيقاتك • مساعدك الذكي",color=Muted,fontSize=11.sp)
                }
                Icon(Icons.Default.Shield,null,tint=Accent,modifier=Modifier.size(38.dp))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("الرئيسية","الألعاب","تصدير التطبيقات","المساعد").forEachIndexed{index,label->
                    FilterChip(selected=tab==index,onClick={tab=index},label={Text(label,fontSize=10.sp)})
                }
            }
            when(tab){
                0->{
                    Text("أهلاً بك في VIP X",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text("اختر ما تريد إنجازه دون تكرار أدوات إعدادات الهاتف.",color=Muted,fontSize=12.sp)
                    ActionCard("مركز الألعاب","اعرض ألعابك وشغّلها واحتسب مدة الجلسة",Icons.Default.SportsEsports){tab=1}
                    ActionCard("تصدير التطبيقات","احفظ نسخة APK أساسية من تطبيق تختاره",Icons.Default.Android){tab=2}
                    ActionCard("المساعد الذكي","افتح المساعد السحابي",Icons.Default.Shield){cloud()}
                    Text(status,color=Muted,fontSize=11.sp)
                    OutlinedButton(onClick={scope.launch{refresh()}},enabled=!loading,modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(7.dp));Text("تحديث قائمة الألعاب والتطبيقات")
                    }
                    Text("التصدير يحفظ ملف APK الأساسي فقط؛ بعض التطبيقات تتكون من ملفات إضافية وقد لا تعمل النسخة وحدها عند إعادة تثبيتها.",color=Muted,fontSize=10.sp)
                }
                1->{
                    Text("مركز الألعاب",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Text("مدة جلسة اللعب",color=Muted,fontSize=12.sp)
                            Text(String.format("%02d:%02d:%02d",sessionSeconds/3600,(sessionSeconds/60)%60,sessionSeconds%60),color=Accent,fontSize=34.sp,fontWeight=FontWeight.Bold)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                Button(onClick={
                                    if(!sessionRunning){
                                        sessionStart=System.currentTimeMillis()-sessionSeconds*1000
                                        sessionRunning=true
                                    }
                                },modifier=Modifier.weight(1f),enabled=!sessionRunning){Text("بدء")}
                                OutlinedButton(onClick={sessionRunning=false},modifier=Modifier.weight(1f),enabled=sessionRunning){Text("إيقاف")}
                                OutlinedButton(onClick={sessionRunning=false;sessionSeconds=0},modifier=Modifier.weight(1f)){Text("تصفير")}
                            }
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text("الألعاب المكتشفة",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Text("${games.size}",color=Accent)
                    }
                    if(loading) Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Accent)}
                    else if(games.isEmpty()) Text("لم يعثر أندرويد على ألعاب مصنفة كألعاب. يمكنك تحديث القائمة أو تشغيل اللعبة من قائمة التصدير.",color=Muted,fontSize=12.sp)
                    else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        items(games,key={it.pkg}){game->
                            AppCard(game,"تشغيل اللعبة",Icons.Default.PlayArrow){launchApp(game)}
                        }
                    }
                    Text("هذا الوضع ينظم تشغيل ألعابك ويحسب وقت الجلسة؛ لا يغيّر ملفات الألعاب أو يتجاوز قيود أندرويد.",color=Muted,fontSize=10.sp)
                }
                2->{
                    Text("تصدير التطبيقات",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Text("اختر تطبيقاً لحفظ ملف APK الأساسي في المكان الذي تحدده.",color=Muted,fontSize=11.sp)
                    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("ابحث عن تطبيق")},leadingIcon={Icon(Icons.Default.Search,null)})
                    Text(status,color=Accent,fontSize=10.sp)
                    if(loading) Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Accent)}
                    else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        items(exportList,key={it.pkg}){app->
                            AppCard(app,"تصدير APK",Icons.Default.Android){
                                pendingExport=app
                                exportLauncher.launch("${app.pkg}.apk")
                            }
                        }
                    }
                    Text("تنبيه: التطبيقات التي تستخدم split APK قد تحتاج إلى جميع أجزائها لإعادة التثبيت. هذه النسخة تحفظ الملف الأساسي فقط.",color=Muted,fontSize=10.sp)
                }
                else->{
                    Text("المساعد الذكي",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    ActionCard("فتح المساعد السحابي","اتصل بالمساعد بعد إعداد الخادم ومفتاح الخدمة",Icons.Default.Cloud){cloud()}
                    Text("لن ندّعي أنه متصل قبل نجاح الاتصال الفعلي. لا نضع مفاتيح الخدمة السرية داخل التطبيق.",color=Muted,fontSize=11.sp)
                }
            }
        }
    }
}

@Composable
private fun AppCard(app:AppEntry,action:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(15.dp)){
        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(if(app.isGame) Icons.Default.SportsEsports else Icons.Default.Android,null,tint=Accent,modifier=Modifier.size(27.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){
                Text(app.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                Text(app.pkg,color=Muted,fontSize=9.sp)
                Text(formatBytes(app.bytes),color=Muted,fontSize=10.sp)
            }
            Icon(icon,null,tint=Accent)
            Spacer(Modifier.width(4.dp))
            Text(action,color=Accent,fontSize=10.sp)
        }
    }
}

@Composable
private fun ActionCard(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(15.dp)){
        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=Accent,modifier=Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column{Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,fontSize=10.sp)}
        }
    }
}

private fun formatBytes(n:Long):String=when{
    n>=1048576->String.format("%.2f MB",n/1048576.0)
    n>=1024->String.format("%.1f KB",n/1024.0)
    else->"$n B"
}
