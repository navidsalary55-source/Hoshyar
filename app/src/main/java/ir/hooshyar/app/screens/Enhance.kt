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

// ---------------------------------------------------------------- بهبود کیفیت تصویر (کاملاً روی گوشی)

private fun enRgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
private fun enLum(p: Int) = (((p shr 16) and 255) * 77 + ((p shr 8) and 255) * 151 + (p and 255) * 28) shr 8

private class EP(
    var auto: Float = 0f,        // کشیدن خودکار سطوح (۰..۱)
    var wb: Float = 0f,          // تعادل سفیدی خودکار (۰..۱)
    var bright: Float = 0f,      // -۱..۱
    var contrast: Float = 0f,    // -۱..۱
    var shadows: Float = 0f,     // روشن‌کردن سایه‌ها ۰..۱
    var highlights: Float = 0f,  // کنترل نقاط پرنور ۰..۱
    var sat: Float = 0f,         // -۱..۱
    var vibrance: Float = 0f,    // ۰..۱
    var warmth: Float = 0f,      // -۱..۱
    var dehaze: Float = 0f,      // ۰..۱
    var clarity: Float = 0f,     // ۰..۱
    var sharp: Float = 0f,       // ۰..۱
    var denoise: Float = 0f,     // ۰..۱
) {
    fun copyOf() = EP(auto, wb, bright, contrast, shadows, highlights, sat, vibrance, warmth, dehaze, clarity, sharp, denoise)
}

private fun enBlur(px: IntArray, w: Int, h: Int, r: Int): IntArray {
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

private fun enBilateral(px: IntArray, w: Int, h: Int, r: Int, sigmaR: Float): IntArray {
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
        out[y * w + x] = enRgb((sr / sw).toInt(), (sg / sw).toInt(), (sb / sw).toInt())
    }
    return out
}

