package com.example.aihub.screens

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------- مدل‌ها

private enum class SFmt(val label: String, val w: Int, val h: Int) {
    STORY("استوری ۹:۱۶", 1080, 1920), POST("پست مربع", 1080, 1080), PORTRAIT("پست ۴:۵", 1080, 1350), WIDE("کاور ۱۶:۹", 1920, 1080)
}

private enum class TStyle(val label: String) {
    PLAIN("ساده"), SHADOW("سایه"), OUTLINE("دورخط"), PILL("نوار"), NEON("نئون"), EXTRUDE("سه‌بعدی"), LONG("سایه‌ی بلند"), GLITCH("گلیچ")
}

private enum class TFont(val label: String, val asset: String?) {
    VAZIR("وزیر", "fonts/Vazirmatn-Regular.ttf"),
    VAZIR_BOLD("وزیر ضخیم", "fonts/Vazirmatn-Bold.ttf"),
    VAZIR_BLACK("وزیر سیاه", "fonts/Vazirmatn-Black.ttf"),
    ESTEDAD("استعداد", "fonts/Estedad-Black.ttf"),
    AMIRI("امیری", "fonts/Amiri-Bold.ttf"),
    SHABNAM("شبنم", "fonts/Shabnam-Bold.ttf"),
    SAMIM("صمیم", "fonts/Samim-Bold.ttf"),
    RUQAA("رقعه (خوشنویسی)", "fonts/ArefRuqaa-Bold.ttf"),
    NASTALIQ("نستعلیق", "fonts/NotoNastaliqUrdu.ttf"),
    LALEZAR("لاله‌زار", "fonts/Lalezar-Regular.ttf"),
    SYSTEM("سیستم", null), SERIF("کتابی", null), MONO("ماشین‌تحریر", null)
}

private enum class VOut(val label: String, val ext: String, val mime: String) {
    MP4("ویدیو (MP4)", "mp4", "video/mp4"), MP3("فقط موزیک (MP3)", "mp3", "audio/mpeg"), M4A("فقط موزیک (M4A)", "m4a", "audio/mp4"), WAV("فقط موزیک (WAV)", "wav", "audio/wav")
}

private enum class SFilt(val label: String) {
    NONE("اصلی"), BW("سیاه‌وسفید"), SEPIA("قدیمی"), VIVID("پرانرژی"), WARM("گرم"), COOL("سرد"), FADE("محو"), DRAMA("دراماتیک"), ROSE("رزی"), MINT("نعنایی")
}
private enum class SFrame(val label: String) { NONE("بدون کادر"), WHITE("کادر سفید"), POLAROID("پولاروید"), ROUND("گوشه‌گرد") }
private enum class SShape(val label: String) { RECT("مستطیل"), ROUND("گوشه‌گرد"), CIRCLE("دایره"), HEART("قلب"), STAR("ستاره"), LINE("خط") }
private enum class SKind { TEXT, EMOJI, SHAPE }
private enum class STab(val label: String) { TEMPLATE("قالب‌ها"), BG("پس‌زمینه"), TEXT("متن"), STICKER("استیکر و شکل"), FILTER("فیلتر"), FRAME("کادر"), LAYERS("لایه‌ها"), VIDEO("ویدیو و موزیک") }

private object StoryFonts {
    var assets: AssetManager? = null
    private val cache = HashMap<TFont, Typeface>()
    fun get(f: TFont): Typeface {
        cache[f]?.let { return it }
        val t = try {
            if (f.asset != null) Typeface.createFromAsset(assets, f.asset)
            else when (f) {
                TFont.SERIF -> Typeface.SERIF
                TFont.MONO -> Typeface.MONOSPACE
                else -> Typeface.DEFAULT_BOLD
            }
        } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        cache[f] = t
        return t
    }
}

private val SGrads = listOf(
    0xFF7C6CFF to 0xFF3FA2F7, 0xFFFF5C8A to 0xFFFFA62B, 0xFF12C2A7 to 0xFF2EC4F1, 0xFF232526 to 0xFF414345,
    0xFFFFD1FF to 0xFFFAD0C4, 0xFF0F2027 to 0xFF2C5364, 0xFFF857A6 to 0xFFFF5858, 0xFFFDFCFB to 0xFFE2D1C3,
    0xFF8E54E9 to 0xFF47B8E0, 0xFF56AB2F to 0xFFA8E063,
)
private val SColors = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFFFD23F, 0xFFFF5C8A, 0xFF3FA2F7, 0xFF5BC97B, 0xFF7C6CFF, 0xFFFF8A5C, 0xFFE63946, 0xFF9AA0A6,
).map { it.toInt() }
private val SEmoji = listOf(
    "❤️", "🔥", "✨", "⭐", "😍", "😂", "🥰", "😎", "🎉", "🎂", "🎈", "🎁", "☀️", "🌙", "🌸", "🌈",
    "☕", "🍕", "🍰", "📍", "📢", "💎", "👑", "💯", "👍", "🙏", "💪", "🚀", "🎵", "📸", "🛍️", "💌",
    "🌹", "🦋", "🍀", "⚡", "🎶", "🏆", "🎯", "💫", "🥳", "😘", "🤩", "🙌", "💥", "🌟", "🎀", "🧿",
)

private class SLayer(val id: Long, val kind: SKind, var text: String) {
    var x = 0.5f; var y = 0.5f; var scale = 1f; var rot = 0f
    var color: Int = 0xFFFFFFFF.toInt()
    var color2: Int = 0xFFFF5C8A.toInt()
    var grad = false
    var bg: Int = 0xCC000000.toInt()
    var style = TStyle.SHADOW
    var font = TFont.VAZIR_BOLD
    var shape = SShape.RECT
    var size = 0.07f          // نسبت به عرض بوم
    var strokeW = 0.014f
    var strokeColor = 0       // ۰ = خودکار
    var opacity = 1f
    var spacing = 0f
    var lineMul = 1f
    var hwf = 0.1f            // نیم‌عرض و نیم‌ارتفاع (نسبت به عرض بوم)؛ برای متن و ایموجی در رندر پر می‌شود
    var hhf = 0.05f

    fun copyOf(): SLayer = SLayer(id, kind, text).also {
        it.x = x; it.y = y; it.scale = scale; it.rot = rot; it.color = color; it.color2 = color2; it.grad = grad; it.bg = bg
        it.style = style; it.font = font; it.shape = shape; it.size = size; it.strokeW = strokeW; it.strokeColor = strokeColor
        it.opacity = opacity; it.spacing = spacing; it.lineMul = lineMul; it.hwf = hwf; it.hhf = hhf
    }
}

private class Lyr(val start: Float, val end: Float, val text: String)

/** خط‌های بلند را به تکه‌های کوتاه (حداکثر ۴ کلمه) می‌شکند تا مثل استوری، کم‌کم و همزمان با خواندن بیایند */
private fun splitLyrics(segs: List<Triple<Float, Float, String>>): List<Lyr> {
    val out = ArrayList<Lyr>()
    for ((s, e, text) in segs) {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) continue
        val parts = words.chunked(4).let { ch -> if (ch.size > 1 && ch.last().size == 1) ch.dropLast(2) + listOf(ch[ch.size - 2] + ch.last()) else ch }
        val total = parts.sumOf { it.joinToString(" ").length }.coerceAtLeast(1)
        var acc = 0
        for (pt in parts) {
            val str = pt.joinToString(" ")
            val a = s + (e - s) * acc / total
            acc += str.length
            val b = s + (e - s) * acc / total
            out.add(Lyr(a, b, str))
        }
    }
    return out
}

private class LFrame(val text: String, val alpha: Float, val scale: Float, val dy: Float)

/** خط فعلی آهنگ در ثانیه‌ی t؛ anim: ۰ محو، ۱ پاپ، ۲ بالا آمدن، ۳ تایپ */
private fun lyricFrame(ls: List<Lyr>, t: Float, anim: Int): LFrame? {
    for (l in ls) {
        if (t >= l.start - 0.05f && t <= l.end + 0.15f) {
            val age = (t - l.start).coerceAtLeast(0f)
            val a = min(age / 0.25f + 0.2f, (l.end + 0.15f - t) / 0.25f).coerceIn(0f, 1f)
            var text = l.text; var sc = 1f; var dy = 0f
            when (anim) {
                1 -> sc = 0.75f + 0.25f * easeBack((age / 0.35f).coerceIn(0f, 1f))
                2 -> dy = (1f - easeOut((age / 0.4f).coerceIn(0f, 1f))) * 0.04f
                3 -> { val pr = (age / max(0.3f, (l.end - l.start) * 0.6f)).coerceIn(0f, 1f); text = l.text.take(kotlin.math.ceil(l.text.length * pr).toInt().coerceAtLeast(1)) }
            }
            return LFrame(text, a, sc, dy)
        }
    }
    return null
}

