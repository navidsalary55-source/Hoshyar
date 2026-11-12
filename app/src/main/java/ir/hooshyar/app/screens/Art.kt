package ir.hooshyar.app.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- افکت‌های هنری و کارتونی (کاملاً روی گوشی)

private enum class ArtKind(val label: String) {
    CARTOON("کارتونی"), COMIC("کمیک"), ANIME("انیمه‌ای"), SKETCH("طراحی مداد"), PENCIL("مداد رنگی"),
    OIL("نقاشی رنگ‌روغن"), WATER("آبرنگ"), POP("پاپ‌آرت"), NEON("نئون"), PIXEL("پیکسلی"), MOSAIC("موزاییک"), EMBOSS("برجسته"),
}

private fun lum(p: Int) = (((p shr 16) and 255) * 77 + ((p shr 8) and 255) * 151 + (p and 255) * 28) shr 8

private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int): IntArray {
    if (r <= 0) return px
    val d = 2 * r + 1
    val tmp = IntArray(w * h); val out = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) { val p = px[row + i.coerceIn(0, w - 1)]; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
        for (x in 0 until w) {
            tmp[row + x] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
            val a = px[row + (x + r + 1).coerceAtMost(w - 1)]; val b = px[row + (x - r).coerceAtLeast(0)]
            sr += ((a shr 16) and 255) - ((b shr 16) and 255)
            sg += ((a shr 8) and 255) - ((b shr 8) and 255)
            sb += (a and 255) - (b and 255)
        }
    }
    for (x in 0 until w) {
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) { val p = tmp[i.coerceIn(0, h - 1) * w + x]; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
        for (y in 0 until h) {
            out[y * w + x] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
            val a = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]; val b = tmp[(y - r).coerceAtLeast(0) * w + x]
            sr += ((a shr 16) and 255) - ((b shr 16) and 255)
            sg += ((a shr 8) and 255) - ((b shr 8) and 255)
            sb += (a and 255) - (b and 255)
        }
    }
    return out
}

private fun sobel(px: IntArray, w: Int, h: Int): IntArray {
    val g = IntArray(w * h) { lum(px[it]) }
    val e = IntArray(w * h)
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        val gx = -g[i - w - 1] - 2 * g[i - 1] - g[i + w - 1] + g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1]
        val gy = -g[i - w - 1] - 2 * g[i - w] - g[i - w + 1] + g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]
        e[i] = minOf(255, (Math.abs(gx) + Math.abs(gy)) / 2)
    }
    return e
}


/** فیلتر دوطرفه: نرم می‌کند ولی لبه‌ها را نگه می‌دارد (پوست صاف، خطوط سالم) */
private fun bilateral(px: IntArray, w: Int, h: Int, r: Int, sigmaR: Float): IntArray {
    val out = IntArray(w * h)
    val sp = FloatArray((2 * r + 1) * (2 * r + 1))
    val ss = r * 0.7f + 0.5f
    for (dy in -r..r) for (dx in -r..r) sp[(dy + r) * (2 * r + 1) + dx + r] = Math.exp((-(dx * dx + dy * dy) / (2 * ss * ss)).toDouble()).toFloat()
    val lut = FloatArray(766) { Math.exp((-(it * it) / (2 * sigmaR * sigmaR)).toDouble()).toFloat() }
    for (y in 0 until h) for (x in 0 until w) {
        val c = px[y * w + x]
        val cr = (c shr 16) and 255; val cg = (c shr 8) and 255; val cb = c and 255
        var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
        for (dy in -r..r) {
            val yy = (y + dy).coerceIn(0, h - 1)
            for (dx in -r..r) {
                val q = px[yy * w + (x + dx).coerceIn(0, w - 1)]
                val qr = (q shr 16) and 255; val qg = (q shr 8) and 255; val qb = q and 255
                val wt = sp[(dy + r) * (2 * r + 1) + dx + r] * lut[Math.abs(qr - cr) + Math.abs(qg - cg) + Math.abs(qb - cb)]
                sr += qr * wt; sg += qg * wt; sb += qb * wt; sw += wt
            }
        }
        out[y * w + x] = rgb((sr / sw).toInt(), (sg / sw).toInt(), (sb / sw).toInt())
    }
    return out
}

