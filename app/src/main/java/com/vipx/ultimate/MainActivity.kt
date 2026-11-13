package com.vipx.ultimate

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list=if(Build.VERSION.SDK_INT>=33)
            packageManager.queryIntentActivities(intent,PackageManager.ResolveInfoFlags.of(0))
        else packageManager.queryIntentActivities(intent,0)
        return list.mapNotNull{r->
            val ai=r.activityInfo?.applicationInfo?:return@mapNotNull null
            if(ai.packageName==packageName)return@mapNotNull null
            InstalledApp(packageManager.getApplicationLabel(ai).toString(),ai.packageName)
        }.distinctBy{it.packageName}.sortedBy{it.name.lowercase()}
    }
}

@Composable
private fun VipBuilderScreen(loadApps:()->List<InstalledApp>){
    val scope= rememberCoroutineScope()
    var apps by remember{mutableStateOf(emptyList<InstalledApp>())}
    var selected by remember{mutableStateOf<InstalledApp?>(null)}
    var query by remember{mutableStateOf("")}
    var goal by remember{mutableStateOf("أنشئ تصوراً احترافياً لنسخة VIP متوافقة، بواجهة راقية وميزات عملية دون تعطيل التطبيق الأصلي.") }
    var endpoint by remember{mutableStateOf("https://vip-x-ultimate-api-production.up.railway.app")}
    var token by remember{mutableStateOf("")}
    var answer by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("اختر تطبيقاً من هاتفك للبدء")}
    var busy by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){apps=withContext(Dispatchers.IO){loadApps()};status="تم العثور على ${apps.size} تطبيقاً"}
    val visible=remember(apps,query){apps.filter{it.name.contains(query,true)||it.packageName.contains(query,true)}}
    Surface(Modifier.fillMaxSize(),color=Navy){
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("VIP X ULTIMATE",color=Mint,fontSize=13.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.8.sp)
                    Text("AI VIP Builder",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold)
                    Text("حوّل اختيارك إلى خطة تطوير ذكية",color=Soft,fontSize=12.sp)
                }
                Icon(Icons.Default.AutoAwesome,null,tint=Mint,modifier=Modifier.size(34.dp))
            }
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("1 • اختر التطبيق المثبّت",color=Color.White,fontWeight=FontWeight.Bold)
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("ابحث في تطبيقاتك")},leadingIcon={Icon(Icons.Default.Search,null)})
                    Text("المحدد: ${selected?.name?:"لم تختر تطبيقاً بعد"}",color=Mint,fontSize=12.sp)
                }
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
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
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("2 • الذكاء الاصطناعي",color=Color.White,fontWeight=FontWeight.Bold)
                    OutlinedTextField(goal,{goal=it},Modifier.fillMaxWidth(),label={Text("ما الذي تريد تحسينه؟")},minLines=2,maxLines=3)
                    OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("رمز ربط خادم VIP X")})
                    Text(status,color=Mint,fontSize=10.sp)
                    Button(onClick={
                        val app=selected
                        if(app==null){status="اختر تطبيقاً أولاً";return@Button}
                        if(token.isBlank()||!endpoint.startsWith("https://")){status="أدخل رمز الربط الصحيح";return@Button}
                        busy=true;answer="";status="الذكاء الاصطناعي يحلل التطبيق والطلب..."
                        scope.launch{
                            try{
                                val result=withContext(Dispatchers.IO){
                                    val conn=(URL(endpoint.trimEnd('/')+"/api/vip-plan").openConnection() as HttpURLConnection).apply{
                                        requestMethod="POST";connectTimeout=20000;readTimeout=90000;doOutput=true
                                        setRequestProperty("Content-Type","application/json; charset=utf-8")
                                        setRequestProperty("Authorization","Bearer "+token.trim())
                                    }
                                    try{
                                        val body=JSONObject().put("appName",app.name).put("packageName",app.packageName).put("goal",goal).toString()
                                        conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
                                        val stream=if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream
                                        val response=stream.bufferedReader().use{it.readText()}
                                        if(conn.responseCode !in 200..299)throw IllegalStateException(JSONObject(response).optString("error","HTTP ${conn.responseCode}"))
                                        JSONObject(response).optString("reply")
                                    }finally{conn.disconnect()}
                                }
                                answer=result
                                status="اكتمل تحليل الذكاء الاصطناعي"
                            }catch(e:Exception){status="لم يكتمل الاتصال: ${e.message?:"تحقق من الخادم والرمز"}"}
                            finally{busy=false}
                        }
                    },enabled=!busy,modifier=Modifier.fillMaxWidth()){
                        if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                        else Icon(Icons.Default.AutoAwesome,null)
                        Spacer(Modifier.width(8.dp));Text(if(busy)"جارٍ التحليل..." else "حلّل وابتكر ميزات VIP")
                    }
                }
            }
            if(answer.isNotBlank()){
                Card(Modifier.fillMaxWidth().heightIn(max=190.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF102F32))){
                    Column(Modifier.padding(12.dp)){
                        Text("نتيجة تحليل VIP",color=Mint,fontWeight=FontWeight.Bold)
                        LazyColumn{item{Text(answer,color=Color.White,fontSize=12.sp)}}
                    }
                }
            }
            Text("تنبيه تقني: التحليل يقترح تطويراً متوافقاً؛ لا يغيّر التطبيق الأصلي ولا ينتج APK معدّلاً تلقائياً بعد. البناء الفعلي يتطلب مصدر التطبيق أو قالباً متوافقاً ومحرك بناء.",color=Soft,fontSize=9.sp)
        }
    }
}
