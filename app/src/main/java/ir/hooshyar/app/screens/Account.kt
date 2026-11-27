package ir.hooshyar.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var tgToken by remember { mutableStateOf("") }
    var tgWait by remember { mutableStateOf(false) }
    var showMail by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Account.sync() }
    LaunchedEffect(tgToken) {
        if (tgToken.isBlank()) return@LaunchedEffect
        tgWait = true
        var tries = 0
        while (tries < 80 && Store.accountEmail.isBlank()) {
            kotlinx.coroutines.delay(2500)
            tries++
            try {
                if (Account.tgCheck(tgToken)) { toast(ctx, "وارد شدی ✅"); break }
            } catch (e: Exception) {
                if ((e.message ?: "").contains("منقضی") || (e.message ?: "").contains("مسدود")) { toast(ctx, e.message ?: "خطا"); break }
            }
        }
        tgWait = false
        tgToken = ""
    }

    ToolScaffold("حساب کاربری", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), color = c.primary.copy(alpha = 0.12f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("چرا حساب بسازم؟", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("با حساب، اشتراکت به تلگرام (یا ایمیلت) وصل می‌شود. اگر گوشی عوض کنی یا اپ را دوباره نصب کنی، با همان حساب وارد می‌شوی و اشتراکت برمی‌گردد. ساخت حساب فقط برای خرید یا بازیابی اشتراک لازم است؛ بقیه‌ی اپ بدون حساب کار می‌کند.", fontSize = 13.sp, lineHeight = 22.sp)
                }
            }
            if (Store.accountEmail.isNotBlank()) {
                Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("واردشده با", fontSize = 12.sp, color = c.onSurface.copy(alpha = 0.65f))
                        Text(Store.accountEmail, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(if (Store.paid) "اشتراک فعال است ✅" else "اشتراک فعال نیست", fontSize = 13.sp, color = c.primary)
                    }
                }
                OutlinedButton({ scope.launch { try { Account.logout(); toast(ctx, "از حساب خارج شدی") } catch (e: Exception) { toast(ctx, e.message ?: "خطا") } } }, Modifier.fillMaxWidth()) { Text("خروج از حساب") }
            } else {
                GradButton(if (tgWait) "منتظر تأیید در تلگرام…" else "ورود با تلگرام", icon = Icons.Rounded.Send, modifier = Modifier.fillMaxWidth(), loading = tgWait, enabled = !busy && !tgWait, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val (tk, link) = Account.tgStart()
                            tgToken = tk
                            openUrl(ctx, link)
                            toast(ctx, "در تلگرام روی Start بزن و بعد به اپ برگرد")
                        } catch (e: Exception) { toast(ctx, e.message ?: "خطا") }
                        busy = false
                    }
                })
                if (tgWait) Text("بعد از زدن Start در ربات، خودکار وارد می‌شوی.", fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 4.dp))
                TextButton({ showMail = !showMail }, Modifier.fillMaxWidth()) { Text(if (showMail) "بستن ورود با ایمیل" else "ورود با ایمیل") }
                if (showMail) {
                AppField(email, { email = it.trim() }, "ایمیل", Modifier.fillMaxWidth(), ltr = true, maxLines = 1)
                GradButton(if (sent) "ارسال دوباره‌ی کد" else "ارسال کد تأیید", icon = Icons.Rounded.Email, modifier = Modifier.fillMaxWidth(), loading = busy && !sent, enabled = email.contains("@") && !busy, onClick = {
                    scope.launch {
                        busy = true
                        try { Account.sendCode(email); sent = true; toast(ctx, "کد به ایمیلت فرستاده شد (پوشه‌ی هرزنامه را هم ببین)") } catch (e: Exception) { toast(ctx, e.message ?: "خطا") }
                        busy = false
                    }
                })
                if (sent) {
                    AppField(code, { code = it.filter { ch -> ch.isDigit() }.take(6) }, "کد ۶ رقمی", Modifier.fillMaxWidth(), ltr = true, maxLines = 1)
                    GradButton("ورود", icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), loading = busy, enabled = code.length == 6 && !busy, onClick = {
                        scope.launch {
                            busy = true
                            try { Account.verify(email, code); toast(ctx, "وارد شدی ✅"); code = ""; sent = false } catch (e: Exception) { toast(ctx, e.message ?: "خطا") }
                            busy = false
                        }
                    })
                }
                }
                Text("فقط شناسه‌ی حساب تلگرام یا ایمیل برای ورود و بازیابی اشتراک نگه‌داری می‌شود.", fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
