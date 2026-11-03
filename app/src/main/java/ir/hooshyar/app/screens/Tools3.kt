package ir.hooshyar.app.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

// ---------------------------------------------------------------- QR کد و کارت ویزیت

private val qrColors = listOf(0xFF111111.toInt(), 0xFF1E3A8A.toInt(), 0xFF7C3AED.toInt(), 0xFFBE123C.toInt(), 0xFF047857.toInt(), 0xFFC2410C.toInt(), 0xFF0F766E.toInt())
private val qrBgs = listOf(0xFFFFFFFF.toInt(), 0xFFFFF7E6.toInt(), 0xFFE8F4FF.toInt(), 0xFFF3E8FF.toInt(), 0xFFE7FBEF.toInt(), 0xFF111111.toInt())

private fun renderQr(text: String, ecl: Int, sizePx: Int, fg: Int, fg2: Int, bg: Int, rounded: Boolean, logo: String): Bitmap {
    val m = QrEncoder.encode(text, if (logo.isNotBlank()) 3 else ecl)
    val n = m.size
    val quiet = 3
    val cell = sizePx.toFloat() / (n + quiet * 2)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(bg)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), fg, fg2, Shader.TileMode.CLAMP) }
    val skip = if (logo.isNotBlank()) (n * 0.24f).toInt() / 2 else -1
    val c0 = n / 2
    for (y in 0 until n) for (x in 0 until n) {
        if (!m[y][x]) continue
        if (skip >= 0 && Math.abs(x - c0) <= skip && Math.abs(y - c0) <= skip) continue
        val l = (x + quiet) * cell; val t = (y + quiet) * cell
        if (rounded) cv.drawRoundRect(RectF(l + cell * 0.04f, t + cell * 0.04f, l + cell * 0.96f, t + cell * 0.96f), cell * 0.42f, cell * 0.42f, p)
        else cv.drawRect(l - 0.3f, t - 0.3f, l + cell + 0.3f, t + cell + 0.3f, p)
    }
    if (logo.isNotBlank()) {
        val r = (skip + 1) * cell
        val cx = sizePx / 2f
        val bgp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        cv.drawRoundRect(RectF(cx - r, cx - r, cx + r, cx + r), r * 0.4f, r * 0.4f, bgp)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = r * 1.15f; textAlign = Paint.Align.CENTER; color = fg }
        cv.drawText(logo.take(2), cx, cx + tp.textSize * 0.35f, tp)
    }
    return bmp
}

private fun waNumber(s: String): String {
    var d = s.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2) else if (d.startsWith("09")) d = "98" + d.drop(1) else if (d.startsWith("0")) d = "98" + d.drop(1)
    return d
}

private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")

private fun vcard(name: String, org: String, title: String, phone: String, email: String, url: String, addr: String): String {
    val sb = StringBuilder("BEGIN:VCARD\nVERSION:3.0\n")
    sb.append("N:;").append(name).append(";;;\nFN:").append(name).append("\n")
    if (org.isNotBlank()) sb.append("ORG:").append(org).append("\n")
    if (title.isNotBlank()) sb.append("TITLE:").append(title).append("\n")
    if (phone.isNotBlank()) sb.append("TEL;TYPE=CELL:").append(phone).append("\n")
    if (email.isNotBlank()) sb.append("EMAIL:").append(email).append("\n")
    if (url.isNotBlank()) sb.append("URL:").append(url).append("\n")
    if (addr.isNotBlank()) sb.append("ADR:;;").append(addr).append(";;;;\n")
    sb.append("END:VCARD")
    return sb.toString()
}

private val cardThemes = listOf(
    0xFF7C6CFF.toInt() to 0xFF3FA2F7.toInt(), 0xFFFF5C8A.toInt() to 0xFFFFA62B.toInt(), 0xFF0F172A.toInt() to 0xFF334155.toInt(),
    0xFF047857.toInt() to 0xFF34D399.toInt(), 0xFFBE123C.toInt() to 0xFF7C3AED.toInt(), 0xFFF59E0B.toInt() to 0xFFEF4444.toInt(),
)