/** تنظیمات یک فریم ویدیو */
private class VFx {
    var motion = 0; var p = 0f; var t = 0f; var tanim = 0
    var lyric: SLayer? = null; var lyricAlpha = 1f; var lyricScale = 1f; var lyricDy = 0f
    var photoA: Bitmap? = null; var photoB: Bitmap? = null; var mix = 0f
    var fadeBlack = 0f; var progressBar = false
}

private class VCfg(
    val motion: Int, val tanim: Int, val lanim: Int, val dur: Float, val lyrics: List<Lyr>, val lyricOn: Boolean,
    val lyricStyle: SLayer, val photos: List<Bitmap>, val fadeBlack: Boolean, val pbar: Boolean,
)

private fun makeFx(c: VCfg, p: Float): VFx {
    val fx = VFx()
    val t = p * c.dur
    fx.motion = c.motion; fx.p = p; fx.t = t; fx.tanim = c.tanim; fx.progressBar = c.pbar
    if (c.lyricOn) {
        val lf = lyricFrame(c.lyrics, t, c.lanim)
        if (lf != null) {
            val ls = c.lyricStyle.copyOf(); ls.text = lf.text
            fx.lyric = ls; fx.lyricAlpha = lf.alpha; fx.lyricScale = lf.scale; fx.lyricDy = lf.dy
        }
    }
    val n = c.photos.size
    if (n > 1) {
        val seg = (p * n).coerceIn(0f, n - 0.0001f)
        val i = seg.toInt()
        val local = seg - i
        fx.photoA = c.photos[i]
        val tr = (0.7f / (c.dur / n)).coerceIn(0.05f, 0.5f)
        if (i < n - 1 && local > 1f - tr) { fx.photoB = c.photos[i + 1]; fx.mix = (local - (1f - tr)) / tr }
    }
    if (c.fadeBlack) fx.fadeBlack = max(0f, max(1f - t / 0.6f, (t - (c.dur - 0.8f)) / 0.8f)).coerceIn(0f, 1f)
    return fx
}

private class Snap(
    val fmt: SFmt, val photo: Bitmap?, val grad: Int, val solid: Int?, val bgScale: Float, val bgDx: Float, val bgDy: Float,
    val rot90: Int, val flip: Boolean, val angle: Float, val blur: Float, val filt: SFilt, val bright: Float, val contrast: Float,
    val sat: Float, val vignette: Boolean, val shade: Boolean, val frame: SFrame, val layers: List<SLayer>, val sel: Long, val hide: Boolean,
)

private class StoryState {
    var fmt = SFmt.STORY
    var photo: Bitmap? = null
    var grad = 0
    var solid: Int? = null
    var bgScale = 1f; var bgDx = 0f; var bgDy = 0f
    var rot90 = 0; var flip = false; var angle = 0f; var blur = 0f
    var filt = SFilt.NONE
    var bright = 0f; var contrast = 1f; var sat = 1f
    var vignette = false; var shade = false
    var hidePhoto = false
    var frame = SFrame.NONE
    var layers = mutableListOf<SLayer>()
    var sel = -1L
    var tick by mutableStateOf(0)
    var dirty = false
    private var nextId = 1L
    private val hist = mutableListOf<Snap>()
    private var hi = -1

    fun change(f: StoryState.() -> Unit) { f(); tick++ }
    /** تغییر گسسته: اعمال + ثبت در تاریخچه (برای برگردان) */
    fun act(f: StoryState.() -> Unit) { f(); commit(); tick++ }
    fun newLayer(kind: SKind, text: String): SLayer = SLayer(nextId++, kind, text)
    fun selected(): SLayer? = layers.firstOrNull { it.id == sel }

    private fun snap() = Snap(fmt, photo, grad, solid, bgScale, bgDx, bgDy, rot90, flip, angle, blur, filt, bright, contrast, sat, vignette, shade, frame, layers.map { it.copyOf() }, sel, hidePhoto)
    fun commit() {
        while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
        hist.add(snap()); hi = hist.size - 1
        if (hist.size > 40) { hist.removeAt(0); hi-- }
        dirty = false
    }
    private fun restore(s: Snap) {
        fmt = s.fmt; photo = s.photo; grad = s.grad; solid = s.solid; bgScale = s.bgScale; bgDx = s.bgDx; bgDy = s.bgDy
        rot90 = s.rot90; flip = s.flip; angle = s.angle; blur = s.blur; filt = s.filt; bright = s.bright; contrast = s.contrast; sat = s.sat
        vignette = s.vignette; shade = s.shade; frame = s.frame; layers = s.layers.map { it.copyOf() }.toMutableList(); sel = s.sel; hidePhoto = s.hide
        tick++
    }
    fun canUndo() = hi > 0
    fun canRedo() = hi < hist.size - 1
    fun undo() { if (canUndo()) { hi--; restore(hist[hi]) } }
    fun redo() { if (canRedo()) { hi++; restore(hist[hi]) } }

    fun hit(nx: Float, ny: Float): Long {
        val w = fmt.w.toFloat()
        val cx = nx * w; val cy = ny * fmt.h
        for (l in layers.asReversed()) {
            val dx = cx - l.x * w; val dy = cy - l.y * fmt.h
            val a = Math.toRadians(-l.rot.toDouble())
            val rx = (dx * cos(a) - dy * sin(a)).toFloat() / l.scale
            val ry = (dx * sin(a) + dy * cos(a)).toFloat() / l.scale
            if (abs(rx) <= l.hwf * w + 24f && abs(ry) <= l.hhf * w + 24f) return l.id
        }
        return -1L
    }
}

// ---------------------------------------------------------------- فیلتر رنگ

private fun scaleM(r: Float, g: Float, b: Float, ro: Float = 0f, go: Float = 0f, bo: Float = 0f) = ColorMatrix(
    floatArrayOf(r, 0f, 0f, 0f, ro, 0f, g, 0f, 0f, go, 0f, 0f, b, 0f, bo, 0f, 0f, 0f, 1f, 0f),
)

private fun presetMatrix(f: SFilt): ColorMatrix = when (f) {
    SFilt.NONE -> ColorMatrix()
    SFilt.BW -> ColorMatrix().apply { setSaturation(0f) }
    SFilt.SEPIA -> ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    SFilt.VIVID -> ColorMatrix().apply { setSaturation(1.5f) }
    SFilt.WARM -> scaleM(1.08f, 1.0f, 0.88f, 6f, 0f, 0f)
    SFilt.COOL -> scaleM(0.9f, 1.0f, 1.12f, 0f, 0f, 6f)
    SFilt.FADE -> scaleM(0.86f, 0.86f, 0.86f, 28f, 28f, 28f)
    SFilt.DRAMA -> ColorMatrix().apply { setSaturation(0.8f); postConcat(scaleM(1.3f, 1.3f, 1.3f, -38f, -38f, -38f)) }
    SFilt.ROSE -> scaleM(1.1f, 0.95f, 1.05f, 8f, 0f, 6f)
    SFilt.MINT -> scaleM(0.95f, 1.05f, 1.02f, 0f, 6f, 6f)
}

private fun buildMatrix(st: StoryState): ColorMatrix {
    val m = ColorMatrix().apply { setSaturation(st.sat) }
    m.postConcat(presetMatrix(st.filt))
    val c = st.contrast
    val t = (-0.5f * c + 0.5f) * 255f + st.bright * 255f
    m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    return m
}

private fun shade(col: Int, f: Float): Int = android.graphics.Color.argb(
    android.graphics.Color.alpha(col),
    (android.graphics.Color.red(col) * f).toInt().coerceIn(0, 255),
    (android.graphics.Color.green(col) * f).toInt().coerceIn(0, 255),
    (android.graphics.Color.blue(col) * f).toInt().coerceIn(0, 255),
)

// ---------------------------------------------------------------- رندر

private fun drawPhoto(cv: Canvas, ph: Bitmap, st: StoryState, W: Float, H: Float, bgPaint: Paint) {
        var src: Bitmap = ph
        if (st.blur > 0.02f) {
            val f = 1f / (1f + st.blur * 28f)
            src = Bitmap.createScaledBitmap(ph, max(2, (ph.width * f).toInt()), max(2, (ph.height * f).toInt()), true)
        }
        val odd = st.rot90 % 2 != 0
        val rw = if (odd) src.height.toFloat() else src.width.toFloat()
        val rh = if (odd) src.width.toFloat() else src.height.toFloat()
        val ang = Math.toRadians(abs(st.angle).toDouble())
        val extra = (cos(ang) + sin(ang) * max(W / H, H / W)).toFloat()
        val s = max(W / rw, H / rh) * st.bgScale * extra
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            if (st.flip) postScale(-1f, 1f)
            postRotate(st.rot90 * 90f + st.angle)
            postScale(s, s)
            postTranslate(W / 2f + st.bgDx * W, H / 2f + st.bgDy * H)
        }
    cv.drawBitmap(src, m, bgPaint)
}

