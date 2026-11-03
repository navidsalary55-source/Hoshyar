package ir.hooshyar.app.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
import java.io.File

// ---------------------------------------------------------------- حذف اشیاء (کاملاً روی گوشی)

/** پرکردن ناحیه با تکه‌های مشابهِ اطراف (روش نمونه‌محور). خروجی برای ویدیو دوباره قابل استفاده است. */
private class InpRes(val order: IntArray, val src: IntArray)

private fun inpaintSolve(px: IntArray, w: Int, h: Int, mask: BooleanArray, cands48: Int = 48, R: Int = 3, onProg: (Int) -> Unit): InpRes {
    val n = w * h
    val known = BooleanArray(n) { !mask[it] }
    val cur = px.copyOf()
    val src = IntArray(n) { -1 }
    var x0 = w; var y0 = h; var x1 = -1; var y1 = -1; var remaining = 0
    for (i in 0 until n) if (mask[i]) { val x = i % w; val y = i / w; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; remaining++ }
    if (remaining == 0) return InpRes(IntArray(0), src)
    val total = remaining
    // جمع تجمعی ماسک برای پیدا کردن تکه‌های سالم
    val ps = IntArray((w + 1) * (h + 1))
    for (y in 0 until h) { var row = 0; for (x in 0 until w) { row += if (mask[y * w + x]) 1 else 0; ps[(y + 1) * (w + 1) + x + 1] = ps[y * (w + 1) + x + 1] + row } }
    fun cnt(ax: Int, ay: Int, bx: Int, by: Int) = ps[(by + 1) * (w + 1) + bx + 1] - ps[ay * (w + 1) + bx + 1] - ps[(by + 1) * (w + 1) + ax] + ps[ay * (w + 1) + ax]
    fun valid(cx: Int, cy: Int) = cx >= R && cy >= R && cx < w - R && cy < h - R && cnt(cx - R, cy - R, cx + R, cy + R) == 0
    val pad = maxOf(x1 - x0, y1 - y0) + 60
    val wx0 = maxOf(R, x0 - pad); val wy0 = maxOf(R, y0 - pad); val wx1 = minOf(w - R - 1, x1 + pad); val wy1 = minOf(h - R - 1, y1 + pad)
    val area = (wx1 - wx0 + 1).toLong() * (wy1 - wy0 + 1)
    val stride = maxOf(1, Math.sqrt(area / 30000.0).toInt())
    val cl = ArrayList<Int>()
    var yy = wy0
    while (yy <= wy1) { var xx = wx0; while (xx <= wx1) { if (valid(xx, yy)) cl.add(yy * w + xx); xx += stride }; yy += stride }
    val cands = cl.toIntArray()
    val rnd = java.util.Random(5)
    val order = IntArray(total); var oc = 0
    val kdx = IntArray((2 * R + 1) * (2 * R + 1)); val kdy = IntArray(kdx.size)
    val nbx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1); val nby = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
    var lastP = -1
    while (remaining > 0) {
        // حلقه‌ی مرزی: ناشناخته‌هایی که همسایه‌ی شناخته دارند
        val ring = ArrayList<Long>()
        for (y in maxOf(0, y0 - 1)..minOf(h - 1, y1 + 1)) for (x in maxOf(0, x0 - 1)..minOf(w - 1, x1 + 1)) {
            val i = y * w + x
            if (known[i]) continue
            var k = 0
            for (d in 0 until 8) { val xx = x + nbx[d]; val yy2 = y + nby[d]; if (xx in 0 until w && yy2 in 0 until h && known[yy2 * w + xx]) k++ }
            if (k > 0) ring.add(((8 - k).toLong() shl 32) or i.toLong())
        }
        if (ring.isEmpty()) break
        ring.sort()
        for (e in ring) {
            val p = (e and 0xFFFFFFFFL).toInt()
            val px0 = p % w; val py0 = p / w
            var kn = 0
            for (dy in -R..R) for (dx in -R..R) {
                val x = px0 + dx; val y = py0 + dy
                if (x in 0 until w && y in 0 until h && known[y * w + x]) { kdx[kn] = dx; kdy[kn] = dy; kn++ }
            }
            var best = -1; var bestD = Long.MAX_VALUE
            fun tryC(c: Int) {
                var s = 0L
                for (j in 0 until kn) {
                    val a = cur[(py0 + kdy[j]) * w + px0 + kdx[j]]; val b = px[c + kdy[j] * w + kdx[j]]
                    val dr = ((a shr 16) and 255) - ((b shr 16) and 255); val dg = ((a shr 8) and 255) - ((b shr 8) and 255); val db = (a and 255) - (b and 255)
                    s += dr * dr + dg * dg + db * db
                    if (s >= bestD) return
                }
                best = c; bestD = s
            }
            for (d in 0 until 8) {
                val qx = px0 + nbx[d]; val qy = py0 + nby[d]
                if (qx !in 0 until w || qy !in 0 until h) continue
                val s = src[qy * w + qx]
                if (s < 0) continue
                val cx = s % w - nbx[d]; val cy = s / w - nby[d]
                if (cx in 0 until w && cy in 0 until h && valid(cx, cy)) tryC(cy * w + cx)
            }
            if (cands.isNotEmpty()) repeat(cands48) { tryC(cands[rnd.nextInt(cands.size)]) }
            if (best >= 0) { src[p] = best; cur[p] = px[best] }
            else {
                // پشتیبان: میانگین همسایه‌های شناخته
                var sr = 0; var sg = 0; var sb = 0; var k = 0; var nearest = -1
                for (d in 0 until 8) { val xx = px0 + nbx[d]; val yy2 = py0 + nby[d]; if (xx in 0 until w && yy2 in 0 until h && known[yy2 * w + xx]) { val c = cur[yy2 * w + xx]; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255; k++; nearest = yy2 * w + xx } }
                if (k > 0) { cur[p] = (0xFF shl 24) or ((sr / k) shl 16) or ((sg / k) shl 8) or (sb / k); src[p] = nearest }
            }
            known[p] = true
            if (oc < total) order[oc++] = p
            remaining--
            lastP = p
        }
        onProg(((total - remaining) * 100L / total).toInt())
    }
    return InpRes(order.copyOf(oc), src)
}

