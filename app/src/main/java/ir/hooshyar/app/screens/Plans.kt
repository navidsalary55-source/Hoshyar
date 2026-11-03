package ir.hooshyar.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*

/** قیمت‌ها به هزار تومان. برای تغییر قیمت فقط همین لیست را عوض کن. */
private val planPrices = listOf(
    1 to 300, 2 to 550, 3 to 800, 4 to 1000, 5 to 1200, 6 to 1400,
    7 to 1600, 8 to 1800, 9 to 2200, 10 to 2400, 11 to 2600, 12 to 2800,
)

private fun fa(n: Int): String = n.toString().map { if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("")

private fun faMoney(k: Int): String {
    val m = k / 1000; val r = k % 1000
    return when {
        m == 0 -> "${fa(k)} هزار تومان"
        r == 0 -> "${fa(m)} میلیون تومان"
        else -> "${fa(m)} میلیون و ${fa(r)} هزار تومان"
    }
}

private fun planName(m: Int) = when (m) {
    1 -> "یک ماهه"; 2 -> "دو ماهه"; 3 -> "سه ماهه"; 4 -> "چهار ماهه"; 5 -> "پنج ماهه"; 6 -> "شش ماهه"
    7 -> "هفت ماهه"; 8 -> "هشت ماهه"; 9 -> "نُه ماهه"; 10 -> "ده ماهه"; 11 -> "یازده ماهه"; else -> "یک ساله"
}

@Composable
fun PlansScreen(onBack: () -> Unit, onHaveCode: () -> Unit, onAccount: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val c = MaterialTheme.colorScheme
    var sel by remember { mutableStateOf(6) }
    val scope = rememberCoroutineScope()
    val bazaar = remember { Billing.fromBazaar(ctx) }
    var payBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Store.accountEmail) { if (bazaar && Store.accountEmail.isNotBlank()) Billing.restore(ctx) { n, _ -> if (n > 0) scope.launch { Server.refresh() } } }
    val monthly = planPrices[0].second
    ToolScaffold("قیمت اشتراک", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), color = c.primary.copy(alpha = 0.12f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("با اشتراک چه می‌گیری؟", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("✔ ذخیره، دانلود، کپی و اشتراک‌گذاری همه‌ی خروجی‌ها\n✔ سهمیه‌ی روزانه‌ی بیشتر\n✔ استفاده از همه‌ی بخش‌ها بدون محدودیت قفل", fontSize = 13.sp, lineHeight = 22.sp)
                }
            }
            planPrices.forEach { (m, k) ->
                val on = sel == m
                val perMonth = k / m
                val off = ((1f - k.toFloat() / (monthly * m)) * 100).toInt()
                Surface(
                    Modifier.fillMaxWidth().clickable { sel = m },
                    shape = RoundedCornerShape(20.dp),
                    color = if (on) c.primary.copy(alpha = 0.14f) else c.surfaceVariant,
                    border = if (on) BorderStroke(2.dp, c.primary) else null,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(on, { sel = m })
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(planName(m), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (m == 6) PlanTag("پرطرفدار")
                                if (m == 12) PlanTag("به‌صرفه‌ترین")
                            }
                            Text(if (m == 1) "بدون تخفیف" else "ماهی ${faMoney(perMonth)}", fontSize = 12.sp, color = c.onSurface.copy(alpha = 0.65f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(faMoney(k), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = c.primary)
                            if (off > 0) Text("${fa(off)}٪ تخفیف", fontSize = 11.5.sp, color = Color0)
                        }
                    }
                }
            }
            val k = planPrices.first { it.first == sel }.second
            GradButton("خرید ${planName(sel)} — ${faMoney(k)}", icon = Icons.Rounded.Send, modifier = Modifier.fillMaxWidth(), loading = payBusy, enabled = !payBusy, onClick = {
                if (Store.accountEmail.isBlank()) { toast(ctx, "اول وارد حساب شو تا اشتراکت به حسابت وصل شود"); onAccount(); return@GradButton }
                if (bazaar) {
                    val act = Billing.activityOf(ctx)
                    if (act == null) { toast(ctx, "پرداخت در دسترس نیست"); return@GradButton }
                    payBusy = true
                    Billing.buy(act, sel) { ok, m -> payBusy = false; toast(ctx, m); if (ok) scope.launch { Server.refresh() } }
                    return@GradButton
                }
                val msg = "سلام، می‌خوام اشتراک ${planName(sel)} (${faMoney(k)}) هوش مصنوعی هوشیار رو بخرم.\nحساب: ${Store.accountEmail}\nشناسه‌ی گوشی: ${Store.deviceId}"
                clip.setText(AnnotatedString(msg))
                toast(ctx, "پیام آماده کپی شد؛ در تلگرام بفرست")
                openUrl(ctx, Config.URL_TELEGRAM)
            })
            if (Store.accountEmail.isBlank()) OutlinedButton(onAccount, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AccountCircle, null); Spacer(Modifier.width(6.dp)); Text("ورود به حساب (بازیابی اشتراک قبلی)") }
            else Text("حساب: ${Store.accountEmail}", fontSize = 12.sp, color = c.primary, modifier = Modifier.padding(horizontal = 4.dp))
            if (bazaar) OutlinedButton({ Billing.restore(ctx) { _, m -> toast(ctx, m); scope.launch { Server.refresh() } } }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("بازیابی خرید") }
            else OutlinedButton(onHaveCode, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.VpnKey, null); Spacer(Modifier.width(6.dp)); Text("کد اشتراک دارم") }
            Text(if (bazaar) "پرداخت از طریق کافه‌بازار انجام می‌شود و اشتراک بلافاصله روی حسابت فعال می‌شود. همه‌ی قیمت‌ها به تومان است." else "بعد از پرداخت، کد اشتراک برایت فرستاده می‌شود و در بخش تنظیمات وارد می‌کنی. همه‌ی قیمت‌ها به تومان است.", fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = c.onBackground.copy(alpha = 0.6f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0xFF16A34A)

@Composable
private fun PlanTag(t: String) {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(t, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}