/** ماسک خط‌های تمیز (۰..۲۵۵)؛ خال‌های تک‌پیکسلی حذف می‌شوند. line: ۰..۱ */
private fun lineMask(smooth: IntArray, w: Int, h: Int, line: Float, boost: Float): IntArray {
    val e = sobel(smooth, w, h)
    val thr = 135f - 95f * line
    val m = IntArray(w * h)
    for (i in e.indices) {
        val v = ((e[i] * boost - thr) / 40f).coerceIn(0f, 1f)
        val g = (v * 255).toInt()
        m[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    val bl = boxBlur(m, w, h, if (line > 0.65f) 2 else 1)
    return IntArray(w * h) { (((bl[it] and 255) - 55) * 2.2f).toInt().coerceIn(0, 255) }
}

private fun post(v: Int, levels: Int): Int = (((v * levels) shr 8).coerceIn(0, levels - 1)) * 255 / (levels - 1)

private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

private fun saturate(p: Int, k: Float): Int {
    val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
    val l = lum(p)
    return rgb((l + (r - l) * k).toInt(), (l + (g - l) * k).toInt(), (l + (b - l) * k).toInt())
}

/** line: ۰..۱ ضخامت/حساسیت خطوط */
private fun artEffect(src: Bitmap, kind: ArtKind, strength: Float, line: Float): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
    val res = IntArray(w * h)
    val thr = (150 - 110 * line).toInt()
    when (kind) {
        ArtKind.CARTOON, ArtKind.COMIC, ArtKind.ANIME -> {
            // ۱) صاف‌کردن با حفظ لبه‌ها  ۲) رنگ‌های تخت با حفظ ته‌رنگ  ۳) خط‌های تمیز و بدون لکه
            val sm = bilateral(bilateral(px, w, h, 3, 38f), w, h, 3, 30f)
            val n = when (kind) { ArtKind.ANIME -> 5; ArtKind.COMIC -> 4; else -> 6 }
            val sat = when (kind) { ArtKind.COMIC -> 1.55f; ArtKind.ANIME -> 1.3f; else -> 1.4f }
            val lines = lineMask(sm, w, h, line, if (kind == ArtKind.COMIC) 1.25f else 1f)
            val ink = if (kind == ArtKind.ANIME) rgb(52, 36, 56) else rgb(18, 14, 20)
            for (i in px.indices) {
                val p0 = sm[i]
                val l = lum(p0) / 255f
                val f = l * n
                val ii = Math.floor(f.toDouble()).toInt()
                val tt = ((f - ii - 0.3f) / 0.4f).coerceIn(0f, 1f)
                var lq = (ii + tt * tt * (3 - 2 * tt)) / n
                if (kind == ArtKind.ANIME) lq = 0.12f + lq * 0.88f
                val k = ((lq * 255f + 6f) / (l * 255f + 6f)).coerceIn(0.4f, 2.4f)
                val r = ((p0 shr 16) and 255) * k; val g = ((p0 shr 8) and 255) * k; val b = (p0 and 255) * k
                var c = saturate(rgb(r.toInt(), g.toInt(), b.toInt()), sat)
                if (kind == ArtKind.COMIC && lq < 0.42f) {
                    val x = i % w; val y = i / w
                    val dx = (x % 7) - 3; val dy = (y % 7) - 3
                    if (dx * dx + dy * dy <= (0.42f - lq) * 40f) c = rgb(((c shr 16) and 255) * 6 / 10, ((c shr 8) and 255) * 6 / 10, (c and 255) * 6 / 10)
                }
                val al = lines[i] / 255f
                res[i] = if (al <= 0f) c else rgb(
                    (((c shr 16) and 255) * (1 - al) + ((ink shr 16) and 255) * al).toInt(),
                    (((c shr 8) and 255) * (1 - al) + ((ink shr 8) and 255) * al).toInt(),
                    ((c and 255) * (1 - al) + (ink and 255) * al).toInt(),
                )
            }
        }
        ArtKind.SKETCH, ArtKind.PENCIL -> {
            val gray = IntArray(w * h) { val l = lum(px[it]); rgb(l, l, l) }
            val inv = IntArray(w * h) { val l = 255 - lum(px[it]); rgb(l, l, l) }
            val bl = boxBlur(boxBlur(inv, w, h, 8), w, h, 6)
            val soft = if (kind == ArtKind.PENCIL) boxBlur(px, w, h, 2) else px
            for (i in px.indices) {
                val g = gray[i] and 255; val b = bl[i] and 255
                var d = if (b >= 255) 255 else minOf(255, g * 255 / (255 - b))
                d = (d + (255 - d) * (0.25f * (1f - line))).toInt().coerceIn(0, 255)
                if (kind == ArtKind.SKETCH) res[i] = rgb(d, d, d)
                else {
                    val c = soft[i]
                    val f = d / 255f
                    res[i] = rgb((((c shr 16) and 255) * f * 0.55f + 255 * 0.45f * f).toInt(), (((c shr 8) and 255) * f * 0.55f + 255 * 0.45f * f).toInt(), ((c and 255) * f * 0.55f + 255 * 0.45f * f).toInt())
                }
            }
        }
        ArtKind.OIL, ArtKind.WATER -> {
            val rr = if (kind == ArtKind.OIL) 4 else 3
            val sm = boxBlur(bilateral(bilateral(px, w, h, 3, 45f), w, h, 3, 40f), w, h, 1)
            val e = sobel(sm, w, h)
            val lv = if (kind == ArtKind.OIL) 9 else 14
            val rnd = java.util.Random(11)
            for (i in px.indices) {
                val p = saturate(sm[i], 1.25f)
                var r = post((p shr 16) and 255, lv); var g = post((p shr 8) and 255, lv); var b = post(p and 255, lv)
                if (kind == ArtKind.WATER) {
                    val n = rnd.nextInt(14) - 7
                    r = (r * 0.85f + 38 + n).toInt(); g = (g * 0.85f + 38 + n).toInt(); b = (b * 0.85f + 38 + n).toInt()
                    if (e[i] > 40) { r = r * 9 / 10; g = g * 9 / 10; b = b * 9 / 10 }
                } else if (e[i] > 70) { r = r * 8 / 10; g = g * 8 / 10; b = b * 8 / 10 }
                res[i] = rgb(r, g, b)
            }
        }
        ArtKind.POP -> {
            val sm = boxBlur(px, w, h, 2)
            val pal = arrayOf(intArrayOf(20, 12, 50), intArrayOf(214, 30, 96), intArrayOf(255, 196, 30), intArrayOf(250, 244, 220))
            for (i in px.indices) {
                val l = lum(sm[i]) * 4 / 256
                val c = pal[l.coerceIn(0, 3)]
                res[i] = rgb(c[0], c[1], c[2])
            }
        }
        ArtKind.NEON -> {
            val sm = boxBlur(px, w, h, 1)
            val e = sobel(sm, w, h)
            for (i in px.indices) {
                val v = minOf(255, e[i] * (2 + (line * 3).toInt()))
                val c = saturate(px[i], 3f)
                res[i] = rgb(((c shr 16) and 255) * v / 255 + 6, ((c shr 8) and 255) * v / 255 + 4, (c and 255) * v / 255 + 14)
            }
        }
        ArtKind.PIXEL, ArtKind.MOSAIC -> {
            val cell = maxOf(4, (maxOf(w, h) / (if (kind == ArtKind.PIXEL) 90 else 45)))
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    var sr = 0; var sg = 0; var sb = 0; var n = 0
                    for (yy in y until minOf(h, y + cell)) for (xx in x until minOf(w, x + cell)) { val p = px[yy * w + xx]; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255; n++ }
                    var c = rgb(post(sr / n, 8), post(sg / n, 8), post(sb / n, 8))
                    if (kind == ArtKind.MOSAIC) c = saturate(rgb(sr / n, sg / n, sb / n), 1.2f)
                    for (yy in y until minOf(h, y + cell)) for (xx in x until minOf(w, x + cell)) {
                        val border = kind == ArtKind.MOSAIC && (yy == y + cell - 1 || xx == x + cell - 1 || yy == y || xx == x)
                        res[yy * w + xx] = if (border) rgb(((c shr 16) and 255) * 6 / 10, ((c shr 8) and 255) * 6 / 10, (c and 255) * 6 / 10) else c
                    }
                    x += cell
                }
                y += cell
            }
        }
        ArtKind.EMBOSS -> {
            val s = 2
            for (y in 0 until h) for (x in 0 until w) {
                val a = lum(px[y * w + x]); val b = lum(px[minOf(h - 1, y + s) * w + minOf(w - 1, x + s)])
                val v = (128 + (a - b) * (1 + line * 2)).toInt()
                res[y * w + x] = rgb(v, v, v)
            }
        }
    }
    val out = IntArray(w * h)
    val s = strength.coerceIn(0f, 1f)
    for (i in px.indices) {
        if (s >= 0.999f) { out[i] = res[i]; continue }
        val a = px[i]; val b = res[i]
        out[i] = rgb(
            (((a shr 16) and 255) * (1 - s) + ((b shr 16) and 255) * s).toInt(),
            (((a shr 8) and 255) * (1 - s) + ((b shr 8) and 255) * s).toInt(),
            ((a and 255) * (1 - s) + (b and 255) * s).toInt(),
        )
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

private fun scaleTo(b: Bitmap, maxDim: Int): Bitmap {
    val m = maxOf(b.width, b.height)
    if (m <= maxDim) return b
    val k = maxDim.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
}

private enum class AiStyle(val key: String, val label: String) {
    FLAT("flat", "کارتون تخت (مثل نمونه)"), PIXAR("pixar", "پیکسار ۳بعدی"), ANIME("anime", "انیمه"), CARTOON("cartoon", "کارتون دوبعدی"), FIGURE("figure", "عروسک کلکسیونی"),
    CARICATURE("caricature", "کاریکاتور"), CLAY("clay", "خمیری"), COMIC("comic", "کمیک ابرقهرمانی"), WATER("watercolor", "آبرنگ"),
}

private suspend fun aiAvatar(src: Bitmap, style: AiStyle): Bitmap = withContext(Dispatchers.IO) {
    if (!Store.serverOn) throw AiException("سرویس هنوز راه‌اندازی نشده است.")
    val small = scaleTo(src, 1024)
    val b64 = android.util.Base64.encodeToString(small.toJpegBytes(88), android.util.Base64.NO_WRAP)
    val json = org.json.JSONObject().put("image", b64).put("style", style.key).toString()
    val bytes = Net.postAudio(Provider.SERVER.baseUrl + "/avatar", Store.deviceId, json, prefix = "image/")
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw AiException("تصویر خوانده نشد")
}

@Composable
fun ArtScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Bitmap?>(null) }
    var kind by remember { mutableStateOf(ArtKind.CARTOON) }
    var strength by remember { mutableStateOf(1f) }
    var line by remember { mutableStateOf(0.5f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var showOrig by remember { mutableStateOf(false) }
    var aiStyle by remember { mutableStateOf(AiStyle.FLAT) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiErr by remember { mutableStateOf("") }
    var aiImg by remember { mutableStateOf<Bitmap?>(null) }
    val base = remember(orig) { orig?.let { scaleTo(it, 800) } }

    LaunchedEffect(base, kind, strength, line) {
        val b = base ?: return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        busy = true
        result = withContext(Dispatchers.Default) { artEffect(b, kind, strength, line) }
        busy = false
    }
    val c = MaterialTheme.colorScheme
    ToolScaffold("کارتونی و هنری", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b -> orig = b; result = null; aiImg = null; aiErr = "" })
            val shown = if (showOrig) base else (aiImg ?: result ?: base)
            if (shown != null) {
                BitmapImage(shown, Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(20.dp)))
                if (busy) LinearProgressIndicator(Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                OutlinedButton({ showOrig = !showOrig }, Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Rounded.Compare, null); Spacer(Modifier.width(6.dp)); Text(if (showOrig) "نمایش نتیجه" else "نمایش عکس اصلی")
                }
                SectionTitle("سبک")
                ChipRow(ArtKind.values().toList(), kind, { it.label }) { kind = it }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("شدت افکت", fontSize = 13.sp)
                    Slider(strength, { strength = it }, valueRange = 0.2f..1f)
                    Text("خطوط و جزئیات", fontSize = 13.sp)
                    Slider(line, { line = it }, valueRange = 0f..1f)
                }
                SectionTitle("کارتونی واقعی با هوش مصنوعی (ویژه‌ی اشتراک)")
                Text(
                    "چهره‌ی خودت را حفظ می‌کند و عکس را به کارکتر پیکسار، انیمه و … تبدیل می‌کند. چند ثانیه تا یک دقیقه طول می‌کشد.",
                    Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.65f),
                )
                ChipRow(AiStyle.values().toList(), aiStyle, { it.label }) { aiStyle = it }
                GradButton(
                    if (aiImg != null) "ساخت دوباره" else "ساخت با هوش مصنوعی", icon = Icons.Rounded.AutoAwesome, loading = aiBusy,
                    colors = listOf(androidx.compose.ui.graphics.Color(0xFF8E54E9), androidx.compose.ui.graphics.Color(0xFFFF6FB1)),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        if (canExport() && !aiBusy) scope.launch {
                            aiBusy = true; aiErr = ""; showOrig = false
                            try { aiImg = aiAvatar(orig!!, aiStyle) } catch (e: AiException) { aiErr = e.message ?: "ناموفق" } catch (e: Exception) { aiErr = "ساخت ناموفق بود" }
                            aiBusy = false
                        }
                    },
                )
                ErrorText(aiErr.ifBlank { null })
                if (aiImg != null) OutlinedButton({ aiImg = null }, Modifier.padding(horizontal = 16.dp)) { Text("برگشت به افکت‌های گوشی") }
                OutlinedButton(
                    {
                        if (canExport()) scope.launch {
                            val sb = withContext(Dispatchers.Default) { aiImg ?: artEffect(scaleTo(orig!!, 1400), kind, strength, line) }
                            shareBitmap(ctx, sb)
                        }
                    },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری (واتساپ، استوری …)") }
                GradButton(
                    "ذخیره در گالری", icon = Icons.Rounded.Download,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        if (canExport()) scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                val full = aiImg ?: artEffect(scaleTo(orig!!, 1400), kind, strength, line)
                                saveBitmapToGallery(ctx, full)
                            }
                            toast(ctx, if (ok) "در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
            } else {
                Text(
                    "یک عکس انتخاب کن تا به کارتون، کمیک، انیمه، طراحی مداد، نقاشی و … تبدیلش کنم. همه‌چیز روی خود گوشی انجام می‌شود.",
                    Modifier.padding(24.dp), color = c.onBackground.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