private fun inpaintApply(frame: IntArray, r: InpRes) {
    for (i in r.order) { val s = r.src[i]; if (s >= 0) frame[i] = frame[s] }
}

// ---------------------------------------------------------------- ماسک

private class MStroke(val kind: Int, val size: Float) { val pts = mutableStateListOf<androidx.compose.ui.geometry.Offset>() } // kind: ۰ قلم، ۱ مستطیل، ۲ پاک‌کن

private fun renderMask(strokes: List<MStroke>, w: Int, h: Int, extra: Float): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(bmp)
    for (s in strokes) {
        if (s.pts.isEmpty()) continue
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFF2D55.toInt(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            strokeWidth = s.size * w + extra
            if (s.kind == 2) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        if (s.kind == 1) {
            pt.style = Paint.Style.FILL
            val a = s.pts.first(); val b = s.pts.last()
            cv.drawRect(minOf(a.x, b.x) * w - extra / 2, minOf(a.y, b.y) * h - extra / 2, maxOf(a.x, b.x) * w + extra / 2, maxOf(a.y, b.y) * h + extra / 2, pt)
        } else if (s.pts.size == 1) {
            pt.style = Paint.Style.FILL
            cv.drawCircle(s.pts[0].x * w, s.pts[0].y * h, (s.size * w + extra) / 2f, pt)
        } else {
            pt.style = Paint.Style.STROKE
            val path = Path()
            path.moveTo(s.pts[0].x * w, s.pts[0].y * h)
            for (i in 1 until s.pts.size) path.lineTo(s.pts[i].x * w, s.pts[i].y * h)
            cv.drawPath(path, pt)
        }
    }
    return bmp
}

private fun maskOf(bmp: Bitmap): BooleanArray {
    val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    return BooleanArray(px.size) { (px[it] ushr 24) > 40 }
}

private fun fitDown(b: Bitmap, maxDim: Int): Bitmap {
    val m = maxOf(b.width, b.height)
    if (m <= maxDim) return b
    val k = maxDim.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
}

