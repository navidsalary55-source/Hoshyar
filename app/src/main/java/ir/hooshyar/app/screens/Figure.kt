package ir.hooshyar.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.*

// ---------------------------------------------------------------- مدل شکل (JSON → رسم با Canvas)

internal class FSeg(val a: String, val b: String, val label: String, val dashed: Boolean)
internal class FCircle(val center: String, val r: Double, val dashed: Boolean, val label: String)
internal class FAngle(val at: String, val from: String, val to: String, val label: String, val right: Boolean)
internal class FArrow(val from: String, val to: String, val label: String)
internal class FText(val x: Double, val y: Double, val text: String)
internal class FPlot(val fn: String, val xmin: Double, val xmax: Double, val label: String)

internal class Fig(
    val title: String,
    val points: Map<String, Pair<Double, Double>>,
    val segs: List<FSeg>, val circles: List<FCircle>, val angles: List<FAngle>,
    val arrows: List<FArrow>, val texts: List<FText>, val plots: List<FPlot>,
) {
    val isEmpty get() = points.isEmpty() && plots.isEmpty() && texts.isEmpty()
}

/** متن پاسخ مدل (ممکن است داخل ```json باشد) را به شکل تبدیل می‌کند؛ در صورت خطا استثنا می‌دهد */
internal fun parseFig(raw: String): Fig {
    val i = raw.indexOf('{'); val j = raw.lastIndexOf('}')
    if (i < 0 || j <= i) throw IllegalArgumentException("پاسخ شکل خوانده نشد")
    val o = JSONObject(raw.substring(i, j + 1))
    val pts = LinkedHashMap<String, Pair<Double, Double>>()
    o.optJSONObject("points")?.let { po ->
        po.keys().forEach { k ->
            val a = po.optJSONArray(k)
            if (a != null && a.length() >= 2) pts[k] = a.optDouble(0) to a.optDouble(1)
        }
    }
    fun <T> list(key: String, f: (JSONObject) -> T?): List<T> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { idx -> a.optJSONObject(idx)?.let(f) }
    }
    val segs = list("segments") { s ->
        val a = s.optString("a"); val b = s.optString("b")
        if (a in pts && b in pts) FSeg(a, b, s.optString("label"), s.optBoolean("dashed")) else null
    }
    val circles = list("circles") { c ->
        val ce = c.optString("center"); val r = c.optDouble("r", Double.NaN)
        if (ce in pts && r > 0 && r.isFinite()) FCircle(ce, r, c.optBoolean("dashed"), c.optString("label")) else null
    }
    val angles = list("angles") { a ->
        val at = a.optString("at"); val f = a.optString("from"); val t = a.optString("to")
        if (at in pts && f in pts && t in pts) FAngle(at, f, t, a.optString("label"), a.optBoolean("right")) else null
    }
    val arrows = list("arrows") { a ->
        val f = a.optString("from"); val t = a.optString("to")
        if (f in pts && t in pts) FArrow(f, t, a.optString("label")) else null
    }
    val texts = list("texts") { t ->
        val at = t.optJSONArray("at")
        if (at != null && at.length() >= 2 && t.optString("text").isNotBlank()) FText(at.optDouble(0), at.optDouble(1), t.optString("text")) else null
    }
    val plots = list("plots") { p ->
        val fn = p.optString("fn")
        val x0 = p.optDouble("xmin", -5.0); val x1 = p.optDouble("xmax", 5.0)
        if (fn.isNotBlank() && x1 > x0) FPlot(fn, x0, x1, p.optString("label")) else null
    }
    return Fig(o.optString("title"), pts, segs, circles, angles, arrows, texts, plots)
}

