package com.example.aihub.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.R
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- تنظیمات

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme


    ToolScaffold("تنظیمات", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("حساب و اشتراک")
            Surface(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp), color = c.surfaceVariant,
            ) {
                Text(
                    "همه‌ی بخش‌ها را می‌توانی رایگان امتحان کنی؛ برای ذخیره، دانلود و کپی نتیجه اشتراک لازم است.",
                    Modifier.padding(14.dp), fontSize = 13.sp, lineHeight = 22.sp,
                )
            }
            var codeIn by remember { mutableStateOf("") }
            var srvInfo by remember { mutableStateOf("") }
            var srvBusy by remember { mutableStateOf(false) }
            if (!Store.serverOn) {
                Text(
                    "اتصال به سرور هنوز برقرار نشده است. بعداً دوباره تلاش کن.",
                    Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.error,
                )
            }
            if (Store.serverOn) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            srvBusy = true
                            srvInfo = try { Server.status() } catch (e: Exception) { e.message ?: "خطا" }
                            srvBusy = false
                        }
                    },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { if (srvBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("وضعیت اشتراک و سهمیه‌ی امروز") }
                if (srvInfo.isNotBlank()) Text(srvInfo, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, lineHeight = 20.sp)
                AppField(codeIn, { codeIn = it }, "کد اشتراک (XXXX-XXXX-XXXX)", Modifier.padding(horizontal = 16.dp), ltr = true, maxLines = 1)
                GradButton(
                    "فعال‌سازی اشتراک", icon = Icons.Rounded.Check,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            try {
                                Server.redeem(codeIn)
                                codeIn = ""
                                srvInfo = Server.status()
                                toast(ctx, "اشتراک فعال شد ✅")
                            } catch (e: Exception) { toast(ctx, e.message ?: "خطا") }
                        }
                    },
                )
                Text(
                    "شناسه‌ی این گوشی (برای فعال‌سازی دستی توسط پشتیبانی): ${Store.deviceId}",
                    Modifier.padding(horizontal = 20.dp), fontSize = 11.sp, color = c.onBackground.copy(alpha = 0.6f),
                )
            }

            SectionTitle("صدای خود گوشی (وقتی آنلاین نیست)")
            var rate by remember { mutableStateOf(Store.ttsRate) }
            var pitch by remember { mutableStateOf(Store.ttsPitch) }
            val tester = remember { Speaker(ctx) }
            DisposableEffect(Unit) { onDispose { tester.shutdown() } }
            Text("سرعت: ${"%.2f".format(rate)}", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            Slider(
                value = rate, onValueChange = { rate = it }, valueRange = 0.5f..1.4f,
                onValueChangeFinished = { Store.saveTts(rate, pitch) }, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("تُن (زیر و بمی): ${"%.2f".format(pitch)}", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            Slider(
                value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..1.4f,
                onValueChangeFinished = { Store.saveTts(rate, pitch) }, modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedButton(
                onClick = {
                    Store.saveTts(rate, pitch)
                    scope.launch { tester.speak("سلام، من دستیار هوشمند هستم. این صدای آزمایشی است.", locale = java.util.Locale("fa", "IR")) }
                },
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) { Text("پخش صدای آزمایشی") }
            Text(
                "برای فارسی، موتور صدای گوشی باید eSpeak NG باشد (راهنما در بخش «راهنما»). اگر صدا می‌لرزد، سرعت و تُن را کمی پایین بیاور.",
                Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.7f), lineHeight = 18.sp,
            )

            SectionTitle("ظاهر برنامه")
            ChipRow(listOf(0, 1, 2), Store.themeMode, { listOf("خودکار", "روشن", "تاریک")[it] }) { Store.saveTheme(it) }
            Text(
                "نکته: اگر سرویسی وصل نشد، VPN را روشن کن یا سرویس دیگری انتخاب کن. سهمیه‌ی رایگان هر سرویس محدود است.",
                Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