private fun easeOut(x: Float): Float { val a = 1f - x; return 1f - a * a * a }
private fun easeBack(x: Float): Float { val c = 1.70158f; val y = x - 1f; return 1f + (c + 1f) * y * y * y + c * y * y }

/** motion: ۰ ثابت، ۱ زوم‌این، ۲ زوم‌اوت، ۳ چپ، ۴ راست، ۵ ضربان، ۶ لرزش دست، ۷ چرخش آرام؛ tanim: ۰ هیچ، ۱ محو، ۲ بالا آمدن، ۳ پاپ، ۴ اسلاید از چپ، ۵ افتادن، ۶ زوم بزرگ */
private fun renderStory(st: StoryState, outW: Int, showSel: Boolean, fx: VFx? = null): Bitmap {
    val motion = fx?.motion ?: 0
    val p = fx?.p ?: 0f
    val tanim = fx?.tanim ?: 0
    val lyric = fx?.lyric
    val lyricAlpha = fx?.lyricAlpha ?: 1f
    val W = outW.toFloat()
    val k = W / st.fmt.w
    val H = st.fmt.h * k
    val bmp = Bitmap.createBitmap(outW, H.toInt(), Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)

    if (st.frame == SFrame.ROUND) {
        cv.drawColor(android.graphics.Color.WHITE)
        cv.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, W, H), W * 0.06f, W * 0.06f, Path.Direction.CW) })
    }

    // پس‌زمینه
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(buildMatrix(st)) }
    cv.save()
    if (motion != 0) {
        val e = p.coerceIn(0f, 1f)
        val tt = fx?.t ?: 0f
        var s = 1f; var tx = 0f; var ty = 0f; var rb = 0f
        when (motion) {
            1 -> s = 1f + 0.15f * e
            2 -> s = 1.15f - 0.15f * e
            3 -> { s = 1.12f; tx = (0.5f - e) * 0.08f * W }
            4 -> { s = 1.12f; tx = (e - 0.5f) * 0.08f * W }
            5 -> s = 1.03f + 0.03f * abs(sin(tt * Math.PI * 1.6)).toFloat()
            6 -> { s = 1.08f; tx = sin(tt * 1.7).toFloat() * 0.012f * W; ty = cos(tt * 2.3).toFloat() * 0.012f * H }
            7 -> { s = 1.14f; rb = (e - 0.5f) * 6f }
        }
        cv.scale(s, s, W / 2f, H / 2f); cv.translate(tx, ty); if (rb != 0f) cv.rotate(rb, W / 2f, H / 2f)
    }
    val ph = if (st.hidePhoto) null else (fx?.photoA ?: st.photo)
    if (ph != null) {
        drawPhoto(cv, ph, st, W, H, bgPaint)
        val pb = fx?.photoB
        if (pb != null && (fx?.mix ?: 0f) > 0f) {
            bgPaint.alpha = ((fx?.mix ?: 0f) * 255).toInt().coerceIn(0, 255)
            drawPhoto(cv, pb, st, W, H, bgPaint)
            bgPaint.alpha = 255
        }
    } else if (st.solid != null) {
        bgPaint.color = st.solid!!
        cv.drawRect(0f, 0f, W, H, bgPaint)
    } else {
        val (a, b) = SGrads[st.grad.coerceIn(0, SGrads.lastIndex)]
        bgPaint.shader = LinearGradient(0f, 0f, W * 0.6f, H, a.toInt(), b.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, W, H, bgPaint)
    }

    cv.restore()
    if (st.shade) {
        val p = Paint().apply { shader = LinearGradient(0f, H * 0.55f, 0f, H, 0x00000000, 0xA6000000.toInt(), Shader.TileMode.CLAMP) }
        cv.drawRect(0f, H * 0.55f, W, H, p)
        val p2 = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H * 0.25f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP) }
        cv.drawRect(0f, 0f, W, H * 0.25f, p2)
    }
    if (st.vignette) {
        val r = max(W, H) * 0.75f
        val p = Paint().apply { shader = RadialGradient(W / 2f, H / 2f, r, intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
        cv.drawRect(0f, 0f, W, H, p)
    }

    for ((li, l) in st.layers.withIndex()) {
        var aa = 1f; var dy = 0f; var sc = 1f; var dx = 0f
        if (tanim != 0) {
            val start = min(0.04f + li * 0.08f, 0.6f)
            val a = ((p - start) / 0.14f).coerceIn(0f, 1f)
            aa = a
            if (tanim == 2) dy = (1f - easeOut(a)) * 0.08f * H
            if (tanim == 3) sc = 0.4f + 0.6f * easeBack(a)
            if (tanim == 4) dx = -(1f - easeOut(a)) * 0.3f * W
            if (tanim == 5) dy = -(1f - easeOut(a)) * 0.1f * H
            if (tanim == 6) sc = 1f + (1f - easeOut(a)) * 1.2f
        }
        if (aa <= 0f) continue
        cv.save()
        cv.translate(l.x * W + dx, l.y * H + dy)
        cv.rotate(l.rot)
        cv.scale(l.scale * sc, l.scale * sc)
        val alpha = l.opacity * aa
        val layered = alpha < 0.99f
        if (layered) cv.saveLayerAlpha(RectF(-W * 2f, -W * 2f, W * 2f, W * 2f), (alpha * 255).toInt().coerceIn(0, 255))
        when (l.kind) {
            SKind.EMOJI -> drawEmoji(cv, l, W)
            SKind.SHAPE -> drawShape(cv, l, W)
            SKind.TEXT -> drawText(cv, l, W)
        }
        if (layered) cv.restore()
        if (showSel && l.id == st.sel) {
            val bx = RectF(-(l.hwf * W) - 12f, -(l.hhf * W) - 12f, l.hwf * W + 12f, l.hhf * W + 12f)
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 4f / max(l.scale, 0.3f) * (W / 540f)
                color = android.graphics.Color.WHITE
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                setShadowLayer(6f, 0f, 0f, 0xFF000000.toInt())
            }
            cv.drawRoundRect(bx, 16f, 16f, dash)
        }
        cv.restore()
    }

    if (lyric != null && lyricAlpha > 0f && lyric.text.isNotBlank()) {
        cv.save()
        cv.translate(lyric.x * W, (lyric.y + (fx?.lyricDy ?: 0f)) * H)
        cv.scale(lyric.scale * (fx?.lyricScale ?: 1f), lyric.scale * (fx?.lyricScale ?: 1f))
        val la = (lyricAlpha * lyric.opacity)
        if (la < 0.99f) cv.saveLayerAlpha(RectF(-W * 2f, -W * 2f, W * 2f, W * 2f), (la * 255).toInt().coerceIn(0, 255))
        drawText(cv, lyric, W)
        if (la < 0.99f) cv.restore()
        cv.restore()
    }
    when (st.frame) {
        SFrame.WHITE -> {
            val p = Paint().apply { style = Paint.Style.STROKE; color = android.graphics.Color.WHITE; strokeWidth = W * 0.05f }
            cv.drawRect(W * 0.025f, W * 0.025f, W - W * 0.025f, H - W * 0.025f, p)
        }
        SFrame.POLAROID -> {
            val p = Paint().apply { color = android.graphics.Color.WHITE }
            val s = W * 0.045f
            cv.drawRect(0f, 0f, W, s, p); cv.drawRect(0f, 0f, s, H, p); cv.drawRect(W - s, 0f, W, H, p)
            cv.drawRect(0f, H - W * 0.18f, W, H, p)
        }
        else -> {}
    }
    if (fx != null) {
        if (fx.progressBar) {
            val pp = Paint().apply { color = 0xE6FFFFFF.toInt() }
            cv.drawRect(0f, H - W * 0.009f, W * fx.p.coerceIn(0f, 1f), H, pp)
        }
        if (fx.fadeBlack > 0f) cv.drawColor(android.graphics.Color.argb((fx.fadeBlack * 255).toInt().coerceIn(0, 255), 0, 0, 0))
    }
    return bmp
}

private fun drawEmoji(cv: Canvas, l: SLayer, W: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = l.size * W * 1.8f; textAlign = Paint.Align.CENTER }
    val fm = p.fontMetrics
    cv.drawText(l.text, 0f, -(fm.ascent + fm.descent) / 2f, p)
    l.hwf = p.measureText(l.text) / 2f / W
    l.hhf = (fm.descent - fm.ascent) / 2f / W
}