internal const val FIGURE_SYSTEM = """You design clean math / physics figures as data. Output ONLY one JSON object, no explanation, no markdown fences.
Schema (all keys optional except what you need):
{"title":"short Persian title",
 "points":{"A":[0,0],"B":[6,0]},
 "segments":[{"a":"A","b":"B","label":"6","dashed":false}],
 "circles":[{"center":"O","r":3,"dashed":false,"label":""}],
 "angles":[{"at":"B","from":"A","to":"C","label":"60°","right":false}],
 "arrows":[{"from":"A","to":"B","label":"F = 20 N"}],
 "texts":[{"at":[1,2],"text":"label"}],
 "plots":[{"fn":"x^2 - 4","xmin":-5,"xmax":5,"label":"y = x² − 4"}]}
Rules:
- Cartesian plane, y points up. Numbers must satisfy ALL given lengths and angles EXACTLY: compute coordinates numerically (for example A=(0,0), B=(c,0), C=(b*cos(A), b*sin(A)); right angle at B means perpendicular sides; circle through points means r equals the distance from the center).
- Use the point names of the problem (A, B, C, O, H, M ...). A point name starting with "_" is an unnamed helper (its name is not drawn).
- Put given numbers in labels (short plain text, e.g. "5", "60°", "x"); unknown values as letters or "?".
- For a function graph use ONLY "plots" (no points). fn syntax: + - * / ^ ( ), sin cos tan sqrt ln log exp abs, variable x, constants pi and e. Several plots are allowed (for example a curve and its tangent line).
- For physics draw the bodies as simple shapes from segments, forces and velocities as arrows with labels like "F₁ = 20 N", "mg", "N".
- Keep it simple and readable: at most 40 elements in total. Coordinates are plain numbers (no expressions)."""

// ---------------------------------------------------------------- رسم

private fun niceStep(range: Double): Double {
    val raw = range / 6.0
    val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return p * when { f < 1.5 -> 1.0; f < 3.5 -> 2.0; f < 7.5 -> 5.0; else -> 10.0 }
}

@Composable
internal fun FigureView(f: Fig, accent: Color, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(20.dp)).background(bg)) {
        drawFigure(f, accent, ink, tm)
    }
}