private fun renderCard(ctx: android.content.Context, theme: Int, name: String, title: String, org: String, phone: String, email: String, url: String, addr: String, qr: Bitmap?): Bitmap {
    val W = 1050; val H = 600
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val (c1, c2) = cardThemes[theme]
    cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), c1, c2, Shader.TileMode.CLAMP) })
    val deco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    cv.drawCircle(W * 0.88f, -40f, 260f, deco); cv.drawCircle(W * 0.1f, H + 60f, 220f, deco)
    val bold = try { Typeface.createFromAsset(ctx.assets, "fonts/Vazirmatn-Bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
    val reg = try { Typeface.createFromAsset(ctx.assets, "fonts/Vazirmatn-Regular.ttf") } catch (e: Exception) { Typeface.DEFAULT }
    val right = W - 70f
    var textW = if (qr != null) W - 70 - 390 else W - 140
    fun draw(text: String, size: Float, tf: Typeface, alpha: Int, y: Float): Float {
        if (text.isBlank()) return y
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; this.alpha = alpha; textSize = size; typeface = tf }
        val lay = StaticLayout.Builder.obtain(text, 0, text.length, tp, textW).setAlignment(Layout.Alignment.ALIGN_OPPOSITE).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        cv.save(); cv.translate(right - textW, y); lay.draw(cv); cv.restore()
        return y + lay.height + 8f
    }
    var y = 70f
    y = draw(name.ifBlank { "نام و نام خانوادگی" }, 66f, bold, 255, y)
    y = draw(title, 36f, reg, 235, y)
    y = draw(org, 34f, bold, 235, y)
    y += 24f
    y = draw(if (phone.isNotBlank()) "$phone  ☎" else "", 34f, reg, 255, y)
    y = draw(if (email.isNotBlank()) "$email  ✉" else "", 30f, reg, 255, y)
    y = draw(if (url.isNotBlank()) "$url  🌐" else "", 30f, reg, 255, y)
    y = draw(if (addr.isNotBlank()) "$addr  📍" else "", 28f, reg, 235, y)
    if (qr != null) {
        val s = 300
        val x0 = 60f; val y0 = (H - s) / 2f
        cv.drawRoundRect(RectF(x0 - 14, y0 - 14, x0 + s + 14, y0 + s + 14), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        cv.drawBitmap(Bitmap.createScaledBitmap(qr, s, s, true), x0, y0, null)
    }
    return bmp
}

@Composable
fun QrScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }           // ۰ QR، ۱ کارت ویزیت
    var type by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var wa by remember { mutableStateOf("") }; var waMsg by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }; var wpass by remember { mutableStateOf("") }; var open by remember { mutableStateOf(false) }
    var tel by remember { mutableStateOf("") }
    var mail by remember { mutableStateOf("") }; var subj by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }; var jobTitle by remember { mutableStateOf("") }; var org by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var site by remember { mutableStateOf("") }; var addr by remember { mutableStateOf("") }
    var fg by remember { mutableStateOf(qrColors[0]) }; var fg2 by remember { mutableStateOf(qrColors[0]) }; var bg by remember { mutableStateOf(qrBgs[0]) }
    var rounded by remember { mutableStateOf(true) }
    var logo by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(0) }
    var cardQr by remember { mutableStateOf(true) }
    val c = MaterialTheme.colorScheme

    val payload: String = when (type) {
        1 -> if (waNumber(wa).isBlank()) "" else "https://wa.me/" + waNumber(wa) + (if (waMsg.isBlank()) "" else "?text=" + URLEncoder.encode(waMsg, "UTF-8"))
        2 -> if (ssid.isBlank()) "" else "WIFI:T:${if (open) "nopass" else "WPA"};S:${esc(ssid)};" + (if (open) "" else "P:${esc(wpass)};") + ";"
        3 -> if (tel.isBlank()) "" else "tel:" + tel.filter { it.isDigit() || it == '+' }
        4 -> if (mail.isBlank()) "" else "mailto:$mail" + (if (subj.isBlank()) "" else "?subject=" + URLEncoder.encode(subj, "UTF-8").replace("+", "%20"))
        5 -> if (name.isBlank()) "" else vcard(name, org, jobTitle, phone, email, site, addr)
        else -> text
    }
    val qrBmp: Bitmap? = remember(payload, fg, fg2, bg, rounded, logo, mode, cardQr) {
        val pl = if (mode == 1) (if (name.isBlank() || !cardQr) "" else vcard(name, org, jobTitle, phone, email, site, addr)) else payload
        if (pl.isBlank()) null else try { renderQr(pl, 1, 900, fg, fg2, bg, rounded, if (mode == 0) logo else "") } catch (e: Exception) { null }
    }
    val cardBmp: Bitmap? = remember(mode, theme, name, jobTitle, org, phone, email, site, addr, qrBmp) { if (mode == 1) renderCard(ctx, theme, name, jobTitle, org, phone, email, site, addr, qrBmp) else null }
    val shown: Bitmap? = if (mode == 1) cardBmp else qrBmp

    ToolScaffold("QR کد و کارت ویزیت", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipRow(listOf(0, 1), mode, { if (it == 0) "ساخت QR کد 🔳" else "کارت ویزیت 💼" }) { mode = it }
            if (mode == 0) {
                ChipRow((0..5).toList(), type, { listOf("متن / لینک", "واتساپ", "وای‌فای", "تماس", "ایمیل", "مخاطب")[it] }) { type = it }
                val pad = Modifier.padding(horizontal = 16.dp)
                when (type) {
                    0 -> AppField(text, { text = it }, "متن یا لینک (مثلاً اینستاگرام، سایت، منو)", pad, minLines = 2, ltr = false)
                    1 -> { AppField(wa, { wa = it }, "شماره‌ی واتساپ (مثلاً 09121234567)", pad, ltr = true); AppField(waMsg, { waMsg = it }, "پیام آماده (اختیاری)", pad, minLines = 2) }
                    2 -> {
                        AppField(ssid, { ssid = it }, "نام وای‌فای", pad, ltr = true)
                        if (!open) AppField(wpass, { wpass = it }, "رمز وای‌فای", pad, ltr = true)
                        Row(pad, verticalAlignment = Alignment.CenterVertically) { Switch(open, { open = it }); Spacer(Modifier.width(10.dp)); Text("شبکه رمز ندارد", fontSize = 13.sp) }
                    }
                    3 -> AppField(tel, { tel = it }, "شماره‌ی تلفن", pad, ltr = true)
                    4 -> { AppField(mail, { mail = it }, "آدرس ایمیل", pad, ltr = true); AppField(subj, { subj = it }, "موضوع (اختیاری)", pad) }
                    else -> { AppField(name, { name = it }, "نام", pad); AppField(org, { org = it }, "شرکت یا سازمان (اختیاری)", pad); AppField(phone, { phone = it }, "تلفن", pad, ltr = true); AppField(email, { email = it }, "ایمیل", pad, ltr = true) }
                }
            } else {
                val pad = Modifier.padding(horizontal = 16.dp)
                AppField(name, { name = it }, "نام و نام خانوادگی", pad)
                AppField(jobTitle, { jobTitle = it }, "عنوان شغلی", pad)
                AppField(org, { org = it }, "نام شرکت یا کسب‌وکار", pad)
                AppField(phone, { phone = it }, "شماره‌ی تماس", pad, ltr = true)
                AppField(email, { email = it }, "ایمیل", pad, ltr = true)
                AppField(site, { site = it }, "وب‌سایت یا اینستاگرام", pad, ltr = true)
                AppField(addr, { addr = it }, "آدرس (اختیاری)", pad)
            }

            if (shown != null) {
                BitmapImage(shown, Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(16.dp)))
            } else Text(if (mode == 0) "اطلاعات را وارد کن تا QR کد ساخته شود." else "نام را بنویس تا کارت ساخته شود.", Modifier.padding(horizontal = 20.dp), color = c.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            if (mode == 1 && shown == null) {
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp))
            }

            if (mode == 1) {
                SectionTitle("طرح کارت")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    cardThemes.forEachIndexed { i, (a, b) ->
                        Box(Modifier.size(if (theme == i) 46.dp else 40.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(a), Color(b)))).clickable { theme = i }, contentAlignment = Alignment.Center) {
                            if (theme == i) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Switch(cardQr, { cardQr = it }); Spacer(Modifier.width(10.dp)); Text("QR ذخیره‌ی مخاطب روی کارت", fontSize = 13.sp) }
            } else {
                SectionTitle("رنگ QR")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    qrColors.forEach { col -> Box(Modifier.size(if (fg == col) 42.dp else 36.dp).clip(CircleShape).background(Color(col)).clickable { fg = col; fg2 = col }, contentAlignment = Alignment.Center) { if (fg == col && fg == fg2) Icon(Icons.Rounded.Check, null, tint = Color.White) } }
                    qrColors.drop(1).forEach { col -> Box(Modifier.size(36.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(qrColors[1]), Color(col)))).clickable { fg = qrColors[1]; fg2 = col }) }
                }
                SectionTitle("پس‌زمینه")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    qrBgs.forEach { col -> Box(Modifier.size(if (bg == col) 42.dp else 36.dp).clip(CircleShape).background(Color(col)).clickable { bg = col; if (col == qrBgs.last() && fg == qrColors[0]) { fg = qrBgs[0]; fg2 = qrBgs[0] } }, contentAlignment = Alignment.Center) { if (bg == col) Icon(Icons.Rounded.Check, null, tint = if (col == qrBgs.last()) Color.White else Color.Black) } }
                }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Switch(rounded, { rounded = it }); Spacer(Modifier.width(10.dp)); Text("نقطه‌های گرد", fontSize = 13.sp) }
                AppField(logo, { logo = it.take(2) }, "ایموجی یا حرف وسط QR (اختیاری)", Modifier.padding(horizontal = 16.dp))
            }

            if (shown != null) {
                GradButton("ذخیره در گالری", icon = Icons.Rounded.Download, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = {
                    if (canExport()) scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveBitmapToGallery(ctx, shown) }
                        toast(ctx, if (ok) "در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                    }
                })
                OutlinedButton({ if (canExport()) shareBitmap(ctx, shown) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری") }
            }
            Text("بعد از ساخت، QR را با دوربین گوشی امتحان کن. QR وسط‌چین با ایموجی ممکن است در گوشی‌های قدیمی دیرتر خوانده شود.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.55f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- عکس پاسپورتی / پرسنلی

private class PSize(val label: String, val wMm: Float, val hMm: Float)
private val pSizes = listOf(PSize("۳×۴ سانتی", 30f, 40f), PSize("۴×۶ سانتی", 40f, 60f), PSize("۳.۵×۴.۵ (پاسپورت)", 35f, 45f), PSize("۵×۵ (۲ اینچ)", 50.8f, 50.8f), PSize("۲×۳ سانتی", 20f, 30f))

private fun mmPx(mm: Float) = (mm / 25.4f * 300f).toInt()

private fun cropRender(src: Bitmap, zoom: Float, offX: Float, offY: Float, outW: Int, outH: Int): Bitmap {
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out)
    cv.drawColor(android.graphics.Color.WHITE)
    val base = maxOf(outW.toFloat() / src.width, outH.toFloat() / src.height)
    val s = base * zoom
    val cx = outW / 2f + offX * outW; val cy = outH / 2f + offY * outH
    val m = Matrix(); m.postScale(s, s); m.postTranslate(cx - src.width * s / 2f, cy - src.height * s / 2f)
    cv.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

private fun makeSheet(photo: Bitmap, sheetW: Int, sheetH: Int): Bitmap {
    val gap = mmPx(2f); val margin = mmPx(4f)
    val cols = ((sheetW - 2 * margin + gap) / (photo.width + gap)).coerceAtLeast(1)
    val rows = ((sheetH - 2 * margin + gap) / (photo.height + gap)).coerceAtLeast(1)
    val out = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out); cv.drawColor(android.graphics.Color.WHITE)
    val totW = cols * photo.width + (cols - 1) * gap; val totH = rows * photo.height + (rows - 1) * gap
    val x0 = (sheetW - totW) / 2f; val y0 = (sheetH - totH) / 2f
    val line = Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 1f }
    for (r in 0 until rows) for (c in 0 until cols) {
        val x = x0 + c * (photo.width + gap); val y = y0 + r * (photo.height + gap)
        cv.drawBitmap(photo, x, y, null)
        cv.drawLine(x - 8, y, x - 2, y, line); cv.drawLine(x, y - 8, x, y - 2, line)
        cv.drawLine(x + photo.width + 2, y + photo.height, x + photo.width + 8, y + photo.height, line); cv.drawLine(x + photo.width, y + photo.height + 2, x + photo.width, y + photo.height + 8, line)
    }
    return out
}

@Composable
fun PassportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var sizeIdx by remember { mutableStateOf(2) }
    var zoom by remember { mutableStateOf(1f) }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    var sheet by remember { mutableStateOf(0) }       // ۰ ۱۰×۱۵، ۱ A4
    var bright by remember { mutableStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    val c = MaterialTheme.colorScheme
    val ps = pSizes[sizeIdx]
    val ratio = ps.wMm / ps.hMm

    fun clampOff(s: Bitmap) {
        val base = maxOf(1f / s.width * 1f, 1f / s.height * ratio.let { 1f })  // نسبی؛ محدودیت دقیق در رندر
        zoom = zoom.coerceIn(1f, 5f)
        val outW = 400; val outH = (400 / ratio).toInt()
        val b = maxOf(outW.toFloat() / s.width, outH.toFloat() / s.height) * zoom
        val mx = maxOf(0f, (s.width * b - outW) / 2f / outW); val my = maxOf(0f, (s.height * b - outH) / 2f / outH)
        offX = offX.coerceIn(-mx, mx); offY = offY.coerceIn(-my, my)
    }

    val preview = remember(src, zoom, offX, offY, sizeIdx, bright) { src?.let { cropRender(it, zoom, offX, offY, 420, (420 / ratio).toInt()) } }
    ToolScaffold("عکس پاسپورتی و پرسنلی", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("عکس را انتخاب کن، صورت را با دو انگشت وسط قاب ببر و بزرگ‌نمایی کن، بعد برگه‌ی چاپی (۱۰×۱۵ یا A4) با چند نسخه بساز.", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f))
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b -> src = b; zoom = 1f; offX = 0f; offY = 0f })
            ChipRow(pSizes.indices.toList(), sizeIdx, { pSizes[it].label }) { sizeIdx = it; offX = 0f; offY = 0f }
            val s0 = src
            if (s0 != null && preview != null) {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(0.7f).align(Alignment.CenterHorizontally).aspectRatio(ratio).clip(RoundedCornerShape(10.dp)).onSizeChanged { boxSize = it }
                        .pointerInput(s0, sizeIdx) {
                            detectTransformGestures { _, pan, z, _ ->
                                zoom = (zoom * z).coerceIn(1f, 5f)
                                offX += pan.x / boxSize.width; offY += pan.y / boxSize.height
                                clampOff(s0)
                            }
                        },
                ) {
                    BitmapImage(preview, Modifier.fillMaxSize(), crop = true)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height
                        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        drawOval(Color.White.copy(alpha = 0.85f), topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.5f), style = st)
                        drawArc(Color.White.copy(alpha = 0.6f), 200f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.7f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.55f), style = st)
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("بزرگ‌نمایی", fontSize = 13.sp)
                    Slider(zoom, { zoom = it; clampOff(s0) }, valueRange = 1f..5f)
                }
                SectionTitle("اندازه‌ی برگه‌ی چاپ")
                ChipRow(listOf(0, 1), sheet, { if (it == 0) "۱۰×۱۵ سانتی (چاپخانه)" else "A4" }) { sheet = it }
                val outW = mmPx(ps.wMm); val outH = mmPx(ps.hMm)
                val per = remember(sheet, sizeIdx) {
                    val sw = if (sheet == 0) 1181 else 2480; val sh = if (sheet == 0) 1772 else 3508
                    val gap = mmPx(2f); val mg = mmPx(4f)
                    ((sw - 2 * mg + gap) / (outW + gap)) * ((sh - 2 * mg + gap) / (outH + gap))
                }
                Text("روی این برگه ${per} نسخه جا می‌شود (با خط برش).", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.primary)
                GradButton("ساخت و ذخیره‌ی برگه‌ی چاپی", icon = Icons.Rounded.GridView, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = {
                    if (canExport()) scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            val one = cropRender(s0, zoom, offX, offY, outW, outH)
                            val sh = makeSheet(one, if (sheet == 0) 1181 else 2480, if (sheet == 0) 1772 else 3508)
                            saveBitmapToGallery(ctx, sh)
                        }
                        toast(ctx, if (ok) "برگه در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                    }
                })
                OutlinedButton({
                    if (canExport()) scope.launch {
                        val ok = withContext(Dispatchers.Default) { saveBitmapToGallery(ctx, cropRender(s0, zoom, offX, offY, outW, outH)) }
                        toast(ctx, if (ok) "عکس تکی ذخیره شد" else "ذخیره نشد")
                    }
                }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("فقط یک عکس (بدون برگه)") }
                OutlinedButton({
                    if (canExport()) scope.launch {
                        val sh = withContext(Dispatchers.Default) { makeSheet(cropRender(s0, zoom, offX, offY, outW, outH), if (sheet == 0) 1181 else 2480, if (sheet == 0) 1772 else 3508) }
                        shareBitmap(ctx, sh)
                    }
                }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری برگه") }
                Text("برای عکس رسمی، پس‌زمینه‌ی روشن و ساده و نور یکنواخت روی صورت داشته باش. حذف و عوض‌کردن پس‌زمینه در این بخش نیست؛ خروجی با ۳۰۰ نقطه در اینچ ساخته می‌شود.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