private fun drawShape(cv: Canvas, l: SLayer, W: Float) {
    val hw = l.hwf * W; val hh = l.hhf * W
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = l.color
        if (l.grad) shader = LinearGradient(-hw, -hh, hw, hh, l.color, l.color2, Shader.TileMode.CLAMP)
    }
    when (l.shape) {
        SShape.RECT -> cv.drawRect(-hw, -hh, hw, hh, p)
        SShape.ROUND -> cv.drawRoundRect(RectF(-hw, -hh, hw, hh), min(hw, hh) * 0.35f, min(hw, hh) * 0.35f, p)
        SShape.CIRCLE -> cv.drawOval(RectF(-hw, -hh, hw, hh), p)
        SShape.LINE -> cv.drawRoundRect(RectF(-hw, -hh, hw, hh), hh, hh, p)
        SShape.HEART -> {
            val path = Path().apply {
                moveTo(0f, hh * 0.95f)
                cubicTo(-hw * 1.35f, hh * 0.1f, -hw * 0.75f, -hh * 1.15f, 0f, -hh * 0.4f)
                cubicTo(hw * 0.75f, -hh * 1.15f, hw * 1.35f, hh * 0.1f, 0f, hh * 0.95f)
                close()
            }
            cv.drawPath(path, p)
        }
        SShape.STAR -> {
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 1f else 0.45f
                val a = Math.toRadians(-90.0 + i * 36.0)
                val px = (hw * r * cos(a)).toFloat(); val py = (hh * r * sin(a)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            cv.drawPath(path, p)
        }
    }
}

private fun drawText(cv: Canvas, l: SLayer, W: Float) {
    val text = l.text.ifBlank { "متن" }
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = l.size * W; color = l.color; typeface = StoryFonts.get(l.font); letterSpacing = l.spacing
    }
    fun build(w: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, w).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0f, l.lineMul).build()
    var layout = build((W * 0.84f).toInt())
    var lineMax = 0f
    for (i in 0 until layout.lineCount) lineMax = max(lineMax, layout.getLineWidth(i))
    val boxW = lineMax.toInt().coerceAtLeast(10)
    layout = build(boxW)
    val hh = layout.height / 2f
    val pad = if (l.style == TStyle.PILL) W * 0.03f else 0f
    l.hwf = (boxW / 2f + pad) / W
    l.hhf = (hh + pad * 0.7f) / W

    if (l.style == TStyle.PILL) {
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.bg }
        cv.drawRoundRect(RectF(-boxW / 2f - pad, -hh - pad * 0.7f, boxW / 2f + pad, hh + pad * 0.7f), W * 0.035f, W * 0.035f, bp)
    }
    cv.save()
    cv.translate(-boxW / 2f, -hh)

    fun fillWith(col: Int, useGrad: Boolean) {
        tp.style = Paint.Style.FILL; tp.color = col; tp.clearShadowLayer()
        tp.shader = if (useGrad) LinearGradient(0f, 0f, 0f, layout.height.toFloat(), l.color, l.color2, Shader.TileMode.CLAMP) else null
    }

    when (l.style) {
        TStyle.EXTRUDE, TStyle.LONG -> {
            val steps = if (l.style == TStyle.EXTRUDE) 16 else 44
            val d = W * (if (l.style == TStyle.EXTRUDE) 0.0042f else 0.0032f)
            val dc = shade(l.color, if (l.style == TStyle.EXTRUDE) 0.5f else 0.28f)
            fillWith(dc, false)
            for (i in steps downTo 1) { cv.save(); cv.translate(i * d, i * d); layout.draw(cv); cv.restore() }
            fillWith(l.color, l.grad); layout.draw(cv)
        }
        TStyle.GLITCH -> {
            val d = W * 0.008f
            fillWith(0xCC00E5FF.toInt(), false); cv.save(); cv.translate(-d, 0f); layout.draw(cv); cv.restore()
            fillWith(0xCCFF2D6F.toInt(), false); cv.save(); cv.translate(d, 0f); layout.draw(cv); cv.restore()
            fillWith(l.color, l.grad); layout.draw(cv)
        }
        TStyle.OUTLINE -> {
            val lum = android.graphics.Color.red(l.color) * 0.3 + android.graphics.Color.green(l.color) * 0.59 + android.graphics.Color.blue(l.color) * 0.11
            tp.clearShadowLayer(); tp.shader = null
            tp.style = Paint.Style.STROKE; tp.strokeWidth = max(1f, l.strokeW * W); tp.strokeJoin = Paint.Join.ROUND
            tp.color = if (l.strokeColor != 0) l.strokeColor else if (lum > 140) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            layout.draw(cv)
            fillWith(l.color, l.grad); layout.draw(cv)
        }
        TStyle.NEON -> {
            fillWith(l.color, false); tp.setShadowLayer(W * 0.03f, 0f, 0f, l.color)
            layout.draw(cv); layout.draw(cv)
        }
        TStyle.SHADOW -> {
            fillWith(l.color, l.grad); tp.setShadowLayer(W * 0.012f, 0f, W * 0.006f, 0xB0000000.toInt())
            layout.draw(cv)
        }
        else -> { fillWith(l.color, l.grad); layout.draw(cv) }
    }
    cv.restore()
}

// ---------------------------------------------------------------- قالب‌های آماده

private fun applyTemplate(st: StoryState, n: Int) {
    st.act {
        layers.clear(); sel = -1L
        shade = false; vignette = false; frame = SFrame.NONE; filt = SFilt.NONE; bright = 0f; contrast = 1f; sat = 1f; solid = null
        fun t(text: String, x: Float, y: Float, size: Float, color: Int = 0xFFFFFFFF.toInt(), style: TStyle = TStyle.SHADOW, bg: Int = 0xCC000000.toInt(), font: TFont = TFont.VAZIR_BOLD) {
            layers.add(newLayer(SKind.TEXT, text).also { it.x = x; it.y = y; it.size = size; it.color = color; it.style = style; it.bg = bg; it.font = font })
        }
        fun e(text: String, x: Float, y: Float, size: Float) {
            layers.add(newLayer(SKind.EMOJI, text).also { it.x = x; it.y = y; it.size = size })
        }
        when (n) {
            0 -> { if (photo == null) grad = 3; shade = photo != null
                e("❝", 0.5f, 0.3f, 0.12f); t("زندگی همین حالاست", 0.5f, 0.5f, 0.09f, font = TFont.ESTEDAD); t("— نام شما", 0.5f, 0.66f, 0.045f, 0xFFFFD23F.toInt(), TStyle.PLAIN, font = TFont.VAZIR) }
            1 -> { if (photo == null) grad = 1; shade = photo != null
                t("تخفیف ویژه", 0.5f, 0.3f, 0.11f, font = TFont.ESTEDAD); t("۵۰٪", 0.5f, 0.47f, 0.28f, 0xFFFFF176.toInt(), TStyle.EXTRUDE, font = TFont.VAZIR_BLACK)
                t("فقط تا پایان هفته", 0.5f, 0.66f, 0.05f, 0xFF111111.toInt(), TStyle.PILL, 0xFFFFFFFF.toInt()); e("🔥", 0.82f, 0.18f, 0.1f) }
            2 -> { if (photo == null) grad = 4; shade = false
                e("🎂", 0.5f, 0.28f, 0.14f); t("تولدت مبارک!", 0.5f, 0.5f, 0.11f, 0xFF7B2D8E.toInt(), TStyle.SHADOW, font = TFont.ESTEDAD)
                t("بهترین‌ها رو برات آرزو می‌کنم", 0.5f, 0.62f, 0.05f, 0xFF444444.toInt(), TStyle.PLAIN, font = TFont.VAZIR); e("🎈", 0.2f, 0.16f, 0.09f); e("🎉", 0.8f, 0.8f, 0.09f) }
            3 -> { if (photo == null) grad = 0; shade = photo != null
                t("محصول جدید", 0.5f, 0.12f, 0.055f, 0xFF7C6CFF.toInt(), TStyle.PILL, 0xFFFFFFFF.toInt()); t("اسم محصول", 0.5f, 0.45f, 0.11f, font = TFont.VAZIR_BLACK, style = TStyle.LONG)
                t("توضیح کوتاه و جذاب درباره‌ی محصول", 0.5f, 0.58f, 0.05f, style = TStyle.SHADOW, font = TFont.VAZIR)
                t("سفارش از طریق دایرکت", 0.5f, 0.85f, 0.05f, 0xFF111111.toInt(), TStyle.PILL, 0xFFFFD23F.toInt()) }
            4 -> { if (photo == null) grad = 2; shade = photo != null
                e("☀️", 0.5f, 0.26f, 0.14f); t("صبح بخیر", 0.5f, 0.5f, 0.12f, font = TFont.ESTEDAD); t("امروز روز توست", 0.5f, 0.62f, 0.055f, style = TStyle.SHADOW, font = TFont.VAZIR) }
            else -> { if (photo == null) grad = 6; shade = photo != null
                e("📢", 0.5f, 0.3f, 0.14f); t("اطلاعیه", 0.5f, 0.45f, 0.12f, style = TStyle.GLITCH, font = TFont.VAZIR_BLACK); t("متن اطلاعیه را اینجا بنویس", 0.5f, 0.6f, 0.06f, style = TStyle.SHADOW, font = TFont.VAZIR) }
        }
        sel = layers.firstOrNull()?.id ?: -1L
    }
}

private val TemplateNames = listOf("نقل‌قول", "تخفیف ویژه", "تولد", "معرفی محصول", "صبح بخیر", "اطلاعیه")