private fun enhance(src: Bitmap, p: EP): Bitmap {
    val w = src.width; val h = src.height
    var px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
    if (p.denoise > 0.02f) px = enBilateral(px, w, h, 2, 10f + 45f * p.denoise)

    // سطوح و تعادل سفیدی خودکار
    val hist = Array(3) { IntArray(256) }
    var sr = 0L; var sg = 0L; var sb = 0L
    for (c in px) {
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        hist[0][r]++; hist[1][g]++; hist[2][b]++; sr += r; sg += g; sb += b
    }
    val total = (w * h).toFloat()
    val lo = IntArray(3); val hi = IntArray(3) { 255 }
    for (ch in 0..2) {
        var acc = 0; for (v in 0..255) { acc += hist[ch][v]; if (acc >= total * 0.005f) { lo[ch] = v; break } }
        acc = 0; for (v in 255 downTo 0) { acc += hist[ch][v]; if (acc >= total * 0.005f) { hi[ch] = v; break } }
        if (hi[ch] - lo[ch] < 40) { lo[ch] = 0; hi[ch] = 255 }
    }
    val mr = sr / total; val mg = sg / total; val mb = sb / total
    val gm = (mr + mg + mb) / 3f
    val gain = floatArrayOf(
        1f + p.wb * ((gm / mr.coerceAtLeast(1f)).coerceIn(0.8f, 1.25f) - 1f),
        1f + p.wb * ((gm / mg.coerceAtLeast(1f)).coerceIn(0.8f, 1.25f) - 1f),
        1f + p.wb * ((gm / mb.coerceAtLeast(1f)).coerceIn(0.8f, 1.25f) - 1f),
    )
    val lut = Array(3) { ch -> IntArray(256) { v ->
        val stretched = ((v - lo[ch]).toFloat() / (hi[ch] - lo[ch]).coerceAtLeast(1)).coerceIn(0f, 1f) * 255f
        (v + (stretched - v) * p.auto).coerceIn(0f, 255f).toInt()
    } }

    val ex = 1f + p.bright * 0.6f
    val ct = 1f + p.contrast * 0.8f
    val sa = 1f + p.sat
    for (i in px.indices) {
        val c = px[i]
        var r = lut[0][(c shr 16) and 255] * gain[0]
        var g = lut[1][(c shr 8) and 255] * gain[1]
        var b = lut[2][c and 255] * gain[2]
        r += p.warmth * 18f; b -= p.warmth * 18f
        r = r.coerceIn(0f, 255f); g = g.coerceIn(0f, 255f); b = b.coerceIn(0f, 255f)
        val l = (r * 0.3f + g * 0.59f + b * 0.11f) / 255f
        // سایه و هایلایت
        val lift = p.shadows * (1f - l) * (1f - l) * 70f - p.highlights * l * l * 60f
        r += lift; g += lift; b += lift
        // نور و کنتراست
        r = ((r * ex - 128f) * ct + 128f); g = ((g * ex - 128f) * ct + 128f); b = ((b * ex - 128f) * ct + 128f)
        // اشباع و ویبرنس
        val l2 = r * 0.3f + g * 0.59f + b * 0.11f
        val mx = maxOf(r, maxOf(g, b)); val mn = minOf(r, minOf(g, b))
        val satNow = if (mx <= 1f) 0f else (mx - mn) / mx
        val k = sa * (1f + p.vibrance * (1f - satNow) * 0.9f)
        r = l2 + (r - l2) * k; g = l2 + (g - l2) * k; b = l2 + (b - l2) * k
        px[i] = enRgb(r.toInt(), g.toInt(), b.toInt())
    }

    // رفع مه
    if (p.dehaze > 0.02f) {
        val mins = IntArray(w * h) { val c = px[it]; val m = minOf((c shr 16) and 255, minOf((c shr 8) and 255, c and 255)); enRgb(m, m, m) }
        val dark = enBlur(enBlur(mins, w, h, maxOf(3, minOf(w, h) / 80)), w, h, 2)
        for (i in px.indices) {
            val c = px[i]; val d = (dark[i] and 255) * p.dehaze * 0.85f
            val t = (1f - d / 255f).coerceAtLeast(0.25f)
            px[i] = enRgb((((c shr 16) and 255) - d).div(t).toInt(), (((c shr 8) and 255) - d).div(t).toInt(), ((c and 255) - d).div(t).toInt())
        }
    }
    // وضوح (کنتراست موضعی)
    if (p.clarity > 0.02f) {
        val gray = IntArray(w * h) { val l = enLum(px[it]); enRgb(l, l, l) }
        val bl = enBlur(enBlur(gray, w, h, maxOf(6, maxOf(w, h) / 60)), w, h, maxOf(6, maxOf(w, h) / 60))
        for (i in px.indices) {
            val c = px[i]; val dlt = (enLum(c) - (bl[i] and 255)) * p.clarity * 0.9f
            px[i] = enRgb(((c shr 16) and 255) + dlt.toInt(), ((c shr 8) and 255) + dlt.toInt(), (c and 255) + dlt.toInt())
        }
    }
    // تیزی (Unsharp mask)
    if (p.sharp > 0.02f) {
        val bl = enBlur(px, w, h, if (maxOf(w, h) > 1600) 2 else 1)
        val amt = p.sharp * 1.6f
        for (i in px.indices) {
            val c = px[i]; val b2 = bl[i]
            px[i] = enRgb(
                ((c shr 16) and 255) + ((((c shr 16) and 255) - ((b2 shr 16) and 255)) * amt).toInt(),
                ((c shr 8) and 255) + ((((c shr 8) and 255) - ((b2 shr 8) and 255)) * amt).toInt(),
                (c and 255) + (((c and 255) - (b2 and 255)) * amt).toInt(),
            )
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun enScale(b: Bitmap, maxDim: Int): Bitmap {
    val m = maxOf(b.width, b.height)
    if (m <= maxDim) return b
    val k = maxDim.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
}

private fun autoParams(b: Bitmap): EP {
    val s = enScale(b, 300)
    val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
    var sum = 0.0; var sr = 0.0; var sg = 0.0; var sb = 0.0
    for (c in px) { sum += enLum(c); sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255 }
    val n = px.size
    val mean = (sum / n / 255.0).toFloat()
    val spread = maxOf(sr, maxOf(sg, sb)) / minOf(sr, minOf(sg, sb)).coerceAtLeast(1.0)
    return EP(
        auto = 0.8f, wb = if (spread > 1.12) 0.8f else 0.3f,
        bright = ((0.46f - mean) * 0.9f).coerceIn(-0.3f, 0.5f),
        contrast = 0.12f, shadows = if (mean < 0.4f) 0.55f else 0.25f, highlights = 0.2f,
        sat = 0.1f, vibrance = 0.35f, clarity = 0.25f, sharp = 0.4f, denoise = if (mean < 0.3f) 0.35f else 0.12f,
    )
}

private suspend fun aiEnhance(src: Bitmap): Bitmap = withContext(Dispatchers.IO) {
    if (!Store.serverOn) throw AiException("سرویس هنوز راه‌اندازی نشده است.")
    val small = enScale(src, 1280)
    val b64 = android.util.Base64.encodeToString(small.toJpegBytes(90), android.util.Base64.NO_WRAP)
    val json = org.json.JSONObject().put("image", b64).toString()
    val bytes = Net.postAudio(Provider.SERVER.baseUrl + "/enhance", Store.deviceId, json, prefix = "image/")
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw AiException("تصویر خوانده نشد")
}

private enum class EPreset(val label: String) { AUTO("خودکار ✨"), CRISP("شفاف و تیز"), VIVID("رنگ‌های زنده"), PORTRAIT("پرتره نرم"), NIGHT("عکس شب"), HAZE("حذف مه"), ORIGINAL("اصلی") }

@Composable
fun EnhanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Bitmap?>(null) }
    var params by remember { mutableStateOf(EP()) }
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var showOrig by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(EPreset.ORIGINAL) }
    var upscale by remember { mutableStateOf(1) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiErr by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf<Bitmap?>(null) }
    val base = remember(orig) { orig?.let { enScale(it, 800) } }

    fun edit(f: EP.() -> Unit) { val n = params.copyOf(); n.f(); params = n; tick++ }
    fun applyPreset(pr: EPreset) {
        val b = orig ?: return
        preset = pr
        params = when (pr) {
            EPreset.AUTO -> autoParams(b)
            EPreset.CRISP -> EP(auto = 0.5f, contrast = 0.15f, clarity = 0.5f, sharp = 0.65f, vibrance = 0.2f)
            EPreset.VIVID -> EP(auto = 0.5f, contrast = 0.12f, sat = 0.25f, vibrance = 0.6f, clarity = 0.2f, sharp = 0.3f)
            EPreset.PORTRAIT -> EP(auto = 0.3f, wb = 0.4f, bright = 0.06f, shadows = 0.25f, highlights = 0.25f, denoise = 0.6f, clarity = 0.1f, sharp = 0.2f, warmth = 0.1f)
            EPreset.NIGHT -> EP(auto = 0.7f, wb = 0.5f, bright = 0.3f, shadows = 0.8f, highlights = 0.3f, denoise = 0.7f, vibrance = 0.3f, clarity = 0.25f, sharp = 0.35f)
            EPreset.HAZE -> EP(auto = 0.6f, dehaze = 0.7f, contrast = 0.15f, vibrance = 0.4f, clarity = 0.35f, sharp = 0.35f)
            EPreset.ORIGINAL -> EP()
        }
        tick++
    }

    LaunchedEffect(base, tick) {
        val b = base ?: return@LaunchedEffect
        kotlinx.coroutines.delay(180)
        busy = true
        val p = params.copyOf()
        result = withContext(Dispatchers.Default) { enhance(b, p) }
        busy = false
    }
    val c = MaterialTheme.colorScheme
    @Composable fun Sl(label: String, v: Float, range: ClosedFloatingPointRange<Float>, on: (Float) -> Unit) {
        Text(label, fontSize = 13.sp); Slider(v, { on(it) }, valueRange = range)
    }

    ToolScaffold("کیفیت تصویر", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b -> orig = b; backup = null; aiErr = ""; params = autoParams(b); preset = EPreset.AUTO; result = null; tick++ })
            val shown = if (showOrig) base else (result ?: base)
            if (shown != null) {
                BitmapImage(shown, Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(20.dp)))
                if (busy) LinearProgressIndicator(Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                OutlinedButton({ showOrig = !showOrig }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Icon(Icons.Rounded.Compare, null); Spacer(Modifier.width(6.dp)); Text(if (showOrig) "نمایش نتیجه‌ی بهبودیافته" else "مقایسه با عکس اصلی")
                }
                SectionTitle("حالت‌های آماده")
                ChipRow(EPreset.values().toList(), preset, { it.label }) { applyPreset(it) }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionTitle("نور")
                    Sl("روشنایی", params.bright, -1f..1f) { v -> edit { bright = v } }
                    Sl("کنتراست", params.contrast, -1f..1f) { v -> edit { contrast = v } }
                    Sl("روشن‌کردن سایه‌ها", params.shadows, 0f..1f) { v -> edit { shadows = v } }
                    Sl("کنترل نقاط پرنور", params.highlights, 0f..1f) { v -> edit { highlights = v } }
                    Sl("سطوح خودکار", params.auto, 0f..1f) { v -> edit { auto = v } }
                    SectionTitle("رنگ")
                    Sl("اشباع رنگ", params.sat, -1f..1f) { v -> edit { sat = v } }
                    Sl("رنگ‌های زنده (ویبرنس)", params.vibrance, 0f..1f) { v -> edit { vibrance = v } }
                    Sl("گرمی رنگ (سرد ← گرم)", params.warmth, -1f..1f) { v -> edit { warmth = v } }
                    Sl("تعادل سفیدی خودکار", params.wb, 0f..1f) { v -> edit { wb = v } }
                    SectionTitle("جزئیات")
                    Sl("تیزی", params.sharp, 0f..1f) { v -> edit { sharp = v } }
                    Sl("وضوح (جزئیات بافت)", params.clarity, 0f..1f) { v -> edit { clarity = v } }
                    Sl("کاهش نویز", params.denoise, 0f..1f) { v -> edit { denoise = v } }
                    Sl("حذف مه و غبار", params.dehaze, 0f..1f) { v -> edit { dehaze = v } }
                }
                SectionTitle("اندازه‌ی خروجی")
                ChipRow(listOf(1, 2), upscale, { if (it == 1) "همان اندازه" else "۲ برابر (بزرگ‌نمایی)" }) { upscale = it }
                OutlinedButton(
                    {
                        if (canExport()) scope.launch {
                            val sb = withContext(Dispatchers.Default) { enhance(enScale(orig!!, 2000), params.copyOf()) }
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
                            busy = true
                            val ok = withContext(Dispatchers.Default) {
                                var full = enScale(orig!!, 2400)
                                if (upscale == 2) {
                                    val m = maxOf(full.width, full.height)
                                    val k = minOf(2f, 3600f / m)
                                    if (k > 1.05f) full = Bitmap.createScaledBitmap(full, (full.width * k).toInt(), (full.height * k).toInt(), true)
                                }
                                saveBitmapToGallery(ctx, enhance(full, params.copyOf()))
                            }
                            busy = false
                            toast(ctx, if (ok) "در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
                Text("همه‌ی پردازش‌ها روی خود گوشی انجام می‌شود. بزرگ‌نمایی ۲ برابر، اندازه‌ی عکس را زیاد می‌کند و تیزی را حفظ می‌کند؛ ولی جزئیات تازه نمی‌سازد.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f))
            } else {
                Text(
                    "یک عکس انتخاب کن؛ کیفیتش خودکار بهتر می‌شود: نور، رنگ، تیزی، نویز و مه. بعد هر چیز را دستی تنظیم کن.",
                    Modifier.padding(24.dp), color = c.onBackground.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