/** شکل را در یک تصویر PNG (زمینه‌ی سفید، مناسب چاپ و اشتراک) می‌کشد */
internal fun renderFig(f: Fig, accent: Color, fontResolver: androidx.compose.ui.text.font.FontFamily.Resolver, w: Int = 1400): android.graphics.Bitmap {
    val h = (w / 1.1f).toInt()
    val dens = androidx.compose.ui.unit.Density(w / 360f)
    val tm = androidx.compose.ui.text.TextMeasurer(fontResolver, dens, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val img = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(img)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(dens, androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawRect(Color.White)
        drawFigure(f, accent, Color(0xFF111827), tm)
    }
    return img.asAndroidBitmap()
}

internal fun DrawScope.drawFigure(f: Fig, accent: Color, ink: Color, tm: androidx.compose.ui.text.TextMeasurer) {
    run {
        val soft = ink.copy(alpha = 0.18f)
        val pad = 34.dp.toPx()
        val usableW = size.width - 2 * pad
        val usableH = size.height - 2 * pad
        val dashed = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        val stroke = 2.4f * density

        // نمودار تابع؟
        val plotMode = f.plots.isNotEmpty() && f.points.isEmpty()

        // نمونه‌برداری از نمودارها
        class Curve(val xs: DoubleArray, val ys: DoubleArray, val label: String)
        val curves = ArrayList<Curve>()
        for (p in f.plots) {
            val n = 360
            val xs = DoubleArray(n) { p.xmin + (p.xmax - p.xmin) * it / (n - 1) }
            val expr = normalizeExpr(p.fn)
            val ys = DoubleArray(n) { k -> try { Calc(expr, false, xs[k]).run() } catch (e: Exception) { Double.NaN } }
            curves.add(Curve(xs, ys, p.label))
        }

        // محدوده‌ی مختصات
        var x0 = Double.POSITIVE_INFINITY; var x1 = Double.NEGATIVE_INFINITY
        var y0 = Double.POSITIVE_INFINITY; var y1 = Double.NEGATIVE_INFINITY
        fun grow(x: Double, y: Double) { if (x.isFinite() && y.isFinite()) { x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y) } }
        f.points.values.forEach { grow(it.first, it.second) }
        f.circles.forEach { c -> f.points[c.center]?.let { grow(it.first - c.r, it.second - c.r); grow(it.first + c.r, it.second + c.r) } }
        f.texts.forEach { grow(it.x, it.y) }
        for (c in curves) {
            c.xs.forEach { grow(it, y0.takeIf { v -> v.isFinite() } ?: 0.0) }
            val good = c.ys.filter { it.isFinite() && abs(it) < 1e6 }.sorted()
            if (good.isNotEmpty()) {
                // برای تابع‌هایی که به بی‌نهایت می‌روند، ۲٪ بالا و پایین را نادیده بگیر
                val lo = good[(good.size * 0.02).toInt().coerceAtMost(good.size - 1)]
                val hi = good[(good.size * 0.98).toInt().coerceAtMost(good.size - 1)]
                grow(c.xs.first(), lo); grow(c.xs.last(), hi)
            }
        }
        if (!x0.isFinite()) { x0 = -1.0; x1 = 1.0; y0 = -1.0; y1 = 1.0 }
        if (plotMode) { x0 = min(x0, 0.0); x1 = max(x1, 0.0); y0 = min(y0, 0.0); y1 = max(y1, 0.0) }
        if (x1 - x0 < 1e-9) { x0 -= 1; x1 += 1 }
        if (y1 - y0 < 1e-9) { y0 -= 1; y1 += 1 }
        val xr = x1 - x0; val yr = y1 - y0

        val sx: Float; val sy: Float
        if (plotMode) { sx = (usableW / xr).toFloat(); sy = (usableH / yr).toFloat() }
        else { val s = min(usableW / xr, usableH / yr).toFloat(); sx = s; sy = s }
        val ox = (pad + (usableW - xr * sx) / 2.0).toFloat()
        val oy = (pad + (usableH - yr * sy) / 2.0).toFloat()
        fun m(x: Double, y: Double) = Offset(ox + ((x - x0) * sx).toFloat(), oy + ((y1 - y) * sy).toFloat())
        fun mp(name: String): Offset = f.points[name]!!.let { m(it.first, it.second) }

        fun label(t: String, at: Offset, color: Color = ink, size: Float = 13f, bold: Boolean = false) {
            if (t.isBlank()) return
            val r = tm.measure(t, TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null))
            drawText(r, topLeft = Offset(at.x - r.size.width / 2f, at.y - r.size.height / 2f))
        }

        // مرکز شکل برای جهت‌دادن برچسب‌ها
        val cen = if (f.points.isEmpty()) Offset(size.width / 2f, size.height / 2f)
        else Offset(f.points.values.map { m(it.first, it.second).x }.average().toFloat(), f.points.values.map { m(it.first, it.second).y }.average().toFloat())
        fun outward(p: Offset, dist: Float): Offset {
            val d = p - cen; val l = hypot(d.x, d.y)
            return if (l < 1f) Offset(p.x, p.y - dist) else Offset(p.x + d.x / l * dist, p.y + d.y / l * dist)
        }

        // محورها و شبکه‌ی نمودار
        if (plotMode) {
            val stepX = niceStep(xr); val stepY = niceStep(yr)
            var gx = ceil(x0 / stepX) * stepX
            while (gx <= x1 + 1e-9) {
                drawLine(soft, m(gx, y0), m(gx, y1), 1f)
                if (abs(gx) > 1e-9) label(fmtNum(gx), m(gx, 0.0) + Offset(0f, 12f * density), ink.copy(alpha = 0.6f), 10f)
                gx += stepX
            }
            var gy = ceil(y0 / stepY) * stepY
            while (gy <= y1 + 1e-9) {
                drawLine(soft, m(x0, gy), m(x1, gy), 1f)
                if (abs(gy) > 1e-9) label(fmtNum(gy), m(0.0, gy) + Offset(-14f * density, 0f), ink.copy(alpha = 0.6f), 10f)
                gy += stepY
            }
            drawLine(ink.copy(alpha = 0.7f), m(x0, 0.0), m(x1, 0.0), 2f)
            drawLine(ink.copy(alpha = 0.7f), m(0.0, y0), m(0.0, y1), 2f)
            label("x", m(x1, 0.0) + Offset(-8f * density, -10f * density), ink, 12f, true)
            label("y", m(0.0, y1) + Offset(10f * density, 8f * density), ink, 12f, true)
        }

        // نمودارها
        val palette = listOf(accent, Color(0xFFEF4444), Color(0xFF16A34A), Color(0xFFF59E0B))
        curves.forEachIndexed { ci, c ->
            val col = palette[ci % palette.size]
            val path = Path()
            var pen = false
            for (k in c.xs.indices) {
                val y = c.ys[k]
                if (!y.isFinite() || y < y0 - yr * 0.5 || y > y1 + yr * 0.5) { pen = false; continue }
                val o = m(c.xs[k], y)
                if (!pen) { path.moveTo(o.x, o.y); pen = true } else path.lineTo(o.x, o.y)
            }
            drawPath(path, col, style = Stroke(width = stroke))
            if (c.label.isNotBlank()) label(c.label, Offset(size.width - pad - 10f * density, pad / 2f + ci * 16f * density), col, 12f, true)
        }

        // دایره‌ها
        for (c in f.circles) {
            val ce = mp(c.center)
            drawCircle(ink, radius = (c.r * sx).toFloat(), center = ce, style = Stroke(width = stroke, pathEffect = if (c.dashed) dashed else null))
            if (c.label.isNotBlank()) label(c.label, ce + Offset((c.r * sx * 0.7).toFloat(), -(c.r * sx * 0.7).toFloat()), accent, 12f, true)
        }

        // پاره‌خط‌ها
        for (s in f.segs) {
            val a = mp(s.a); val b = mp(s.b)
            drawLine(ink, a, b, stroke, pathEffect = if (s.dashed) dashed else null)
            if (s.label.isNotBlank()) {
                val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                label(s.label, outward(mid, 14f * density), accent, 12.5f, true)
            }
        }

        // فلش‌ها (نیرو، سرعت)
        for (ar in f.arrows) {
            val a = mp(ar.from); val b = mp(ar.to)
            drawLine(accent, a, b, stroke * 1.2f)
            val ang = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
            val hl = 12f * density
            for (sgn in listOf(-1, 1)) {
                val t = ang + PI - sgn * 0.45
                drawLine(accent, b, Offset(b.x + (hl * cos(t)).toFloat(), b.y + (hl * sin(t)).toFloat()), stroke * 1.2f)
            }
            if (ar.label.isNotBlank()) {
                val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                val nx = (-(b.y - a.y)); val ny = (b.x - a.x); val l = max(hypot(nx, ny), 1f)
                label(ar.label, Offset(mid.x + nx / l * 14f * density, mid.y + ny / l * 14f * density), accent, 12f, true)
            }
        }

        // زاویه‌ها
        for (an in f.angles) {
            val v = mp(an.at); val p1 = mp(an.from); val p2 = mp(an.to)
            val d1 = p1 - v; val d2 = p2 - v
            val l1 = max(hypot(d1.x, d1.y), 1f); val l2 = max(hypot(d2.x, d2.y), 1f)
            val u1 = Offset(d1.x / l1, d1.y / l1); val u2 = Offset(d2.x / l2, d2.y / l2)
            val r = 22f * density
            if (an.right) {
                val s = 14f * density
                val q1 = v + Offset(u1.x * s, u1.y * s); val q3 = v + Offset(u2.x * s, u2.y * s)
                val q2 = q1 + Offset(u2.x * s, u2.y * s)
                drawLine(accent, q1, q2, 2f * density); drawLine(accent, q2, q3, 2f * density)
            } else {
                val a1 = Math.toDegrees(atan2(d1.y.toDouble(), d1.x.toDouble())).toFloat()
                val a2 = Math.toDegrees(atan2(d2.y.toDouble(), d2.x.toDouble())).toFloat()
                val sweep = ((a2 - a1 + 540f) % 360f) - 180f
                drawArc(accent, a1, sweep, false, topLeft = v - Offset(r, r), size = Size(2 * r, 2 * r), style = Stroke(width = 2f * density))
            }
            val bis = Offset(u1.x + u2.x, u1.y + u2.y); val bl = hypot(bis.x, bis.y)
            val dir = if (bl < 0.05f) Offset(-u1.y, u1.x) else Offset(bis.x / bl, bis.y / bl)
            if (an.label.isNotBlank()) label(an.label, v + Offset(dir.x * (r + 16f * density), dir.y * (r + 16f * density)), accent, 12f, true)
        }

        // نقطه‌ها و نام‌ها
        for ((name, p) in f.points) {
            val o = m(p.first, p.second)
            drawCircle(ink, 4.2f * density, o)
            if (!name.startsWith("_")) label(name, outward(o, 15f * density), ink, 15f, true)
        }
        for (t in f.texts) label(t.text, m(t.x, t.y), ink, 12.5f)

        if (f.title.isNotBlank()) label(f.title, Offset(size.width / 2f, 14f * density), ink.copy(alpha = 0.75f), 12f, true)
    }
}