// ---------------------------------------------------------------- صفحه

@Composable
fun StoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    StoryFonts.assets = ctx.assets
    val st = remember { StoryState().also { it.commit() } }
    val tk = st.tick
    var tab by remember { mutableStateOf(STab.TEMPLATE) }
    var newText by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    val box = remember { floatArrayOf(1f, 1f) }
    var motion by remember { mutableStateOf(1) }
    var tanim by remember { mutableStateOf(2) }
    var lanim by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(15f) }
    var quality by remember { mutableStateOf(1) }
    var fps by remember { mutableStateOf(30) }
    var vout by remember { mutableStateOf(VOut.MP4) }
    var kbps by remember { mutableStateOf(192) }
    var fadeBlack by remember { mutableStateOf(true) }
    var pbar by remember { mutableStateOf(false) }
    var music by remember { mutableStateOf<android.net.Uri?>(null) }
    var musicName by remember { mutableStateOf("") }
    var musicDurSec by remember { mutableStateOf(0f) }
    var musicStart by remember { mutableStateOf(0f) }
    var mgain by remember { mutableStateOf(1f) }
    var mFadeIn by remember { mutableStateOf(0f) }
    var mFadeOut by remember { mutableStateOf(1.5f) }
    var pv by remember { mutableStateOf(-1f) }
    val lyrics = remember { mutableStateListOf<Lyr>() }
    var lyricOn by remember { mutableStateOf(true) }
    var lyricBusy by remember { mutableStateOf(false) }
    val lyricStyle = remember { SLayer(-5L, SKind.TEXT, "").also { it.x = 0.5f; it.y = 0.4f; it.size = 0.1f; it.font = TFont.RUQAA; it.style = TStyle.SHADOW; it.color = 0xFFE9E9E9.toInt(); it.opacity = 0.92f } }
    var lyricTick by remember { mutableStateOf(0) }
    var vprog by remember { mutableStateOf(-1) }
    val extra = remember { mutableStateListOf<Bitmap>() }
    var vresult by remember { mutableStateOf<File?>(null) }
    var vkind by remember { mutableStateOf(VOut.MP4) }

    fun runLyrics(u: android.net.Uri, startSec: Float) {
        scope.launch {
            lyricBusy = true
            try {
                val s = (startSec * 1_000_000L).toLong()
                val pcm = withContext(Dispatchers.Default) { StoryVideo.decodePcm16k(ctx, u, s + 125_000_000L, s) }
                if (pcm == null) toast(ctx, "خواندن فایل صوتی ممکن نشد")
                else {
                    val segs = Ai.transcribeTimed(pcmToWav(pcm, 16000), null)
                    lyrics.clear(); lyrics.addAll(splitLyrics(segs)); lyricTick++
                    if (segs.isEmpty()) toast(ctx, "متنی در آهنگ پیدا نشد")
                }
            } catch (e: Exception) { toast(ctx, e.message ?: "تشخیص متن آهنگ ناموفق بود") }
            lyricBusy = false
        }
    }
    val pickMusic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            music = u
            lyrics.clear(); musicStart = 0f
            musicName = try { ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cu -> if (cu.moveToFirst()) cu.getString(0) else null } } catch (_: Exception) { null } ?: "فایل صوتی"
            scope.launch { musicDurSec = (withContext(Dispatchers.IO) { MediaTrim.info(ctx, u)?.durMs } ?: 0L) / 1000f }
            runLyrics(u, 0f)
        }
    }
    val pickPhotos = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        scope.launch { for (u in uris) { val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, u, 1280) }; if (b != null) extra.add(b) } }
    }
    fun allPhotos(): List<Bitmap> = listOfNotNull(st.photo) + extra
    fun cfg(): VCfg = VCfg(motion, tanim, lanim, dur, lyrics.toList(), lyricOn, lyricStyle, allPhotos(), fadeBlack, pbar)
    val preview = remember(tk, pv, lyricTick) { if (pv >= 0f) renderStory(st, 360, false, makeFx(cfg(), pv)) else renderStory(st, 540, true) }

    fun startExport() {
        if (!canExport()) return
        if (vout != VOut.MP4 && music == null) { toast(ctx, "برای خروجی صوتی اول یک موزیک انتخاب کن"); return }
        if (vprog >= 0) return
        val cf = cfg(); val d = dur; val q = quality; val fp = fps; val kind = vout; val mu = music
        val ms = musicStart; val mg = mgain; val mfi = mFadeIn; val mfo = mFadeOut; val kb = kbps
        scope.launch {
            vprog = 0; st.sel = -1L; vresult = null
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith("story_out_") }?.forEach { it.delete() }
            val out = File(dir, "story_out_${System.currentTimeMillis()}.${kind.ext}")
            val ok = withContext(Dispatchers.Default) {
                val tmp = ArrayList<File>()
                try {
                    val durUs = (d * 1_000_000L).toLong()
                    val pcm: MediaTrim.Pcm? = if (mu != null) {
                        val offUs = (ms * 1_000_000L).toLong()
                        MediaTrim.decodeRange(ctx, mu, offUs, offUs + durUs) {}?.also { MediaTrim.applyFx(it, mg, mfi, mfo) }
                    } else null
                    when (kind) {
                        VOut.MP3 -> pcm != null && Mp3Enc.encode(pcm, out, kb) { vprog = it }
                        VOut.WAV -> if (pcm == null) false else { MediaTrim.writeWav(pcm, out); true }
                        VOut.M4A -> pcm != null && MediaTrim.encodeAac(pcm, out) { vprog = it }
                        VOut.MP4 -> {
                            val wide = st.fmt.w > st.fmt.h
                            val w = (if (wide) listOf(960, 1280, 1920) else listOf(540, 720, 1080))[q]
                            val h = (w.toLong() * st.fmt.h / st.fmt.w).toInt() / 2 * 2
                            val frames = (d * fp).toInt().coerceAtLeast(2)
                            val v = File(ctx.cacheDir, "story_v_${System.currentTimeMillis()}.mp4").also { tmp.add(it) }
                            StoryVideo.bitrate = (w.toLong() * h * 3).toInt().coerceIn(2_000_000, 10_000_000)
                            val vok = try {
                                StoryVideo.encodeVideo(v, w, h, fp, frames, { vprog = it }) { i -> renderStory(st, w, false, makeFx(cf, i.toFloat() / (frames - 1).coerceAtLeast(1))) }
                            } finally { StoryVideo.bitrate = 0 }
                            if (!vok) false
                            else {
                                var aUri: android.net.Uri? = null
                                if (pcm != null) {
                                    val a = File(ctx.cacheDir, "story_a_${System.currentTimeMillis()}.m4a").also { tmp.add(it) }
                                    if (MediaTrim.encodeAac(pcm, a) {}) aUri = android.net.Uri.fromFile(a)
                                }
                                vprog = 95
                                StoryVideo.mux(ctx, v, aUri, durUs, out)
                            }
                        }
                    }
                } catch (e: Throwable) { false } finally { tmp.forEach { it.delete() } }
            }
            vprog = -1
            if (ok && out.exists() && out.length() > 0) {
                vresult = out; vkind = kind
                val saved = withContext(Dispatchers.IO) { saveFileToGallery(ctx, out, "AIHub_story_${System.currentTimeMillis()}.${kind.ext}", kind.mime) }
                toast(ctx, if (saved) (if (kind == VOut.MP4) "در گالری ذخیره شد (Movies/AIHub)" else "در پوشه‌ی Music/AIHub ذخیره شد") else "ساخته شد ولی ذخیره نشد؛ از دکمه‌ی اشتراک استفاده کن")
            } else { out.delete(); toast(ctx, "ساخت فایل ناموفق بود. مدت کوتاه‌تر یا کیفیت پایین‌تر را امتحان کن") }
        }
    }
    val c = MaterialTheme.colorScheme
    val cur = st.selected()

    ToolScaffold("استودیوی استوری", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipRow(SFmt.values().toList(), st.fmt, { it.label }) { f -> st.act { fmt = f } }
            Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MediaBar(Modifier.weight(1f), onImage = { b -> st.act { photo = b; hidePhoto = false; bgScale = 1f; bgDx = 0f; bgDy = 0f; rot90 = 0; flip = false; angle = 0f; blur = 0f } })
            }
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ st.undo() }, Modifier.weight(1f), enabled = st.canUndo(), contentPadding = PaddingValues(6.dp)) { Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("برگشت (قدم قبل)", fontSize = 12.sp, maxLines = 1) }
                OutlinedButton({ st.redo() }, Modifier.weight(1f), enabled = st.canRedo(), contentPadding = PaddingValues(6.dp)) { Icon(Icons.AutoMirrored.Rounded.Redo, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("جلو", fontSize = 12.sp, maxLines = 1) }
            }

            val ratio = st.fmt.w.toFloat() / st.fmt.h
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = preview.asImageBitmap(), contentDescription = null,
                    modifier = Modifier
                        .then(if (ratio < 1f) Modifier.fillMaxWidth(0.62f) else Modifier.fillMaxWidth(0.92f))
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(14.dp))
                        .onSizeChanged { box[0] = it.width.toFloat().coerceAtLeast(1f); box[1] = it.height.toFloat().coerceAtLeast(1f) }
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = { off ->
                                val id = st.hit(off.x / box[0], off.y / box[1])
                                if (id != st.sel) st.change { sel = id }
                                st.selected()?.let { s -> if (tab != STab.VIDEO && tab != STab.LAYERS) tab = if (s.kind == SKind.TEXT) STab.TEXT else STab.STICKER }
                                tryAwaitRelease()
                                if (st.dirty) st.commit()
                            })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                val l = st.selected()
                                st.change {
                                    dirty = true
                                    if (l != null) {
                                        l.x += pan.x / box[0]; l.y += pan.y / box[1]
                                        l.scale = (l.scale * zoom).coerceIn(0.2f, 6f); l.rot += rotation
                                    } else if (photo != null) {
                                        bgDx += pan.x / box[0]; bgDy += pan.y / box[1]
                                        bgScale = (bgScale * zoom).coerceIn(1f, 5f)
                                    }
                                }
                            }
                        },
                )
            }
            Text(
                if (cur != null) "با یک انگشت جابه‌جا کن؛ با دو انگشت بزرگ و بچرخان. برای برداشتن انتخاب، روی جای خالی بزن."
                else "روی متن یا استیکر بزن تا انتخاب شود. بدون انتخاب، با انگشت عکس پس‌زمینه را جابه‌جا و زوم کن.",
                Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f),
            )

            if (cur != null) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cur.kind == SKind.TEXT) OutlinedButton({ tab = STab.TEXT; custom = false }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("ویرایش متن", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton({ st.act { layers.removeAll { it.id == cur.id }; sel = -1L } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("حذف", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton({ st.change { sel = -1L } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("لغو انتخاب", fontSize = 12.sp, maxLines = 1) }
                }
            }
            ChipRow(STab.values().toList(), tab, { it.label }) { tab = it; custom = false }
            when (tab) {
                STab.TEMPLATE -> {
                    ChipRow((0..5).toList(), -1, { TemplateNames[it] }) { applyTemplate(st, it) }
                    Text("قالب را بزن و بعد متن‌ها را با انتخاب و ویرایش، مال خودت کن.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.65f))
                }
                STab.BG -> {
                    Text("رنگ پس‌زمینه (وقتی عکس نداری)", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SGrads.forEachIndexed { i, (a, b) ->
                            Box(
                                Modifier.size(46.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(a.toInt()), Color(b.toInt()))))
                                    .clickable { st.act { grad = i; solid = null; if (photo != null) hidePhoto = true } },
                            )
                        }
                    }
                    Text("یا رنگ ساده", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    Swatches(st.solid ?: 0) { col -> st.act { solid = col; if (photo != null) hidePhoto = true } }
                    ColorPicker(st.solid ?: 0xFF7C6CFF.toInt(), { col -> st.change { solid = col; if (photo != null) hidePhoto = true } }, { st.commit() })
                    if (st.photo != null && st.hidePhoto) {
                        Text("عکس فعلاً پنهان است (رنگ پس‌زمینه دیده می‌شود).", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
                        GradButton("نمایش دوباره‌ی عکس", icon = Icons.Rounded.Image, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { st.act { hidePhoto = false } })
                    } else if (st.photo != null) {
                        Text("اگر رنگی انتخاب کنی عکس پنهان می‌شود و هر وقت خواستی با «نمایش دوباره‌ی عکس» برمی‌گردد.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f))
                    }
                    if (st.photo != null) {
                        SectionTitle("ویرایش عکس")
                        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton({ st.act { rot90 = (rot90 + 1) % 4 } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Rounded.RotateRight, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("بچرخان", fontSize = 12.sp, maxLines = 1) }
                            OutlinedButton({ st.act { flip = !flip } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Rounded.Flip, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("آینه", fontSize = 12.sp, maxLines = 1) }
                            OutlinedButton({ st.act { photo = null; hidePhoto = false } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("حذف عکس", fontSize = 12.sp, maxLines = 1) }
                        }
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("کج‌بودن عکس", fontSize = 13.sp)
                            Slider(st.angle, { v -> st.change { angle = v } }, valueRange = -30f..30f, onValueChangeFinished = { st.commit() })
                            Text("محو کردن عکس (بلور)", fontSize = 13.sp)
                            Slider(st.blur, { v -> st.change { blur = v } }, valueRange = 0f..1f, onValueChangeFinished = { st.commit() })
                            Text("زوم عکس", fontSize = 13.sp)
                            Slider(st.bgScale, { v -> st.change { bgScale = v } }, valueRange = 1f..4f, onValueChangeFinished = { st.commit() })
                        }
                    }
                }
                STab.TEXT -> {
                    AppField(newText, { newText = it }, "متن جدید", Modifier.padding(horizontal = 16.dp), minLines = 1, maxLines = 3)
                    GradButton(
                        "افزودن متن", icon = Icons.Rounded.TextFields, enabled = newText.isNotBlank(),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        onClick = {
                            st.act { val l = newLayer(SKind.TEXT, newText.trim()); layers.add(l); sel = l.id }
                            newText = ""
                        },
                    )
                    if (cur != null && cur.kind == SKind.TEXT) {
                        AppField(cur.text, { v -> st.change { cur.text = v } }, "ویرایش متن انتخاب‌شده", Modifier.padding(horizontal = 16.dp), minLines = 1, maxLines = 4)
                        SectionTitle("جلوه‌ی متن"); ChipRow(TStyle.values().toList(), cur.style, { it.label }) { s -> st.act { cur.style = s } }
                        SectionTitle("فونت"); ChipRow(TFont.values().toList(), cur.font, { it.label }) { f -> st.act { cur.font = f } }
                        SectionTitle("رنگ متن"); Swatches(cur.color) { col -> st.act { cur.color = col } }
                        ChipRow(listOf(false, true), custom, { if (it) "رنگ دلخواه ▲" else "رنگ دلخواه ▼" }) { custom = it }
                        if (custom) ColorPicker(cur.color, { col -> st.change { cur.color = col } }, { st.commit() })
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Switch(cur.grad, { v -> st.act { cur.grad = v } }); Spacer(Modifier.width(10.dp)); Text("تدریج رنگ (رنگ اول → دوم)", fontSize = 13.sp)
                        }
                        if (cur.grad) { SectionTitle("رنگ دوم"); Swatches(cur.color2) { col -> st.act { cur.color2 = col } } }
                        if (cur.style == TStyle.PILL) {
                            SectionTitle("رنگ نوار"); Swatches(cur.bg) { col -> st.act { cur.bg = (col and 0x00FFFFFF) or 0xE6000000.toInt() } }
                        }
                        if (cur.style == TStyle.OUTLINE) {
                            SectionTitle("رنگ دورخط"); Swatches(cur.strokeColor) { col -> st.act { cur.strokeColor = col } }
                            Column(Modifier.padding(horizontal = 20.dp)) {
                                Text("ضخامت دورخط", fontSize = 13.sp)
                                Slider(cur.strokeW, { v -> st.change { cur.strokeW = v } }, valueRange = 0.004f..0.04f, onValueChangeFinished = { st.commit() })
                            }
                        }
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("اندازه", fontSize = 13.sp)
                            Slider(cur.size, { v -> st.change { cur.size = v } }, valueRange = 0.03f..0.3f, onValueChangeFinished = { st.commit() })
                            Text("فاصله‌ی حروف", fontSize = 13.sp)
                            Slider(cur.spacing, { v -> st.change { cur.spacing = v } }, valueRange = -0.05f..0.4f, onValueChangeFinished = { st.commit() })
                            Text("فاصله‌ی خطوط", fontSize = 13.sp)
                            Slider(cur.lineMul, { v -> st.change { cur.lineMul = v } }, valueRange = 0.7f..1.8f, onValueChangeFinished = { st.commit() })
                            Text("شفافیت", fontSize = 13.sp)
                            Slider(cur.opacity, { v -> st.change { cur.opacity = v } }, valueRange = 0.1f..1f, onValueChangeFinished = { st.commit() })
                        }
                    }
                    if (cur != null) LayerActions(st, cur)
                }
                STab.STICKER -> {
                    SEmoji.chunked(8).forEach { row ->
                        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { em ->
                                Text(
                                    em, fontSize = 26.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                        st.act { val l = newLayer(SKind.EMOJI, em).also { it.size = 0.12f }; layers.add(l); sel = l.id }
                                    }.padding(horizontal = 5.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    SectionTitle("شکل‌ها")
                    ChipRow(SShape.values().toList(), SShape.RECT, { it.label }) { sh ->
                        st.act {
                            val l = newLayer(SKind.SHAPE, "").also {
                                it.shape = sh; it.color = 0xFFFFD23F.toInt()
                                when (sh) {
                                    SShape.LINE -> { it.hwf = 0.25f; it.hhf = 0.008f }
                                    SShape.RECT, SShape.ROUND -> { it.hwf = 0.3f; it.hhf = 0.12f }
                                    else -> { it.hwf = 0.15f; it.hhf = 0.15f }
                                }
                            }
                            layers.add(l); sel = l.id
                        }
                    }
                    if (cur != null && cur.kind == SKind.EMOJI) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("اندازه", fontSize = 13.sp)
                            Slider(cur.size, { v -> st.change { cur.size = v } }, valueRange = 0.04f..0.4f, onValueChangeFinished = { st.commit() })
                            Text("شفافیت", fontSize = 13.sp)
                            Slider(cur.opacity, { v -> st.change { cur.opacity = v } }, valueRange = 0.1f..1f, onValueChangeFinished = { st.commit() })
                        }
                    }
                    if (cur != null && cur.kind == SKind.SHAPE) {
                        SectionTitle("رنگ شکل"); Swatches(cur.color) { col -> st.act { cur.color = col } }
                        ChipRow(listOf(false, true), custom, { if (it) "رنگ دلخواه ▲" else "رنگ دلخواه ▼" }) { custom = it }
                        if (custom) ColorPicker(cur.color, { col -> st.change { cur.color = col } }, { st.commit() })
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Switch(cur.grad, { v -> st.act { cur.grad = v } }); Spacer(Modifier.width(10.dp)); Text("تدریج رنگ", fontSize = 13.sp)
                        }
                        if (cur.grad) { SectionTitle("رنگ دوم"); Swatches(cur.color2) { col -> st.act { cur.color2 = col } } }
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("عرض", fontSize = 13.sp)
                            Slider(cur.hwf, { v -> st.change { cur.hwf = v } }, valueRange = 0.02f..0.6f, onValueChangeFinished = { st.commit() })
                            Text("ارتفاع", fontSize = 13.sp)
                            Slider(cur.hhf, { v -> st.change { cur.hhf = v } }, valueRange = 0.004f..0.6f, onValueChangeFinished = { st.commit() })
                            Text("شفافیت", fontSize = 13.sp)
                            Slider(cur.opacity, { v -> st.change { cur.opacity = v } }, valueRange = 0.1f..1f, onValueChangeFinished = { st.commit() })
                        }
                    }
                    if (cur != null) LayerActions(st, cur)
                }
                STab.FILTER -> {
                    ChipRow(SFilt.values().toList(), st.filt, { it.label }) { f -> st.act { filt = f } }
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("روشنایی", fontSize = 13.sp); Slider(st.bright, { v -> st.change { bright = v } }, valueRange = -0.3f..0.3f, onValueChangeFinished = { st.commit() })
                        Text("کنتراست", fontSize = 13.sp); Slider(st.contrast, { v -> st.change { contrast = v } }, valueRange = 0.6f..1.6f, onValueChangeFinished = { st.commit() })
                        Text("اشباع رنگ", fontSize = 13.sp); Slider(st.sat, { v -> st.change { sat = v } }, valueRange = 0f..2f, onValueChangeFinished = { st.commit() })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(st.vignette, { v -> st.act { vignette = v } }); Spacer(Modifier.width(10.dp)); Text("حاشیه‌ی تیره (وینیت)", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(st.shade, { v -> st.act { shade = v } }); Spacer(Modifier.width(10.dp)); Text("سایه‌ی بالا و پایین (برای خوانایی متن)", fontSize = 13.sp)
                        }
                    }
                }
                STab.FRAME -> ChipRow(SFrame.values().toList(), st.frame, { it.label }) { f -> st.act { frame = f } }
                STab.VIDEO -> {
                    SectionTitle("عکس‌ها (اسلایدشو)")
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ pickPhotos.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("افزودن چند عکس", maxLines = 1) }
                        if (extra.isNotEmpty()) OutlinedButton({ extra.clear() }) { Text("پاک کردن") }
                    }
                    Text(
                        if (extra.isEmpty()) "با چند عکس، ویدیو به‌صورت اسلایدشو ساخته می‌شود و عکس‌ها نرم جابه‌جا می‌شوند."
                        else "${allPhotos().size} عکس؛ مدت ویدیو بین عکس‌ها مساوی تقسیم می‌شود.",
                        Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f),
                    )

                    SectionTitle("حرکت و انیمیشن")
                    Text("حرکت پس‌زمینه", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    ChipRow((0..7).toList(), motion, { listOf("ثابت", "زوم‌این", "زوم‌اوت", "حرکت به چپ", "حرکت به راست", "ضربان", "لرزش دست", "چرخش آرام")[it] }) { motion = it }
                    Text("انیمیشن ورود متن و استیکرها", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    ChipRow((0..6).toList(), tanim, { listOf("بدون انیمیشن", "محو", "بالا آمدن", "پاپ", "اسلاید از چپ", "افتادن از بالا", "زوم بزرگ")[it] }) { tanim = it }
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(fadeBlack, { fadeBlack = it }); Spacer(Modifier.width(10.dp)); Text("محو شدن ابتدا و انتهای ویدیو", fontSize = 13.sp)
                    }
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(pbar, { pbar = it }); Spacer(Modifier.width(10.dp)); Text("نوار پیشرفت پایین ویدیو", fontSize = 13.sp)
                    }

                    SectionTitle("مدت و کیفیت")
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("مدت ویدیو: ${dur.toInt()} ثانیه", fontSize = 13.sp)
                        Slider(dur, { dur = it }, valueRange = 3f..120f)
                    }
                    ChipRow(listOf(15, 30, 60, 90, 120), dur.toInt(), { "$it ثانیه" }) { dur = it.toFloat() }
                    Text("کیفیت ویدیو", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    ChipRow(listOf(0, 1, 2), quality, { listOf("کم (۵۴۰)", "خوب (۷۲۰)", "عالی (۱۰۸۰)")[it] }) { quality = it }
                    ChipRow(listOf(24, 30), fps, { "$it فریم" }) { fps = it }
                    run {
                        val fr = dur * fps
                        val sec = fr * listOf(0.03f, 0.055f, 0.13f)[quality] * (if (allPhotos().size > 1 || lyrics.isNotEmpty()) 1.25f else 1f)
                        Text("تخمین زمان ساخت: " + (if (sec < 60f) "کمتر از ۱ دقیقه" else "حدود ${(sec / 60f).toInt() + 1} دقیقه") + " (بسته به سرعت گوشی). هنگام ساخت برنامه را باز نگه دار.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f))
                    }

                    SectionTitle("موزیک")
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ pickMusic.launch(arrayOf("audio/*")) }, Modifier.weight(1f)) { Icon(Icons.Rounded.MusicNote, null); Spacer(Modifier.width(6.dp)); Text(if (music == null) "انتخاب موزیک" else "تغییر موزیک", maxLines = 1) }
                        if (music != null) OutlinedButton({ music = null; musicName = ""; lyrics.clear(); lyricTick++ }) { Text("حذف") }
                    }
                    if (music != null) {
                        Text("🎵 " + musicName, Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, maxLines = 1)
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            if (musicDurSec > 6f) {
                                Text("شروع موزیک از ثانیه ${musicStart.toInt()} (از ${musicDurSec.toInt()} ثانیه)", fontSize = 13.sp)
                                Slider(musicStart, { musicStart = it }, valueRange = 0f..(musicDurSec - 3f))
                            }
                            Text("بلندی موزیک: ${(mgain * 100).toInt()}٪", fontSize = 13.sp)
                            Slider(mgain, { mgain = it }, valueRange = 0.2f..2f)
                            Text("محو شدن ابتدای موزیک: ${"%.1f".format(mFadeIn)} ثانیه", fontSize = 13.sp)
                            Slider(mFadeIn, { mFadeIn = it }, valueRange = 0f..5f)
                            Text("محو شدن انتهای موزیک: ${"%.1f".format(mFadeOut)} ثانیه", fontSize = 13.sp)
                            Slider(mFadeOut, { mFadeOut = it }, valueRange = 0f..8f)
                        }
                        SectionTitle("متن آهنگ روی ویدیو")
                        if (lyricBusy) Text("در حال خواندن متن آهنگ…", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Switch(lyricOn, { lyricOn = it }); Spacer(Modifier.width(10.dp)); Text("نمایش متن آهنگ همزمان با موزیک", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            { music?.let { runLyrics(it, musicStart) } }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = !lyricBusy,
                        ) { Text(if (musicStart > 0f) "خواندن متن آهنگ از ثانیه‌ی ${musicStart.toInt()}" else "خواندن دوباره‌ی متن آهنگ", maxLines = 1) }
                        if (lyricOn && lyrics.isNotEmpty()) {
                            Text("نحوه‌ی ظاهر شدن متن", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                            ChipRow((0..3).toList(), lanim, { listOf("محو", "پاپ", "بالا آمدن", "تایپ")[it] }) { lanim = it }
                            Text("خط‌ها را اگر اشتباه شنیده شده، ویرایش کن:", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
                            lyrics.withIndex().filter { it.value.start < dur }.take(40).forEach { (i, ly) ->
                                AppField(ly.text, { v -> lyrics[i] = Lyr(ly.start, ly.end, v); lyricTick++ }, "ثانیه ${ly.start.toInt()} تا ${ly.end.toInt()}", Modifier.padding(horizontal = 16.dp), minLines = 1, maxLines = 3)
                            }
                            SectionTitle("جلوه‌ی متن آهنگ"); ChipRow(listOf(TStyle.SHADOW, TStyle.PLAIN, TStyle.NEON, TStyle.OUTLINE, TStyle.PILL, TStyle.EXTRUDE, TStyle.GLITCH), lyricStyle.style, { it.label }) { s -> lyricStyle.style = s; lyricTick++ }
                            SectionTitle("فونت"); ChipRow(TFont.values().toList(), lyricStyle.font, { it.label }) { f -> lyricStyle.font = f; lyricTick++ }
                            SectionTitle("رنگ"); Swatches(lyricStyle.color) { col -> lyricStyle.color = col; lyricTick++ }
                            Column(Modifier.padding(horizontal = 20.dp)) {
                                Text("اندازه", fontSize = 13.sp); Slider(lyricStyle.size, { v -> lyricStyle.size = v; lyricTick++ }, valueRange = 0.035f..0.14f)
                                Text("جای متن (بالا تا پایین)", fontSize = 13.sp); Slider(lyricStyle.y, { v -> lyricStyle.y = v; lyricTick++ }, valueRange = 0.12f..0.9f)
                                Text("شفافیت", fontSize = 13.sp); Slider(lyricStyle.opacity, { v -> lyricStyle.opacity = v; lyricTick++ }, valueRange = 0.3f..1f)
                            }
                        } else if (!lyricBusy && lyricOn) Text("متنی برای نمایش نیست (آهنگ بی‌کلام یا تشخیص ناموفق).", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
                    }

                    SectionTitle("فرمت خروجی")
                    ChipRow(VOut.values().toList(), vout, { it.label }) { vout = it }
                    if (vout == VOut.MP3) { Text("کیفیت MP3", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); ChipRow(listOf(128, 192, 256, 320), kbps, { it.toString() }) { kbps = it } }
                    if (vout != VOut.MP4) Text("خروجی صوتی همان موزیک بریده‌شده با بلندی و محو شدن تنظیم‌شده است.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f))

                    OutlinedButton(
                        onClick = {
                            if (pv < 0f) scope.launch {
                                val t0 = System.currentTimeMillis(); val total = (minOf(dur, 20f) * 1000).toLong()
                                st.sel = -1L
                                while (true) {
                                    val el = System.currentTimeMillis() - t0
                                    if (el >= total) break
                                    pv = el.toFloat() / total * (minOf(dur, 20f) / dur)
                                    kotlinx.coroutines.delay(40)
                                }
                                pv = -1f
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("پیش‌نمایش (۲۰ ثانیه‌ی اول)") }
                    GradButton(
                        if (vprog >= 0) "در حال ساخت… ${vprog}٪" else "ساخت و ذخیره (${vout.label})", icon = Icons.Rounded.Movie,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = vprog < 0, onClick = { startExport() },
                    )
                    if (vprog >= 0) LinearProgressIndicator(progress = { vprog / 100f }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())
                    val vr = vresult
                    if (vr != null && vprog < 0) OutlinedButton(
                        onClick = {
                            if (canExport()) try {
                                val fu = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", vr)
                                val si = Intent(Intent.ACTION_SEND).apply { type = vkind.mime; putExtra(Intent.EXTRA_STREAM, fu); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                ctx.startActivity(Intent.createChooser(si, "اشتراک‌گذاری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) { toast(ctx, "اشتراک‌گذاری ممکن نشد") }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری آخرین خروجی") }
                }
                STab.LAYERS -> {
                    if (st.layers.isEmpty()) Text("هنوز لایه‌ای نیست.", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    st.layers.asReversed().forEach { l ->
                        Row(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (l.id == st.sel) c.primary.copy(alpha = 0.18f) else c.surface.copy(alpha = 0.5f))
                                .clickable { st.change { sel = l.id } }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                when (l.kind) { SKind.TEXT -> "Aa  " + l.text.take(22); SKind.EMOJI -> l.text + "  استیکر"; SKind.SHAPE -> "▣  " + l.shape.label },
                                Modifier.weight(1f), fontSize = 14.sp, maxLines = 1,
                            )
                            IconButton({ st.act { val i = layers.indexOf(l); if (i < layers.lastIndex) { layers.removeAt(i); layers.add(i + 1, l) } } }) { Icon(Icons.Rounded.KeyboardArrowUp, "بالاتر") }
                            IconButton({ st.act { val i = layers.indexOf(l); if (i > 0) { layers.removeAt(i); layers.add(i - 1, l) } } }) { Icon(Icons.Rounded.KeyboardArrowDown, "پایین‌تر") }
                            IconButton({ st.act { layers.remove(l); if (sel == l.id) sel = -1L } }) { Icon(Icons.Rounded.Delete, "حذف") }
                        }
                    }
                }
            }

            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradButton(
                    "ذخیره در گالری", icon = Icons.Rounded.Download, modifier = Modifier.weight(1f),
                    onClick = {
                        if (canExport()) scope.launch {
                            val b = withContext(Dispatchers.Default) { renderStory(st, st.fmt.w, false) }
                            toast(ctx, if (saveBitmapToGallery(ctx, b)) "در گالری ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
                OutlinedButton(
                    onClick = {
                        if (canExport()) scope.launch {
                            val b = withContext(Dispatchers.Default) { renderStory(st, st.fmt.w, false) }
                            try {
                                val f = withContext(Dispatchers.IO) {
                                    File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "story_share.png").also { out -> out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                                }
                                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                                val i = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                ctx.startActivity(Intent.createChooser(i, "اشتراک‌گذاری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) { toast(ctx, "اشتراک‌گذاری ممکن نشد") }
                        }
                    },
                    modifier = Modifier.weight(0.8f).heightIn(min = 52.dp),
                ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک", maxLines = 1) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Swatches(current: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SColors.forEach { col ->
            val on = current != 0 && (col and 0x00FFFFFF) == (current and 0x00FFFFFF)
            Box(
                Modifier.size(if (on) 40.dp else 34.dp).clip(CircleShape).background(Color(col)).clickable { onPick(col) },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Rounded.Check, null, tint = if (col == SColors[0]) Color.Black else Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ColorPicker(current: Int, onPick: (Int) -> Unit, onDone: () -> Unit) {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(current or 0xFF000000.toInt(), it) }
    fun pick(h: Float, s: Float, v: Float) = onPick(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    Column(Modifier.padding(horizontal = 20.dp)) {
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }),
            ),
        )
        Slider(hsv[0], { pick(it, max(hsv[1], 0.5f), max(hsv[2], 0.6f)) }, valueRange = 0f..360f, onValueChangeFinished = onDone)
        Text("پررنگی رنگ", fontSize = 12.sp)
        Slider(hsv[1], { pick(hsv[0], it, hsv[2]) }, valueRange = 0f..1f, onValueChangeFinished = onDone)
        Text("روشنی رنگ", fontSize = 12.sp)
        Slider(hsv[2], { pick(hsv[0], hsv[1], it) }, valueRange = 0f..1f, onValueChangeFinished = onDone)
    }
}

@Composable
private fun LayerActions(st: StoryState, l: SLayer) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({
            st.act {
                val d = l.copyOf().let { o -> SLayer(newLayer(o.kind, o.text).id, o.kind, o.text).also { n ->
                    n.x = (l.x + 0.06f).coerceAtMost(0.95f); n.y = (l.y + 0.05f).coerceAtMost(0.95f); n.scale = l.scale; n.rot = l.rot
                    n.color = l.color; n.color2 = l.color2; n.grad = l.grad; n.bg = l.bg; n.style = l.style; n.font = l.font; n.shape = l.shape
                    n.size = l.size; n.strokeW = l.strokeW; n.strokeColor = l.strokeColor; n.opacity = l.opacity; n.spacing = l.spacing; n.lineMul = l.lineMul
                    n.hwf = l.hwf; n.hhf = l.hhf
                } }
                layers.add(d); sel = d.id
            }
        }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("تکرار", maxLines = 1, fontSize = 12.sp) }
        OutlinedButton({ st.act { layers.remove(l); layers.add(l) } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("بیار جلو", maxLines = 1, fontSize = 12.sp) }
        OutlinedButton({ st.act { layers.remove(l); sel = -1L } }, Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("حذف", maxLines = 1, fontSize = 12.sp) }
    }
}