private fun removeFromPhoto(full: Bitmap, strokes: List<MStroke>, onProg: (Int) -> Unit): Bitmap {
    val work = fitDown(full, 1400)
    val w = work.width; val h = work.height
    val m = maskOf(renderMask(strokes, w, h, w * 0.008f + 2f))
    val px = IntArray(w * h); work.getPixels(px, 0, w, 0, 0, w, h)
    val res = inpaintSolve(px, w, h, m, onProg = onProg)
    inpaintApply(px, res)
    val filled = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    if (work.width == full.width && work.height == full.height) return filled
    // ترکیب ناحیه‌ی پرشده روی عکس با اندازه‌ی اصلی
    val fw = full.width; val fh = full.height
    val up = Bitmap.createScaledBitmap(filled, fw, fh, true)
    val fm = maskOf(renderMask(strokes, fw, fh, fw * 0.008f + 2f))
    val fp = IntArray(fw * fh); full.getPixels(fp, 0, fw, 0, 0, fw, fh)
    val upp = IntArray(fw * fh); up.getPixels(upp, 0, fw, 0, 0, fw, fh)
    for (i in fp.indices) if (fm[i]) fp[i] = upp[i]
    return Bitmap.createBitmap(fp, fw, fh, Bitmap.Config.ARGB_8888)
}

private class VInfo(val uri: Uri, val durMs: Long, val w: Int, val h: Int, val d480: Pair<Int, Int> = 2 to 2)

private fun videoDims(inf: MediaTrim.Info, shortSide: Int = 720): Pair<Int, Int> {
    val swap = inf.rot == 90 || inf.rot == 270
    val dw = if (swap) inf.h else inf.w; val dh = if (swap) inf.w else inf.h
    val sc = minOf(1f, shortSide.toFloat() / minOf(dw, dh))
    return (((dw * sc).toInt() / 2 * 2).coerceAtLeast(2)) to (((dh * sc).toInt() / 2 * 2).coerceAtLeast(2))
}

private fun frameAt(r: MediaMetadataRetriever, tUs: Long, w: Int, h: Int): Bitmap? = try {
    r.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST, w, h)?.let { if (it.width == w && it.height == h) it.copy(Bitmap.Config.ARGB_8888, true) else Bitmap.createScaledBitmap(it, w, h, true) }
} catch (e: Exception) { null }

// ---------------------------------------------------------------- دنبال‌کردن شیء متحرک

