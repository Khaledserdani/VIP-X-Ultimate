package com.vipx.ultimate

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF07111F)
private val Panel = Color(0xFF122238)
private val Accent = Color(0xFF42E3C2)
private val Muted = Color(0xFF9FB2C7)
private const val TANGO_PACKAGE = "com.sgiggle.production"

private data class NetworkStatus(
    val connection: String,
    val validated: Boolean,
    val downKbps: Int,
    val upKbps: Int,
    val metered: Boolean
)

private fun readNetworkStatus(context: android.content.Context): NetworkStatus {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork
        ?: return NetworkStatus("غير متصل", false, 0, 0, true)
    val caps = manager.getNetworkCapabilities(network)
        ?: return NetworkStatus("غير معروف", false, 0, 0, manager.isActiveNetworkMetered)
    val connection = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الهاتف"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "اتصال آخر"
    }
    return NetworkStatus(
        connection = connection,
        validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        downKbps = caps.linkDownstreamBandwidthKbps,
        upKbps = caps.linkUpstreamBandwidthKbps,
        metered = manager.isActiveNetworkMetered
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent,
                background = Background,
                surface = Panel,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                LiveGridHome()
            }
        }
    }
}

@Composable
private fun LiveGridHome() {
    val context = LocalContext.current
    var columns by rememberSaveable { mutableIntStateOf(2) }
    var previewLimit by rememberSaveable { mutableIntStateOf(4) }
    var dataSaver by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var network by remember { mutableStateOf(readNetworkStatus(context)) }
    val tangoIntent = remember(context) {
        context.packageManager.getLaunchIntentForPackage(TANGO_PACKAGE)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("VIP X", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("LIVE GRID", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("شبكة البثوث المباشرة", color = Muted, fontSize = 14.sp)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("التطبيق المستهدف", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                if (tangoIntent != null) "Tango مثبت على الهاتف" else "Tango غير مثبت أو غير متاح",
                                color = if (tangoIntent != null) Accent else Muted,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (tangoIntent != null) {
                                    context.startActivity(tangoIntent)
                                } else {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TANGO_PACKAGE")))
                                    }.onFailure {
                                        message = "تعذر فتح صفحة Tango."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                        ) {
                            Text("فتح Tango", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إعداد الشبكة", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الأعمدة", color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        ChoiceButton("2", columns == 2) { columns = 2 }
                        ChoiceButton("3", columns == 3) { columns = 3 }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("عدد الخانات", color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        listOf(2, 4, 6).forEach { count ->
                            ChoiceButton(count.toString(), previewLimit == count, enabled = !dataSaver || count == 2) { previewLimit = count }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("توفير البيانات", color = Color.White, fontSize = 13.sp)
                            Text("يُستخدم عند توفر مصدر معاينات فعلي", color = Muted, fontSize = 10.sp)
                        }
                        Switch(checked = dataSaver, onCheckedChange = { enabled -> dataSaver = enabled; if (enabled) previewLimit = 2 })
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("حالة الاتصال", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { network = readNetworkStatus(context) }) { Text("تحديث") }
                    }
                    Text(
                        "${network.connection} • " + if (network.validated) "إنترنت متحقق" else "الإنترنت غير متحقق",
                        color = if (network.validated) Accent else Color(0xFFFFD27A),
                        fontSize = 12.sp
                    )
                    Text("تقدير النظام: تنزيل ${network.downKbps} kbps • رفع ${network.upKbps} kbps", color = Muted, fontSize = 10.sp)
                    Text(if (network.metered) "الشبكة محسوبة البيانات" else "الشبكة غير محسوبة البيانات", color = Muted, fontSize = 10.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("معاينات البث", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$previewLimit خانات", color = Accent, fontSize = 12.sp)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items((1..previewLimit).toList()) { index ->
                    PreviewSlot(index = index, compact = columns == 3)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF332B1A)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("مصدر الفيديو غير مربوط بعد", color = Color(0xFFFFD27A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "هذه الخانات ليست بثوثاً حية وهمية. يلزم أولاً مصدر معاينات رسمي ومصرّح به من Tango؛ بعدها نربط تشغيل عدة فيديوهات حقيقية ونختبرها.",
                        color = Color(0xFFE7DCC4), fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
            if (message.isNotBlank()) {
                Text(message, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (selected) Background else Color.White,
            containerColor = if (selected) Accent else Color.Transparent
        ),
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PreviewSlot(index: Int, compact: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().height(if (compact) 112.dp else 142.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(if (compact) 27.dp else 34.dp)
                    .background(Color(0xFF20364E), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text("•", color = Accent, fontSize = 20.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text("البث $index", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("بانتظار مصدر المعاينة", color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}
