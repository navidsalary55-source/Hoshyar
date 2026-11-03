package ir.hooshyar.app.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- اسکن به PDF

private fun scDoc(b: Bitmap): Bitmap {
    val w = b.width; val h = b.height
    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
    val lum = IntArray(w * h) { val c = px[it]; (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 151 + (c and 255) * 28) shr 8 }
    val iw = w + 1
    val ii = LongArray(iw * (h + 1))
    for (y in 0 until h) { var row = 0L; for (x in 0 until w) { row += lum[y * w + x]; ii[(y + 1) * iw + x + 1] = ii[y * iw + x + 1] + row } }
    val r = maxOf(8, maxOf(w, h) / 28)
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val y0 = maxOf(0, y - r); val y1 = minOf(h - 1, y + r)
        for (x in 0 until w) {
            val x0 = maxOf(0, x - r); val x1 = minOf(w - 1, x + r)
            val area = (x1 - x0 + 1) * (y1 - y0 + 1)
            val s = ii[(y1 + 1) * iw + x1 + 1] - ii[y0 * iw + x1 + 1] - ii[(y1 + 1) * iw + x0] + ii[y0 * iw + x0]
            val mean = s.toFloat() / area
            val ratio = lum[y * w + x] / mean.coerceAtLeast(1f)
            val v = (((ratio - 0.72f) / 0.22f).coerceIn(0f, 1f) * 255f).toInt()
            out[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

private fun scBright(b: Bitmap): Bitmap {
    val w = b.width; val h = b.height
    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val c = px[i]
        val r = ((((c shr 16) and 255) - 25) * 1.28f).toInt().coerceIn(0, 255)
        val g = ((((c shr 8) and 255) - 25) * 1.28f).toInt().coerceIn(0, 255)
        val bl = (((c and 255) - 25) * 1.28f).toInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun scApply(b: Bitmap, filter: Int): Bitmap = when (filter) { 1 -> scDoc(b); 2 -> scBright(b); else -> b }

private fun scRotate(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f) }, true)

private fun scMakePdf(ctx: android.content.Context, pages: List<Bitmap>, filter: Int): File {
    val pdf = PdfDocument()
    val pw = 595; val ph = 842; val m = 20
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    for ((i, src) in pages.withIndex()) {
        val b = scApply(src, filter)
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
        val s = minOf((pw - 2f * m) / b.width, (ph - 2f * m) / b.height)
        val dw = b.width * s; val dh = b.height * s
        page.canvas.drawColor(android.graphics.Color.WHITE)
        page.canvas.drawBitmap(b, null, android.graphics.RectF((pw - dw) / 2, (ph - dh) / 2, (pw + dw) / 2, (ph + dh) / 2), paint)
        pdf.finishPage(page)
    }
    val f = File(ctx.cacheDir, "AIHub_scan_${System.currentTimeMillis()}.pdf")
    f.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    return f
}

@Composable
fun ScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<Bitmap>() }
    var filter by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var lastPdf by remember { mutableStateOf<File?>(null) }
    val c = MaterialTheme.colorScheme
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            val list = withContext(Dispatchers.IO) { uris.mapNotNull { decodeBitmap(ctx, it, 2000) } }
            pages.addAll(list); lastPdf = null; busy = false
        }
    }
    ToolScaffold("اسکن به PDF", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("برگه‌ها را با دوربین بگیر یا از گالری انتخاب کن؛ سند تمیز و سفید می‌شود و به PDF تبدیل می‌شود.", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f))
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b -> pages.add(fitDownSc(b, 2000)); lastPdf = null })
            OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("افزودن چند عکس از گالری")
            }
            if (pages.isNotEmpty()) {
                SectionTitle("حالت اسکن")
                ChipRow(listOf(1, 2, 0), filter, { listOf("اصلی", "سند سیاه‌وسفید", "رنگی روشن")[it] }) { filter = it; lastPdf = null }
                SectionTitle("برگه‌ها (${pages.size})")
                pages.forEachIndexed { i, b ->
                    val thumb = remember(b, filter) { scApply(fitDownSc(b, 320), filter) }
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BitmapImage(thumb, Modifier.width(90.dp).height(120.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text("برگه‌ی ${i + 1}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton({ pages[i] = scRotate(b); lastPdf = null }) { Icon(Icons.Rounded.RotateRight, "چرخش") }
                                IconButton({ if (i > 0) { val t = pages[i - 1]; pages[i - 1] = b; pages[i] = t; lastPdf = null } }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "بالا") }
                                IconButton({ if (i < pages.lastIndex) { val t = pages[i + 1]; pages[i + 1] = b; pages[i] = t; lastPdf = null } }, enabled = i < pages.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "پایین") }
                                IconButton({ pages.removeAt(i); lastPdf = null }) { Icon(Icons.Rounded.Delete, "حذف") }
                            }
                        }
                    }
                }
                GradButton(
                    if (busy) "در حال ساخت PDF…" else "ساخت و ذخیره‌ی PDF", icon = Icons.Rounded.PictureAsPdf, loading = busy, enabled = !busy,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        if (canExport()) scope.launch {
                            busy = true
                            val f = withContext(Dispatchers.Default) { try { scMakePdf(ctx, pages.toList(), filter) } catch (e: Throwable) { null } }
                            busy = false
                            if (f != null) {
                                lastPdf = f
                                val ok = withContext(Dispatchers.IO) { saveFileToGallery(ctx, f, f.name, "application/pdf") }
                                toast(ctx, if (ok) "PDF در پوشه‌ی Download/AIHub ذخیره شد" else "ساخته شد ولی ذخیره نشد")
                            } else toast(ctx, "ساخت PDF ناموفق بود")
                        }
                    },
                )
                lastPdf?.let { f -> OutlinedButton({ shareFile(ctx, f, "application/pdf") }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری PDF") } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun fitDownSc(b: Bitmap, maxDim: Int): Bitmap {
    val m = maxOf(b.width, b.height)
    if (m <= maxDim) return b
    val k = maxDim.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
}

// ---------------------------------------------------------------- فشرده‌ساز و تغییر اندازه‌ی عکس

private fun fmtSize(b: Long): String = if (b >= 1_000_000) "%.1f مگابایت".format(b / 1_000_000.0) else "${b / 1000} کیلوبایت"

@Composable
fun ShrinkScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uris = remember { mutableStateListOf<Uri>() }
    var maxSide by remember { mutableStateOf(1600) }
    var quality by remember { mutableStateOf(80f) }
    var fmt by remember { mutableStateOf(0) }           // ۰ JPG، ۱ WEBP، ۲ PNG
    var busy by remember { mutableStateOf(false) }
    var origTotal by remember { mutableStateOf(0L) }
    var estimate by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    val c = MaterialTheme.colorScheme

    fun compress(b: Bitmap): ByteArray {
        val bo = java.io.ByteArrayOutputStream()
        val f = when (fmt) {
            1 -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            2 -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }
        b.compress(f, quality.toInt(), bo)
        return bo.toByteArray()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { l ->
        if (l.isNotEmpty()) scope.launch {
            uris.clear(); uris.addAll(l); summary = ""
            origTotal = withContext(Dispatchers.IO) { l.sumOf { try { ctx.contentResolver.openAssetFileDescriptor(it, "r")?.use { d -> d.length } ?: 0L } catch (e: Exception) { 0L } } }
        }
    }
    LaunchedEffect(uris.size, maxSide, quality, fmt) {
        val u = uris.firstOrNull() ?: return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        estimate = withContext(Dispatchers.Default) {
            val b = decodeBitmap(ctx, u, maxSide) ?: return@withContext ""
            val sz = compress(b).size.toLong()
            "حدود ${fmtSize(sz)} برای عکس اول (${b.width}×${b.height})"
        }
    }
    ToolScaffold("فشرده‌ساز عکس", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("حجم عکس‌ها را کم کن یا اندازه‌شان را تغییر بده تا ارسال و بارگذاری سریع‌تر شود. چند عکس را هم‌زمان می‌شود.", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f))
            OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(if (uris.isEmpty()) "انتخاب عکس‌ها" else "${uris.size} عکس انتخاب شد (تغییر)")
            }
            if (uris.isNotEmpty()) {
                if (origTotal > 0) Text("حجم کل عکس‌های اصلی: ${fmtSize(origTotal)}", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                SectionTitle("اندازه (بزرگ‌ترین ضلع)")
                ChipRow(listOf(720, 1080, 1600, 2400, 4096), maxSide, { if (it == 4096) "اصلی" else "$it" }) { maxSide = it }
                SectionTitle("فرمت")
                ChipRow(listOf(0, 1, 2), fmt, { listOf("JPG", "WEBP (کم‌حجم‌تر)", "PNG (بدون افت)")[it] }) { fmt = it }
                if (fmt != 2) Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("کیفیت: ${quality.toInt()}٪", fontSize = 13.sp)
                    Slider(quality, { quality = it }, valueRange = 30f..95f)
                }
                if (estimate.isNotBlank()) Text(estimate, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.primary)
                GradButton(
                    if (busy) "در حال فشرده‌سازی…" else "فشرده کن و در گالری ذخیره کن", icon = Icons.Rounded.Compress, loading = busy, enabled = !busy,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        if (canExport()) scope.launch {
                            busy = true
                            var total = 0L; var okN = 0
                            withContext(Dispatchers.Default) {
                                for (u in uris.toList()) {
                                    try {
                                        val b = decodeBitmap(ctx, u, maxSide) ?: continue
                                        val bytes = compress(b)
                                        val ext = when (fmt) { 1 -> "webp"; 2 -> "png"; else -> "jpg" }
                                        val mime = when (fmt) { 1 -> "image/webp"; 2 -> "image/png"; else -> "image/jpeg" }
                                        if (saveBytesToGallery(ctx, bytes, "AIHub_small_${System.nanoTime()}.$ext", mime)) { total += bytes.size; okN++ }
                                    } catch (e: Throwable) { }
                                }
                            }
                            busy = false
                            summary = "$okN عکس ذخیره شد؛ حجم کل: ${fmtSize(total)}" + (if (origTotal > 0) " (از ${fmtSize(origTotal)})" else "")
                            toast(ctx, if (okN > 0) "در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
                if (summary.isNotBlank()) Text(summary, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.primary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