private fun toGray(b: Bitmap): ByteArray {
    val w = b.width; val h = b.height
    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
    return ByteArray(w * h) { val c = px[it]; ((((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 151 + (c and 255) * 28) shr 8).toByte() }
}

/** جابه‌جایی (dx, dy) ناحیه‌ی box نسبت به فریم مرجع، برای هر فریم (روی تصویر خاکستری gw×gh) */
private fun trackBox(grays: Array<ByteArray?>, gw: Int, gh: Int, ref: Int, box: IntArray, lo: Int, hi: Int): Pair<FloatArray, FloatArray> {
    val n = grays.size
    val dxs = FloatArray(n); val dys = FloatArray(n)
    val bx = box[0]; val by = box[1]; val tw = box[2] - box[0] + 1; val th = box[3] - box[1] + 1
    val g0 = grays[ref] ?: return dxs to dys
    val S = maxOf(4, gw / 22)
    fun run(from: Int, to: Int, step: Int) {
        val tmpl = FloatArray(tw * th) { (g0[(by + it / tw) * gw + bx + it % tw].toInt() and 255).toFloat() }
        var ox = 0; var oy = 0
        var i = from
        while (i in lo..hi && i != to + step) {
            val g = grays[i]
            if (g == null) { dxs[i] = ox.toFloat(); dys[i] = oy.toFloat(); i += step; continue }
            var best = Long.MAX_VALUE; var bsx = 0; var bsy = 0
            val sad = HashMap<Int, Long>()
            for (sy in -S..S) for (sx in -S..S) {
                val px0 = bx + ox + sx; val py0 = by + oy + sy
                if (px0 < 0 || py0 < 0 || px0 + tw > gw || py0 + th > gh) continue
                var s = 0L
                var y = 0
                while (y < th) {
                    var x = 0
                    val row = (py0 + y) * gw + px0
                    while (x < tw) { s += Math.abs((g[row + x].toInt() and 255) - tmpl[y * tw + x].toInt()); x += 2 }
                    if (s >= best) break
                    y += 2
                }
                sad[(sy + S) * (2 * S + 1) + sx + S] = s
                if (s < best) { best = s; bsx = sx; bsy = sy }
            }
            // دقت زیرپیکسل با سهمی
            fun sub(a: Long?, b: Long, c: Long?): Float {
                if (a == null || c == null) return 0f
                val d = (a - 2 * b + c).toFloat()
                return if (d <= 0f) 0f else (0.5f * (a - c) / d).coerceIn(-0.5f, 0.5f)
            }
            val w2 = 2 * S + 1
            val fx = sub(sad[(bsy + S) * w2 + bsx + S - 1], best, sad[(bsy + S) * w2 + bsx + S + 1])
            val fy = sub(sad[(bsy + S - 1) * w2 + bsx + S], best, sad[(bsy + S + 1) * w2 + bsx + S])
            ox += bsx; oy += bsy
            dxs[i] = ox + fx; dys[i] = oy + fy
            // به‌روزرسانی آرام الگو تا با تغییر نور و زاویه هماهنگ بماند
            val px1 = bx + ox; val py1 = by + oy
            if (px1 >= 0 && py1 >= 0 && px1 + tw <= gw && py1 + th <= gh) {
                for (y in 0 until th) for (x in 0 until tw) {
                    val v = (g[(py1 + y) * gw + px1 + x].toInt() and 255).toFloat()
                    tmpl[y * tw + x] = tmpl[y * tw + x] * 0.92f + v * 0.08f
                }
            }
            i += step
        }
    }
    run(ref + 1, hi, 1)
    run(ref - 1, lo, -1)
    return dxs to dys
}

private fun removeTracked(ctx: android.content.Context, v: VInfo, strokes: List<MStroke>, fromSec: Float, toSec: Float, refSec: Float, onProg: (Int) -> Unit): File? {
    val r = MediaMetadataRetriever()
    val tmp = File(ctx.cacheDir, "rm_v_${System.nanoTime()}.mp4")
    val out = File(ctx.cacheDir, "rm_out_${System.currentTimeMillis()}.mp4")
    try {
        r.setDataSource(ctx, v.uri)
        val (w, h) = v.d480
        val fps = 15
        val totalMs = minOf(v.durMs, 25_000L)
        val n = (totalMs * fps / 1000).toInt().coerceAtLeast(2)
        val lo = (fromSec * fps).toInt().coerceIn(0, n - 1)
        val hi = (toSec * fps).toInt().coerceIn(lo, n - 1)
        val ref = (refSec * fps).toInt().coerceIn(lo, hi)
        val gw = 256; val gh = (256f * h / w).toInt().coerceAtLeast(16)
        val grays = arrayOfNulls<ByteArray>(n)
        for (i in lo..hi) {
            val fr = try { r.getScaledFrameAtTime(i * 1_000_000L / fps, MediaMetadataRetriever.OPTION_CLOSEST, gw, gh) } catch (e: Exception) { null }
            if (fr != null) grays[i] = toGray(if (fr.width == gw && fr.height == gh) fr else Bitmap.createScaledBitmap(fr, gw, gh, true))
            onProg((i - lo) * 15 / (hi - lo + 1))
        }
        val maskBmp = renderMask(strokes, w, h, w * 0.008f + 2f)
        val mask0 = maskOf(maskBmp)
        var mx0 = w; var my0 = h; var mx1 = -1; var my1 = -1
        val idxs = ArrayList<Int>()
        for (i in mask0.indices) if (mask0[i]) { idxs.add(i); val x = i % w; val y = i / w; if (x < mx0) mx0 = x; if (x > mx1) mx1 = x; if (y < my0) my0 = y; if (y > my1) my1 = y }
        if (idxs.isEmpty()) return null
        val sx = gw.toFloat() / w; val sy = gh.toFloat() / h
        val padX = maxOf(10f, (mx1 - mx0) * 0.35f); val padY = maxOf(10f, (my1 - my0) * 0.35f)
        val box = intArrayOf(
            ((mx0 - padX) * sx).toInt().coerceIn(0, gw - 2), ((my0 - padY) * sy).toInt().coerceIn(0, gh - 2),
            ((mx1 + padX) * sx).toInt().coerceIn(1, gw - 1), ((my1 + padY) * sy).toInt().coerceIn(1, gh - 1),
        )
        if (box[2] - box[0] < 8 || box[3] - box[1] < 8) return null
        val (tdx, tdy) = trackBox(grays, gw, gh, ref, box, lo, hi)
        val ids = idxs.toIntArray()
        StoryVideo.bitrate = (w.toLong() * h * 5).toInt().coerceIn(1_200_000, 6_000_000)
        var last: Bitmap? = null
        val ok = try {
            StoryVideo.encodeVideo(tmp, w, h, fps, n, { onProg(15 + it * 75 / 100) }) { i ->
                val fr0 = try { r.getScaledFrameAtTime(i * 1_000_000L / fps, MediaMetadataRetriever.OPTION_CLOSEST, w, h) } catch (e: Exception) { null }
                val fr = fr0?.let { if (it.width == w && it.height == h) it.copy(Bitmap.Config.ARGB_8888, true) else Bitmap.createScaledBitmap(it, w, h, true) }
                    ?: last?.copy(Bitmap.Config.ARGB_8888, true) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val b = if (i in lo..hi) {
                    val ddx = Math.round(tdx[i] / sx); val ddy = Math.round(tdy[i] / sy)
                    val m = BooleanArray(w * h)
                    for (id in ids) { val x = id % w + ddx; val y = id / w + ddy; if (x in 0 until w && y in 0 until h) m[y * w + x] = true }
                    val px = IntArray(w * h); fr.getPixels(px, 0, w, 0, 0, w, h)
                    val res = inpaintSolve(px, w, h, m, 24, 2) {}
                    inpaintApply(px, res)
                    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                } else fr
                last = b.copy(Bitmap.Config.ARGB_8888, false)
                b
            }
        } finally { StoryVideo.bitrate = 0 }
        if (!ok) return null
        onProg(95)
        return if (StoryVideo.mux(ctx, tmp, v.uri, totalMs * 1000L, out) && out.length() > 0) out else null
    } catch (e: Throwable) {
        return null
    } finally {
        try { r.release() } catch (_: Exception) {}
        tmp.delete()
    }
}

private fun removeFromVideo(ctx: android.content.Context, v: VInfo, w: Int, h: Int, strokes: List<MStroke>, fromSec: Float, toSec: Float, onProg: (Int) -> Unit): File? {
    val r = MediaMetadataRetriever()
    val tmp = File(ctx.cacheDir, "rm_v_${System.nanoTime()}.mp4")
    val out = File(ctx.cacheDir, "rm_out_${System.currentTimeMillis()}.mp4")
    try {
        r.setDataSource(ctx, v.uri)
        val totalMs = minOf(v.durMs, 45_000L)
        val fps = 24
        val frames = (totalMs * fps / 1000).toInt().coerceAtLeast(2)
        val mask = maskOf(renderMask(strokes, w, h, w * 0.008f + 2f))
        val first = frameAt(r, (fromSec * 1_000_000L).toLong(), w, h) ?: return null
        val fp = IntArray(w * h); first.getPixels(fp, 0, w, 0, 0, w, h)
        val res = inpaintSolve(fp, w, h, mask) { onProg(it * 15 / 100) }
        StoryVideo.bitrate = (w.toLong() * h * 4).toInt().coerceIn(1_500_000, 8_000_000)
        var last: Bitmap? = null
        val ok = try {
            StoryVideo.encodeVideo(tmp, w, h, fps, frames, { onProg(15 + it * 75 / 100) }) { i ->
                val tUs = i * 1_000_000L / fps
                val fr = frameAt(r, tUs, w, h) ?: last?.copy(Bitmap.Config.ARGB_8888, true) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val sec = tUs / 1_000_000f
                val b = if (sec >= fromSec && sec <= toSec) {
                    val px = IntArray(w * h); fr.getPixels(px, 0, w, 0, 0, w, h)
                    inpaintApply(px, res)
                    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                } else fr
                last = b.copy(Bitmap.Config.ARGB_8888, false)
                b
            }
        } finally { StoryVideo.bitrate = 0 }
        if (!ok) return null
        onProg(95)
        return if (StoryVideo.mux(ctx, tmp, v.uri, totalMs * 1000L, out) && out.length() > 0) out else null
    } catch (e: Throwable) {
        return null
    } finally {
        try { r.release() } catch (_: Exception) {}
        tmp.delete()
    }
}

// ---------------------------------------------------------------- صفحه

@Composable
fun RemoveScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }            // ۰ عکس، ۱ ویدیو
    var full by remember { mutableStateOf<Bitmap?>(null) }
    val history = remember { mutableStateListOf<Bitmap>() }
    var vinfo by remember { mutableStateOf<VInfo?>(null) }
    var vdims by remember { mutableStateOf(2 to 2) }
    var vframe by remember { mutableStateOf<Bitmap?>(null) }
    var vt by remember { mutableStateOf(0f) }
    var from by remember { mutableStateOf(0f) }
    var to by remember { mutableStateOf(10f) }
    val strokes = remember { mutableStateListOf<MStroke>() }
    var ver by remember { mutableStateOf(0) }
    var tool by remember { mutableStateOf(0) }
    var brush by remember { mutableStateOf(0.05f) }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf(0) }
    var showOrig by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var vresult by remember { mutableStateOf<File?>(null) }
    var tracking by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    val view = remember(full) { full?.let { fitDown(it, 1200) } }
    val shown: Bitmap? = if (mode == 0) view else vframe
    val origView = remember(history.size, full) { history.firstOrNull()?.let { fitDown(it, 1200) } }

    fun clearStrokes() { strokes.clear(); ver++ }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val inf = withContext(Dispatchers.IO) { MediaTrim.info(ctx, uri) }
            if (inf == null || !inf.hasVideo) { toast(ctx, "ویدیو خوانده نشد"); return@launch }
            val d = videoDims(inf)
            vinfo = VInfo(uri, inf.durMs, inf.w, inf.h, videoDims(inf, 480)); vdims = d; vt = 0f; from = 0f
            to = minOf(inf.durMs / 1000f, 45f); vresult = null; clearStrokes()
            vframe = withContext(Dispatchers.IO) { val r = MediaMetadataRetriever(); try { r.setDataSource(ctx, uri); frameAt(r, 0L, d.first, d.second) } finally { r.release() } }
        }
    }
    LaunchedEffect(vt, vinfo) {
        val v = vinfo ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        val d = vdims
        val f = withContext(Dispatchers.IO) { val r = MediaMetadataRetriever(); try { r.setDataSource(ctx, v.uri); frameAt(r, (vt * 1_000_000L).toLong(), d.first, d.second) } finally { r.release() } }
        if (f != null) vframe = f
    }

    ToolScaffold("حذف اشیاء", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipRow(listOf(0, 1), mode, { if (it == 0) "از عکس 🖼️" else "از ویدیو 🎬" }) { mode = it; clearStrokes() }
            if (mode == 0) MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b -> full = fitDown(b, 3000); history.clear(); clearStrokes(); showOrig = false })
            else OutlinedButton({ pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Icon(Icons.Rounded.VideoLibrary, null); Spacer(Modifier.width(6.dp)); Text(if (vinfo == null) "انتخاب ویدیو" else "انتخاب ویدیوی دیگر")
            }

            val bmp = if (showOrig && mode == 0) (origView ?: shown) else shown
            if (bmp != null) {
                Text("روی چیزی که می‌خواهی حذف شود با انگشت بکش (اطرافش را کمی بیشتر بپوشان).", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.7f))
                val mw = 480; val mh = (480f * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                val maskBmp = remember(ver, bmp) { renderMask(strokes, mw, mh, mw * 0.004f) }
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).clip(RoundedCornerShape(16.dp)).onSizeChanged { size = it }) {
                    val img = remember(bmp) { bmp.asImageBitmap() }
                    val mimg = remember(maskBmp) { maskBmp.asImageBitmap() }
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(tool, brush, bmp) {
                                detectTapGestures { o ->
                                    if (tool != 1) { val s = MStroke(tool, brush); s.pts.add(androidx.compose.ui.geometry.Offset(o.x / size.width, o.y / size.height)); strokes.add(s); ver++ }
                                }
                            }
                            .pointerInput(tool, brush, bmp) {
                                var cur: MStroke? = null
                                detectDragGestures(
                                    onDragStart = { o ->
                                        val s = MStroke(tool, brush); s.pts.add(androidx.compose.ui.geometry.Offset(o.x / size.width, o.y / size.height)); strokes.add(s); cur = s; ver++
                                    },
                                    onDrag = { ch, _ ->
                                        val s = cur
                                        if (s != null) {
                                            val p = androidx.compose.ui.geometry.Offset((ch.position.x / size.width).coerceIn(0f, 1f), (ch.position.y / size.height).coerceIn(0f, 1f))
                                            if (s.kind == 1) { if (s.pts.size > 1) s.pts[1] = p else s.pts.add(p) } else s.pts.add(p)
                                            ver++
                                        }
                                    },
                                    onDragEnd = { cur = null }, onDragCancel = { cur = null },
                                )
                            },
                    ) {
                        val dst = IntSize(this.size.width.toInt(), this.size.height.toInt())
                        drawImage(img, dstSize = dst)
                        drawImage(mimg, dstSize = dst)
                    }
                }
                if (busy) {
                    LinearProgressIndicator(progress = { prog / 100f }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                    Text("در حال پردازش… $prog٪", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
                }
                SectionTitle("ابزار")
                ChipRow(listOf(0, 1, 2), tool, { listOf("قلم 🖌️", "مستطیل ▭", "پاک‌کن ✏️")[it] }) { tool = it }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("اندازه‌ی قلم", fontSize = 13.sp)
                    Slider(brush, { brush = it }, valueRange = 0.015f..0.14f)
                }
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); ver++ } }, Modifier.weight(1f), enabled = strokes.isNotEmpty() && !busy) { Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("یک قدم", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton({ clearStrokes() }, Modifier.weight(1f), enabled = strokes.isNotEmpty() && !busy) { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("پاک‌کردن علامت‌ها", fontSize = 12.sp, maxLines = 1) }
                }

                if (mode == 0) {
                    GradButton(
                        if (busy) "در حال حذف…" else "حذف کن ✨", icon = Icons.Rounded.AutoFixHigh, loading = busy, enabled = strokes.isNotEmpty() && !busy,
                        colors = listOf(androidx.compose.ui.graphics.Color(0xFFFF5C8A), androidx.compose.ui.graphics.Color(0xFF8E54E9)),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        onClick = {
                            val cur = full ?: return@GradButton
                            val sl = strokes.toList()
                            scope.launch {
                                busy = true; prog = 0
                                val r = withContext(Dispatchers.Default) { try { removeFromPhoto(cur, sl) { prog = it } } catch (e: Throwable) { null } }
                                busy = false
                                if (r != null) { history.add(cur); if (history.size > 6) history.removeAt(0); full = r; clearStrokes(); showOrig = false }
                                else toast(ctx, "حذف ناموفق بود؛ ناحیه‌ی کوچک‌تری را امتحان کن")
                            }
                        },
                    )
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ if (history.isNotEmpty()) { full = history.removeAt(history.lastIndex); clearStrokes() } }, Modifier.weight(1f), enabled = history.isNotEmpty() && !busy) { Text("برگشت به قبل", fontSize = 12.sp, maxLines = 1) }
                        OutlinedButton({ showOrig = !showOrig }, Modifier.weight(1f), enabled = history.isNotEmpty()) { Icon(Icons.Rounded.Compare, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(if (showOrig) "نتیجه" else "عکس اصلی", fontSize = 12.sp, maxLines = 1) }
                    }
                    OutlinedButton(
                        { val f = full; if (f != null && canExport()) shareBitmap(ctx, f) },
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = history.isNotEmpty() && !busy,
                    ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری (واتساپ، استوری …)") }
                    GradButton(
                        "ذخیره در گالری", icon = Icons.Rounded.Download, enabled = history.isNotEmpty() && !busy,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        onClick = {
                            val f = full
                            if (f != null && canExport()) scope.launch {
                                val ok = withContext(Dispatchers.IO) { saveBitmapToGallery(ctx, f) }
                                toast(ctx, if (ok) "در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                            }
                        },
                    )
                } else {
                    val v = vinfo
                    if (v != null) {
                        val dsec = minOf(v.durMs / 1000f, if (tracking) 25f else 45f)
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Switch(tracking, { tracking = it; if (it) { to = minOf(to, 25f); vt = minOf(vt, 25f) } }); Spacer(Modifier.width(10.dp))
                            Text("شیء متحرک است (دنبال شود)", fontSize = 13.sp)
                        }
                        if (tracking) Text("علامت را روی فریمی بزن که شیء در آن کامل پیداست؛ برنامه حرکتش را در بازه دنبال می‌کند. حداکثر ۲۵ ثانیه، ۱۵ فریم بر ثانیه و کیفیت ۴۸۰.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.7f))
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("نمایش فریم در ثانیه‌ی ${"%.1f".format(vt)}", fontSize = 13.sp)
                            Slider(vt, { vt = it }, valueRange = 0f..dsec.coerceAtLeast(0.5f))
                            Text("حذف از ثانیه‌ی ${"%.1f".format(from)} تا ${"%.1f".format(to)}", fontSize = 13.sp)
                            Slider(from, { from = it.coerceAtMost(to - 0.3f) }, valueRange = 0f..dsec.coerceAtLeast(0.5f))
                            Slider(to, { to = it.coerceAtLeast(from + 0.3f) }, valueRange = 0f..dsec.coerceAtLeast(0.5f))
                        }
                        GradButton(
                            if (busy) "در حال ساخت ویدیو…" else "حذف از ویدیو و ذخیره ✨", icon = Icons.Rounded.AutoFixHigh, loading = busy, enabled = strokes.isNotEmpty() && !busy,
                            colors = listOf(androidx.compose.ui.graphics.Color(0xFFFF5C8A), androidx.compose.ui.graphics.Color(0xFF8E54E9)),
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            onClick = {
                                if (canExport()) {
                                    val sl = strokes.toList(); val d = vdims; val f0 = from; val t0 = to
                                    scope.launch {
                                        busy = true; prog = 0
                                        val trk = tracking; val rv = vt
                                        val out = withContext(Dispatchers.Default) { if (trk) removeTracked(ctx, v, sl, f0, t0, rv) { prog = it } else removeFromVideo(ctx, v, d.first, d.second, sl, f0, t0) { prog = it } }
                                        busy = false
                                        if (out != null) {
                                            vresult = out
                                            val saved = withContext(Dispatchers.IO) { saveFileToGallery(ctx, out, "AIHub_clean_${System.currentTimeMillis()}.mp4", "video/mp4") }
                                            toast(ctx, if (saved) "ویدیو در گالری ذخیره شد (Movies/AIHub)" else "ساخته شد ولی ذخیره نشد")
                                        } else toast(ctx, "ساخت ویدیو ناموفق بود")
                                    }
                                }
                            },
                        )
                        vresult?.let { f -> OutlinedButton({ shareFile(ctx, f, "video/mp4") }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری (واتساپ، استوری …)") } }
                        Text("برای اشیای ثابت مثل لوگو، واترمارک، متن و فرد یا چیزی که در یک جای کادر می‌ماند. شیء متحرک دنبال نمی‌شود. حداکثر ۴۵ ثانیه و کیفیت تا ۷۲۰؛ ساخت چند دقیقه طول می‌کشد.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f))
                    }
                }
            } else {
                Text(
                    if (mode == 0) "یک عکس انتخاب کن، روی آدم یا چیز اضافه بکش و «حذف کن» را بزن؛ جایش با پس‌زمینه‌ی اطراف پر می‌شود."
                    else "یک ویدیو انتخاب کن و روی لوگو، واترمارک یا چیز ثابتی که می‌خواهی حذف شود بکش.",
                    Modifier.padding(24.dp), color = c.onBackground.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
