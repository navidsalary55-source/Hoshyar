package ir.hooshyar.app.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.launch

private fun faD(n: Int): String = n.toString().map { if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("")

@Composable
fun InviteScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var info by remember { mutableStateOf<RefInfo?>(null) }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var friend by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true; err = ""
        try { info = Referral.info() } catch (e: Exception) { err = e.message ?: "خطا" }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    ToolScaffold("دعوت دوستان", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val i = info
            Surface(shape = RoundedCornerShape(22.dp), color = c.primary.copy(alpha = 0.12f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎁 دوستت را دعوت کن، هر دو هدیه بگیرید", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "دوستت با وارد کردن کد تو ${faD(i?.inviteeDays ?: 3)} روز اشتراک رایگان می‌گیرد. وقتی از اپ استفاده کرد، تو هم ${faD(i?.inviterDays ?: 7)} روز اشتراک رایگان می‌گیری.",
                        fontSize = 13.sp, lineHeight = 22.sp,
                    )
                }
            }
            if (loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            if (err.isNotBlank()) { ErrorText(err); OutlinedButton({ scope.launch { load() } }, Modifier.fillMaxWidth()) { Text("تلاش دوباره") } }
            if (i != null) {
                Surface(shape = RoundedCornerShape(22.dp), color = c.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("کد دعوت تو", fontSize = 12.sp, color = c.onSurface.copy(alpha = 0.65f))
                        Text(i.code, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = c.primary, letterSpacing = 4.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ clip.setText(AnnotatedString(i.code)); toast(ctx, "کد کپی شد") }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("کپی کد") }
                        }
                    }
                }
                GradButton("ارسال دعوت به دوستان", icon = Icons.Rounded.Share, modifier = Modifier.fillMaxWidth(), onClick = {
                    val msg = "سلام 👋 اپ «هوش مصنوعی هوشیار» رو امتحان کن: چت، ساخت عکس و ویدیو، حذف اشیاء از عکس و کلی ابزار دیگه.\nکد دعوت من: ${i.code}\nبعد از نصب، از منوی کناری «دعوت دوستان» این کد رو وارد کن تا ${faD(i.inviteeDays)} روز اشتراک رایگان بگیری 🎁\n${Config.SHARE_LINK}"
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg) }
                    ctx.startActivity(Intent.createChooser(send, "ارسال دعوت").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("دعوت‌شده", faD(i.invited))
                        Stat("جایزه گرفته", faD(i.rewarded))
                        Stat("روز هدیه", faD(i.earnedDays))
                    }
                }
                Text("حداکثر ${faD(i.max)} دعوت موفق جایزه دارد. دعوت وقتی موفق حساب می‌شود که دوستت واقعاً از اپ استفاده کند.", fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = c.onBackground.copy(alpha = 0.6f))

                if (!i.usedCode) {
                    SectionTitle("کد دعوت دوستت را داری؟")
                    AppField(friend, { friend = it.uppercase().take(8) }, "کد دعوت (۶ حرفی)", Modifier.fillMaxWidth(), ltr = true, maxLines = 1)
                    GradButton("ثبت کد و گرفتن هدیه", icon = Icons.Rounded.CardGiftcard, modifier = Modifier.fillMaxWidth(), loading = busy, enabled = friend.length >= 6 && !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val d = Referral.use(friend)
                                toast(ctx, "${faD(d)} روز اشتراک رایگان فعال شد 🎉")
                                friend = ""
                                Server.refresh()
                                load()
                            } catch (e: Exception) { toast(ctx, e.message ?: "خطا") }
                            busy = false
                        }
                    })
                } else Text("✔ کد دعوت قبلاً ثبت شده است.", fontSize = 13.sp, color = c.primary, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Stat(label: String, v: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
