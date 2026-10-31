package com.example.aihub.screens

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Canvas as ACanvas

// ---------------------------------------------------------------- مدل

internal class CharCfg(
    var name: String = "کاراکتر من",
    var species: String = "human",
    var skin: Int = 0xFFF2C9A0.toInt(), var body: Int = 0xFF4F7CFF.toInt(), var pants: Int = 0xFF2D3A6B.toInt(),
    var hair: Int = 0xFF3B2A1A.toInt(), var eyes: Int = 0xFF3A2A1A.toInt(), var shoes: Int = 0xFF2B2B2B.toInt(),
    var bg1: Int = 0xFF8FD3F4.toInt(), var bg2: Int = 0xFF84FAB0.toInt(),
    var hairStyle: String = "short", var accessory: String = "none", var ears: String = "none",
    var shape: String = "normal", var beard: String = "none", var pose: String = "stand",
    var face: Bitmap? = null,
) {
    fun copy2() = CharCfg(name, species, skin, body, pants, hair, eyes, shoes, bg1, bg2, hairStyle, accessory, ears, shape, beard, pose, face)
}

private val C3_SPECIES = listOf("human" to "انسان", "robot" to "ربات", "cat" to "گربه", "dog" to "سگ", "bear" to "خرس", "bunny" to "خرگوش", "alien" to "فضایی", "monster" to "هیولا")
private val C3_HAIR = listOf("none" to "بدون مو", "short" to "کوتاه", "long" to "بلند", "spiky" to "سیخی", "bun" to "گیس‌گلوله", "cap" to "کلاه کپ", "hat" to "کلاه جشن")
private val C3_ACC = listOf("none" to "هیچ", "glasses" to "عینک", "shades" to "عینک آفتابی", "headphones" to "هدفون", "crown" to "تاج", "horns" to "شاخ", "antenna" to "آنتن", "wings" to "بال", "tail" to "دم", "bowtie" to "پاپیون", "scarf" to "شال‌گردن")
private val C3_EARS = listOf("none" to "پیش‌فرض", "cat" to "گربه‌ای", "bunny" to "خرگوشی", "bear" to "خرسی", "dog" to "آویزان")
private val C3_SHAPE = listOf("slim" to "لاغر", "normal" to "معمولی", "round" to "گرد")
private val C3_BEARD = listOf("none" to "بدون ریش", "mustache" to "سبیل", "full" to "ریش")
private val C3_POSE = listOf("stand" to "ایستاده", "wave" to "سلام", "cheer" to "شادی", "dance" to "رقص")

private fun c3(h: Long): Int = (0xFF000000 or h).toInt()

private val C3_PRESETS: List<Pair<String, CharCfg>> = listOf(
    "پسر" to CharCfg("پسر شاد", "human", c3(0xF2C9A0), c3(0x4F7CFF), c3(0x2D3A6B), c3(0x3B2A1A), c3(0x3A2A1A), c3(0xFFFFFF), c3(0x8FD3F4), c3(0x84FAB0), "short", "none"),
    "دختر" to CharCfg("دختر شیرین", "human", c3(0xF5D0B5), c3(0xFF6FA8), c3(0x6B4EA8), c3(0x5A2E1F), c3(0x2A5B3A), c3(0xFF8FB8), c3(0xFBC2EB), c3(0xA6C1EE), "long", "none"),
    "گربه" to CharCfg("گربه‌ی ملوس", "cat", c3(0xF2A65A), c3(0xFFD166), c3(0x355070), c3(0x8A4B1F), c3(0x2E7D32), c3(0x2B2B2B), c3(0xFFE29F), c3(0xFFA99F), "none", "none", "cat"),
    "ربات" to CharCfg("ربات هوشمند", "robot", c3(0xC9D3E3), c3(0x5B8DEF), c3(0x4A5568), c3(0x4A5568), c3(0x00E5FF), c3(0x2D3748), c3(0x0F2027), c3(0x2C5364), "none", "antenna"),
    "فضایی" to CharCfg("فضایی بامزه", "alien", c3(0x7CE07C), c3(0x8E54E9), c3(0x3B2A6B), c3(0x3B2A6B), c3(0x111111), c3(0x222222), c3(0x41295A), c3(0x2F0743), "none", "antenna"),
    "خرس" to CharCfg("خرس مهربان", "bear", c3(0xA9744F), c3(0xEF476F), c3(0x264653), c3(0x5C3A21), c3(0x111111), c3(0x2B2B2B), c3(0xFFEFD5), c3(0xF6D365), "none", "bowtie", "bear"),
    "خرگوش" to CharCfg("خرگوش پشمالو", "bunny", c3(0xF5F5F5), c3(0x7ED6DF), c3(0x686DE0), c3(0xDDDDDD), c3(0xD6336C), c3(0xFF9EB5), c3(0xFDE2E4), c3(0xBDE0FE), "none", "none", "bunny"),
    "هیولا" to CharCfg("هیولای خوشگل", "monster", c3(0x9B6CFF), c3(0xFFB703), c3(0x023047), c3(0x5A2E9B), c3(0xFFFF00), c3(0x111111), c3(0x232526), c3(0x414345), "none", "horns"),
)

internal const val C3_SYSTEM =
    "You design cute 3D toy-style characters. Reply with ONLY one JSON object (no markdown, no comments) with these keys: " +
        "\"name\" (short Persian name), \"species\": one of human, robot, cat, dog, bear, bunny, alien, monster; " +
        "\"skin\": hex #RRGGBB (skin tone or fur color), \"body\": hex shirt color, \"pants\": hex pants color, \"hair\": hex hair color, " +
        "\"eyes\": hex eye color, \"shoes\": hex shoe color, \"hairStyle\": one of none, short, long, spiky, bun, cap, hat, " +
        "\"accessory\": one of none, glasses, shades, headphones, crown, horns, antenna, wings, tail, bowtie, scarf, " +
        "\"ears\": one of none, cat, bunny, bear, dog, \"shape\": one of slim, normal, round, \"beard\": one of none, mustache, full, " +
        "\"pose\": one of stand, wave, cheer, dance, \"bg1\" and \"bg2\": two hex colors for a pleasant gradient background. " +
        "If a photo of a person is given, make a cartoon version of that person: match skin tone, hair color and style, beard, glasses and clothes colors. " +
        "Otherwise follow the user's description. Animals use fur color in skin and the matching ears. Robots use species robot."

private fun c3Apply(base: CharCfg, txt: String): CharCfg? {
    val clean = txt.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val o = try { JSONObject(clean) } catch (e: Exception) { return null }
    val n = base.copy2()
    o.optString("name").takeIf { it.isNotBlank() }?.let { n.name = it.take(24) }
    fun pick(k: String, allowed: List<Pair<String, String>>, set: (String) -> Unit) { val v = o.optString(k).lowercase(); if (allowed.any { it.first == v }) set(v) }
    fun col(k: String, set: (Int) -> Unit) { val v = o.optString(k); if (Regex("#[0-9a-fA-F]{6}").matches(v)) set(android.graphics.Color.parseColor(v)) }
    pick("species", C3_SPECIES) { n.species = it }
    pick("hairStyle", C3_HAIR) { n.hairStyle = it }
    pick("accessory", C3_ACC) { n.accessory = it }
    pick("ears", C3_EARS) { n.ears = it }
    pick("shape", C3_SHAPE) { n.shape = it }
    pick("beard", C3_BEARD) { n.beard = it }
    pick("pose", C3_POSE) { n.pose = it }
    col("skin") { n.skin = it }; col("body") { n.body = it }; col("pants") { n.pants = it }; col("hair") { n.hair = it }
    col("eyes") { n.eyes = it }; col("shoes") { n.shoes = it }; col("bg1") { n.bg1 = it }; col("bg2") { n.bg2 = it }
    return n
}

/** چهره را از عکس پیدا و برش می‌دهد و رنگ پوست و مو را هم برمی‌دارد: (چهره، پوست، مو) */
private fun c3FaceFromPhoto(src: Bitmap): Triple<Bitmap, Int, Int> {
    val sc = min(1f, 480f / kotlin.math.max(src.width, src.height))
    val w = ((src.width * sc).toInt() / 2 * 2).coerceAtLeast(64)
    val h = (src.height * sc).toInt().coerceAtLeast(64)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    var cx = w / 2f; var cyy = h * 0.42f; var size = min(w, h) * 0.9f
    try {
        val b565 = small.copy(Bitmap.Config.RGB_565, false)
        val faces = arrayOfNulls<android.media.FaceDetector.Face>(1)
        val n = android.media.FaceDetector(w, h, 1).findFaces(b565, faces)
        val f = faces[0]
        if (n > 0 && f != null) {
            val mp = android.graphics.PointF(); f.getMidPoint(mp)
            val ed = f.eyesDistance()
            cx = mp.x; cyy = mp.y + ed * 0.25f; size = ed * 3.4f
        }
    } catch (e: Exception) {}
    val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val m = android.graphics.Matrix().apply { postTranslate(-(cx - size / 2f), -(cyy - size / 2f)); postScale(512f / size, 512f / size) }
    ACanvas(out).drawBitmap(small, m, Paint(Paint.FILTER_BITMAP_FLAG))
    fun avg(px: Int, py: Int, r: Int): Int {
        var rs = 0; var gs = 0; var bs = 0; var k = 0
        for (yy in (py - r)..(py + r)) for (xx in (px - r)..(px + r)) {
            if (xx in 0 until w && yy in 0 until h) { val c = small.getPixel(xx, yy); rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255; k++ }
        }
        return if (k == 0) 0xFFF2C9A0.toInt() else (255 shl 24) or ((rs / k) shl 16) or ((gs / k) shl 8) or (bs / k)
    }
    val r = kotlin.math.max(2, (size * 0.04f).toInt())
    val skin = avg(cx.toInt(), (cyy + size * 0.03f).toInt(), r)
    val hair = avg(cx.toInt(), (cyy - size * 0.43f).toInt(), r)
    return Triple(out, skin, hair)
}

private fun c3Json(c: CharCfg): String {
    fun h(i: Int) = "#%06X".format(i and 0xFFFFFF)
    return JSONObject().apply {
        put("name", c.name); put("species", c.species); put("skin", h(c.skin)); put("body", h(c.body)); put("pants", h(c.pants))
        put("hair", h(c.hair)); put("eyes", h(c.eyes)); put("shoes", h(c.shoes)); put("hairStyle", c.hairStyle); put("accessory", c.accessory)
        put("ears", c.ears); put("shape", c.shape); put("beard", c.beard); put("pose", c.pose); put("bg1", h(c.bg1)); put("bg2", h(c.bg2))
    }.toString()
}

// ---------------------------------------------------------------- رندر سه‌بعدی (کاملاً داخل برنامه، بدون وب)

private const val WHITE = -1
private const val BLACK = -16777216
private val PINK = c3(0xFF9EB5)

private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((((a shr s) and 255) * (1f - t) + ((b shr s) and 255) * t).toInt()).coerceIn(0, 255)
    return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private class C3Cmd(val depth: Float, val draw: () -> Unit)
private class C3Pt(val sx: Float, val sy: Float, val s: Float, val depth: Float, val facing: Float)

private class C3Scene(val cv: ACanvas, val W: Float, val H: Float, val unit: Float, val gy: Float, yawDeg: Float) {
    val cy: Float = cos(Math.toRadians(yawDeg.toDouble())).toFloat()
    val sy: Float = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
    val cmds = ArrayList<C3Cmd>()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun scr(x: Float, y: Float, z: Float): FloatArray {
        val rx = x * cy + z * sy
        val rz = -x * sy + z * cy
        val s = 9f / (9f - rz)
        return floatArrayOf(W / 2f + rx * s * unit, gy - y * s * unit, s, rz)
    }

    fun drawBallNow(px: Float, py: Float, hx: Float, hy: Float, color: Int, shadeAmt: Float) {
        cv.save(); cv.translate(px, py); cv.scale(hx / 100f, hy / 100f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(-35f, -42f, 170f, intArrayOf(mix(color, WHITE, 0.4f), color, mix(color, BLACK, shadeAmt)), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(0f, 0f, 100f, paint)
        paint.shader = null
        cv.restore()
    }

    fun rad(ax: Float, az: Float, s: Float) = sqrt((ax * cy) * (ax * cy) + (az * sy) * (az * sy)) * s * unit

    fun ball(x: Float, y: Float, z: Float, ax: Float, ay: Float, az: Float, color: Int, shadeAmt: Float = 0.45f, bias: Float = 0f) {
        val p = scr(x, y, z)
        val hx = rad(ax, az, p[2]); val hy = ay * p[2] * unit
        cmds.add(C3Cmd(p[3] + bias) { drawBallNow(p[0], p[1], hx, hy, color, shadeAmt) })
    }

    fun limb(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r: Float, color: Int, n: Int = 6) {
        for (i in 0..n) {
            val f = i / n.toFloat()
            ball(ax + (bx - ax) * f, ay + (by - ay) * f, az + (bz - az) * f, r, r, r, color)
        }
    }

    fun cone(bx: Float, by: Float, bz: Float, r: Float, tx: Float, ty: Float, tz: Float, color: Int) {
        val pts = ArrayList<FloatArray>()
        for (i in 0 until 12) {
            val a = (i * 2.0 * Math.PI / 12.0)
            pts.add(scr(bx + r * cos(a).toFloat(), by, bz + r * sin(a).toFloat()))
        }
        val apex = scr(tx, ty, tz)
        val c0 = scr(bx, by, bz)
        cmds.add(C3Cmd((c0[3] + apex[3]) / 2f) {
            val path = Path()
            for (i in 0 until 12) {
                val a = pts[i]; val b = pts[(i + 1) % 12]
                path.moveTo(apex[0], apex[1]); path.lineTo(a[0], a[1]); path.lineTo(b[0], b[1]); path.close()
            }
            var mn = pts[0][0]; var mx = pts[0][0]
            for (p in pts) { mn = min(mn, p[0]); mx = kotlin.math.max(mx, p[0]) }
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(mn, 0f, mx + 1f, 0f, intArrayOf(mix(color, WHITE, 0.3f), color, mix(color, BLACK, 0.45f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            cv.drawPath(path, paint)
            paint.shader = null
        })
    }

    fun surf(cx: Float, cyy: Float, cz: Float, ax: Float, ay: Float, az: Float, h: Float, v: Float): C3Pt {
        val nx = sin(h) * cos(v); val ny = sin(v); val nz = cos(h) * cos(v)
        val p = scr(cx + nx * ax, cyy + ny * ay, cz + nz * az)
        return C3Pt(p[0], p[1], p[2], p[3], -nx * sy + nz * cy)
    }

    fun flush() { cmds.sortBy { it.depth }; for (c in cmds) c.draw() }
}

private fun c3Eye(sc: C3Scene, pt: C3Pt, w: Float, h: Float, pupil: Int, open: Float, glow: Boolean) {
    if (pt.facing < 0.08f) return
    sc.cmds.add(C3Cmd(pt.depth + 0.03f) {
        val cv = sc.cv; val ps = sc.paint
        cv.save(); cv.translate(pt.sx, pt.sy)
        val ew = w * pt.s * sc.unit * (0.4f + 0.6f * pt.facing)
        val eh = h * pt.s * sc.unit * open
        ps.style = Paint.Style.FILL; ps.shader = null
        if (!glow) { ps.color = WHITE; cv.drawOval(RectF(-ew, -eh, ew, eh), ps) }
        val pw = ew * (if (glow) 0.9f else 0.62f); val ph = min(eh * 0.9f, pw * 1.25f)
        ps.color = pupil; cv.drawOval(RectF(-pw, -ph, pw, ph), ps)
        if (!glow) {
            ps.color = BLACK; cv.drawOval(RectF(-pw * 0.5f, -ph * 0.5f, pw * 0.5f, ph * 0.5f), ps)
            ps.color = WHITE; cv.drawCircle(-pw * 0.3f, -ph * 0.38f, pw * 0.26f, ps)
        }
        cv.restore()
    })
}

private fun buildCharacter(s: C3Scene, c: CharCfg, t: Float) {
    val sp = c.species
    val robot = sp == "robot"; val alien = sp == "alien"; val monster = sp == "monster"
    val animal = sp == "cat" || sp == "dog" || sp == "bear" || sp == "bunny"
    val bw = when (c.shape) { "slim" -> 0.62f; "round" -> 0.92f; else -> 0.76f }
    val dance = c.pose == "dance"
    val bob = 0.03f * sin(t * 2.4f) + (if (dance) 0.06f * abs(sin(t * 5f)) else 0f)
    val sway = if (dance) 0.1f * sin(t * 5f) else 0f
    val skin = c.skin
    val gray = c3(0x9AA5B5)
    val lowerC = c.pants

    // پا و کفش
    for (sg in listOf(-1f, 1f)) {
        s.limb(sg * 0.27f, 1.1f, 0f, sg * 0.27f, 0.22f, 0.02f, 0.17f, if (robot) gray else lowerC)
        s.ball(sg * 0.27f, 0.11f, 0.14f, 0.21f, 0.11f, 0.32f, c.shoes)
    }
    // دم
    val tail = c.accessory == "tail" || sp == "cat" || sp == "dog" || sp == "bunny"
    if (tail) {
        val wag = sin(t * 4f) * 0.25f
        when (sp) {
            "bunny" -> s.ball(sway, 1.2f, -0.55f, 0.22f, 0.22f, 0.22f, c3(0xFFFFFF))
            else -> for (i in 0..6) { val f = i / 6f; s.ball(sway + wag * f * f, 1.15f + f * 0.9f, -0.5f - 0.15f * f, 0.12f - 0.03f * f, 0.12f - 0.03f * f, 0.12f - 0.03f * f, skin) }
        }
    }
    // بال
    if (c.accessory == "wings") {
        val flap = sin(t * 5f) * 0.12f
        for (sg in listOf(-1f, 1f)) for (i in 0..6) {
            val f = i / 6f
            s.ball(sg * (bw * 0.7f + f * 0.9f), 1.8f + bob + f * 0.9f + flap * f, -0.42f, 0.2f - 0.05f * f, 0.26f - 0.06f * f, 0.07f, c3(0xFFFFFF), 0.25f)
        }
    }
    // تنه
    s.ball(sway, 1.18f + bob * 0.3f, 0f, bw * 0.92f, 0.4f, bw * 0.62f, lowerC)
    s.ball(sway, 1.58f + bob, 0f, bw, 0.66f, bw * 0.72f, c.body)
    if (robot) s.ball(sway, 1.58f + bob, bw * 0.4f, bw * 0.4f, 0.3f, bw * 0.5f, c3(0x222B36), 0.3f, 0.5f)
    if (c.accessory == "scarf") for (i in 0 until 9) {
        val a = i * 2.0 * Math.PI / 9.0
        s.ball(sway + 0.5f * cos(a).toFloat(), 2.02f + bob, 0.5f * sin(a).toFloat(), 0.2f, 0.17f, 0.2f, c3(0xE63946))
    }
    s.ball(sway, 2.05f + bob, 0f, 0.2f, 0.16f, 0.2f, skin)
    if (c.accessory == "bowtie") {
        s.cone(sway - 0.02f, 1.98f + bob, bw * 0.55f, 0.16f, sway - 0.3f, 1.98f + bob, bw * 0.55f, c3(0xE63946))
        s.cone(sway + 0.02f, 1.98f + bob, bw * 0.55f, 0.16f, sway + 0.3f, 1.98f + bob, bw * 0.55f, c3(0xE63946))
        s.ball(sway, 1.98f + bob, bw * 0.58f, 0.09f, 0.09f, 0.09f, c3(0xB5222E))
    }

    // دست‌ها
    for (sg in listOf(-1f, 1f)) {
        val shx = sway + sg * bw * 0.95f; val shy = 1.88f + bob
        var ex = sway + sg * (bw + 0.12f); var ey = 1.45f + bob; var ez = 0.05f
        var hx = sway + sg * (bw + 0.16f); var hy = 1.02f + bob + 0.04f * sin(t * 2f + sg); var hz = 0.14f
        when (c.pose) {
            "wave" -> if (sg > 0) { ex = sway + bw + 0.35f; ey = 2.15f + bob; hx = sway + bw + 0.55f + 0.2f * sin(t * 9f); hy = 2.7f + bob; hz = 0.1f }
            "cheer" -> { ex = sway + sg * (bw + 0.35f); ey = 2.4f + bob; ez = 0.05f; hx = sway + sg * (bw + 0.5f); hy = 3.0f + bob + 0.1f * sin(t * 6f + sg); hz = 0.05f }
            "dance" -> { val ph = if (sg > 0) 0f else 3.14f; ex = sway + sg * (bw + 0.4f); ey = 1.7f + bob; hx = sway + sg * (bw + 0.7f); hy = 1.9f + 0.8f * sin(t * 5f + ph) + bob; hz = 0.1f }
        }
        s.limb(shx, shy, 0f, ex, ey, ez, 0.16f, c.body, 4)
        s.limb(ex, ey, ez, hx, hy, hz, 0.13f, if (robot) gray else skin, 4)
        s.ball(hx, hy, hz, 0.17f, 0.17f, 0.17f, if (robot) gray else skin)
    }

    // سر
    val hx = sway; val hy = 2.68f + bob; val hr = 0.74f
    val ax = if (robot) hr * 1.08f else hr; val ay = if (robot) hr * 0.92f else hr * 0.95f; val az = if (robot) hr * 0.95f else hr * 0.95f
    s.ball(hx, hy, 0f, ax, ay, az, skin, 0.4f)
    val head = s.scr(hx, hy, 0f)
    val faceMode = c.face != null
    val headDepth = head[3]
    val hrPx = hr * head[2] * s.unit

    // گوش‌ها
    val earType = if (c.ears != "none") c.ears else when (sp) { "cat" -> "cat"; "dog" -> "dog"; "bear" -> "bear"; "bunny" -> "bunny"; else -> "none" }
    for (sg in listOf(-1f, 1f)) {
        when (earType) {
            "cat" -> {
                s.cone(hx + sg * 0.5f, hy + hr * 0.72f, 0f, 0.27f, hx + sg * 0.64f, hy + hr * 1.5f, -0.02f, skin)
                s.cone(hx + sg * 0.5f, hy + hr * 0.76f, 0.06f, 0.15f, hx + sg * 0.6f, hy + hr * 1.3f, 0.04f, PINK)
            }
            "bunny" -> {
                val sw = 0.05f * sin(t * 2f + sg)
                s.ball(hx + sg * 0.32f + sw, hy + hr * 1.6f, -0.02f, 0.2f, 0.64f, 0.12f, skin)
                s.ball(hx + sg * 0.32f + sw, hy + hr * 1.6f, 0.06f, 0.11f, 0.46f, 0.05f, PINK, 0.3f, 0.02f)
            }
            "bear" -> {
                s.ball(hx + sg * 0.58f, hy + hr * 0.8f, -0.05f, 0.25f, 0.25f, 0.2f, skin)
                s.ball(hx + sg * 0.58f, hy + hr * 0.8f, 0.05f, 0.13f, 0.13f, 0.08f, PINK, 0.3f, 0.02f)
            }
            "dog" -> s.ball(hx + sg * 0.8f, hy - 0.02f, 0f, 0.2f, 0.46f, 0.28f, mix(skin, BLACK, 0.28f))
            else -> if (robot) s.ball(hx + sg * 0.8f, hy, 0f, 0.1f, 0.2f, 0.2f, gray)
            else s.ball(hx + sg * 0.72f, hy - 0.02f, 0f, 0.12f, 0.17f, 0.1f, skin)
        }
    }

    // موی سر
    val hair = c.hair
    val hasHair = !robot && !alien && c.hairStyle != "none"
    if (hasHair) {
        val style = c.hairStyle
        val back = (-s.cy + 0.15f).coerceIn(0f, 1f)
        val hp = s.scr(hx, hy + 0.08f, -0.1f)
        val sx = s.rad(hr * 1.1f, hr * 1.08f, hp[2]); val syy = hr * 1.06f * hp[2] * s.unit
        val capColor = if (style == "cap") c3(0xE63946) else hair
        s.cmds.add(C3Cmd(headDepth + 0.02f) {
            s.cv.save()
            s.cv.clipRect(0f, 0f, s.W, if (faceMode) head[1] - 1.7f * hrPx + back * 3.2f * hrPx else head[1] - 0.22f * hrPx + back * 1.4f * hrPx)
            s.drawBallNow(hp[0], hp[1], sx, syy, capColor, 0.45f)
            s.cv.restore()
        })
        if (style == "long") {
            s.ball(hx, hy - 0.8f, -0.5f, hr * 1.02f, 1.35f, 0.55f, hair, 0.5f)
        }
        if (style == "bun") s.ball(hx, hy + hr * 1.1f, -0.1f, 0.3f, 0.3f, 0.3f, hair)
        if (style == "spiky") for (i in 0..4) {
            val a = (-0.9 + i * 0.45).toFloat()
            s.cone(hx + 0.5f * sin(a), hy + hr * 0.8f, 0.5f * cos(a) * 0.4f, 0.2f, hx + 0.75f * sin(a), hy + hr * 1.55f, 0.15f * cos(a), hair)
        }
        if (style == "cap") s.ball(hx, hy + hr * 0.22f, hr * 0.85f, hr * 0.75f, 0.07f, hr * 0.6f, c3(0xB5222E), 0.4f, 0.4f)
        if (style == "hat") s.cone(hx, hy + hr * 0.82f, 0f, hr * 0.6f, hx + 0.1f, hy + hr * 2.0f, 0f, c3(0xFFB703))
        if (style == "hat") s.ball(hx + 0.1f, hy + hr * 2.0f, 0f, 0.11f, 0.11f, 0.11f, c3(0xE63946))
    }

    // صورت: پوزه
    if (animal && !faceMode) s.ball(hx, hy - 0.25f * hr, hr * 0.78f, hr * 0.4f, hr * 0.3f, hr * 0.3f, mix(skin, WHITE, 0.55f), 0.3f, 0.08f)

    // چهره‌ی واقعی از روی عکس روی سر
    val fb = c.face
    if (fb != null) {
        s.cmds.add(C3Cmd(headDepth + 0.025f) {
            val front = s.cy
            if (front > 0.12f) {
                val hx0 = s.rad(ax, az, head[2]); val hy0 = ay * head[2] * s.unit
                val a = ((front - 0.12f) / 0.35f).coerceIn(0f, 1f)
                val cv = s.cv; val ps = s.paint
                cv.save()
                val oval = RectF(head[0] - hx0, head[1] - hy0, head[0] + hx0, head[1] + hy0)
                cv.clipPath(Path().apply { addOval(oval, Path.Direction.CW) })
                val tw = hx0 * 1.75f * front.coerceAtLeast(0.3f); val th = hy0 * 1.9f
                val cxT = head[0] + s.sy * hx0 * 0.95f; val cyT = head[1] - hy0 * 0.02f
                ps.shader = null; ps.style = Paint.Style.FILL; ps.alpha = (a * 255).toInt(); ps.isFilterBitmap = true
                cv.drawBitmap(fb, null, RectF(cxT - tw / 2f, cyT - th / 2f, cxT + tw / 2f, cyT + th / 2f), ps)
                ps.alpha = 255
                ps.shader = RadialGradient(head[0] - hx0 * 0.3f, head[1] - hy0 * 0.35f, kotlin.math.max(hx0, hy0) * 1.5f, intArrayOf(0x00000000, 0x00000000, 0x55000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                cv.drawOval(oval, ps)
                ps.shader = null
                cv.restore()
            }
        })
    }

    // چشم‌ها
    if (!faceMode) {
    val blink = if ((t % 3.6f) < 0.14f) 0.12f else 1f
    val bigEye = alien || monster
    for (sg in listOf(-1f, 1f)) {
        val pt = s.surf(hx, hy, 0f, ax, ay, az, sg * (if (bigEye) 0.5f else 0.42f), 0.08f)
        if (robot) c3Eye(s, pt, 0.18f, 0.16f, c.eyes, blink, true)
        else c3Eye(s, pt, if (bigEye) 0.24f else 0.17f, if (bigEye) 0.3f else 0.2f, c.eyes, blink, false)
        // گونه
        if (!robot && !alien) {
            val ch = s.surf(hx, hy, 0f, ax, ay, az, sg * 0.78f, -0.22f)
            if (ch.facing > 0.1f) s.cmds.add(C3Cmd(ch.depth + 0.02f) {
                s.paint.style = Paint.Style.FILL; s.paint.shader = null; s.paint.color = 0x66FF6F91
                val w = 0.14f * ch.s * s.unit * (0.4f + ch.facing); val h = 0.09f * ch.s * s.unit
                s.cv.drawOval(RectF(ch.sx - w, ch.sy - h, ch.sx + w, ch.sy + h), s.paint)
            })
        }
    }
    // بینی
    val nose = s.surf(hx, hy, 0f, ax * (if (animal) 1.18f else 1.02f), ay, az * (if (animal) 1.18f else 1.02f), 0f, if (animal) -0.14f else -0.12f)
    if (nose.facing > 0.1f) s.cmds.add(C3Cmd(nose.depth + 0.04f) {
        s.paint.style = Paint.Style.FILL; s.paint.shader = null
        s.paint.color = if (animal) c3(0x3A2A2A) else mix(skin, BLACK, 0.12f)
        val w = (if (animal) 0.11f else 0.07f) * nose.s * s.unit * (0.5f + nose.facing); val h = (if (animal) 0.08f else 0.06f) * nose.s * s.unit
        s.cv.drawOval(RectF(nose.sx - w, nose.sy - h, nose.sx + w, nose.sy + h), s.paint)
    })
    // دهان
    val mL = s.surf(hx, hy, 0f, ax, ay, az, -0.3f, -0.38f)
    val mM = s.surf(hx, hy, 0f, ax, ay, az, 0f, -0.52f)
    val mR = s.surf(hx, hy, 0f, ax, ay, az, 0.3f, -0.38f)
    if (mM.facing > 0.1f && !robot) {
        val open = c.pose == "cheer" || monster || (c.pose == "dance" && sin(t * 5f) > 0f)
        s.cmds.add(C3Cmd(mM.depth + 0.04f) {
            val ps = s.paint; ps.shader = null
            if (open) {
                val w = (mR.sx - mL.sx) * 0.55f; val h = 0.16f * mM.s * s.unit
                ps.style = Paint.Style.FILL; ps.color = c3(0x5A1E1E)
                s.cv.drawOval(RectF(mM.sx - w, mM.sy - h * 0.6f, mM.sx + w, mM.sy + h), ps)
                ps.color = PINK; s.cv.drawOval(RectF(mM.sx - w * 0.5f, mM.sy + h * 0.2f, mM.sx + w * 0.5f, mM.sy + h), ps)
                if (monster) { ps.color = WHITE; s.cv.drawCircle(mM.sx - w * 0.6f, mM.sy - h * 0.4f, h * 0.28f, ps); s.cv.drawCircle(mM.sx + w * 0.6f, mM.sy - h * 0.4f, h * 0.28f, ps) }
            } else {
                val path = Path(); path.moveTo(mL.sx, mL.sy); path.quadTo(mM.sx, mM.sy + (mM.sy - mL.sy) * 1.3f, mR.sx, mR.sy)
                ps.style = Paint.Style.STROKE; ps.strokeCap = Paint.Cap.ROUND; ps.strokeWidth = 0.05f * mM.s * s.unit; ps.color = c3(0x6B2D2D)
                s.cv.drawPath(path, ps)
            }
        })
    }
    if (robot) {
        val rm = s.surf(hx, hy, 0f, ax, ay, az, 0f, -0.45f)
        if (rm.facing > 0.1f) s.cmds.add(C3Cmd(rm.depth + 0.04f) {
            val ps = s.paint; ps.shader = null; ps.style = Paint.Style.STROKE; ps.strokeWidth = 0.035f * rm.s * s.unit; ps.color = c3(0x2D3748); ps.strokeCap = Paint.Cap.ROUND
            val w = 0.3f * rm.s * s.unit * (0.4f + rm.facing)
            for (i in -1..1) s.cv.drawLine(rm.sx - w, rm.sy + i * 0.07f * rm.s * s.unit, rm.sx + w, rm.sy + i * 0.07f * rm.s * s.unit, ps)
        })
    }
    // ریش و سبیل
    if (!animal && !robot && !alien && c.beard != "none") {
        if (c.beard == "full") s.ball(hx, hy - 0.5f * hr, 0.4f * hr, hr * 0.82f, hr * 0.5f, hr * 0.5f, c.hair, 0.5f, 0.05f)
        val mu = s.surf(hx, hy, 0f, ax * 1.03f, ay, az * 1.03f, 0f, -0.28f)
        if (mu.facing > 0.1f) s.cmds.add(C3Cmd(mu.depth + 0.05f) {
            s.paint.style = Paint.Style.FILL; s.paint.shader = null; s.paint.color = c.hair
            val w = 0.26f * mu.s * s.unit * (0.4f + mu.facing); val h = 0.07f * mu.s * s.unit
            s.cv.drawOval(RectF(mu.sx - w, mu.sy - h, mu.sx + w, mu.sy + h), s.paint)
        })
    }

    }

    // اکسسوری سر
    when (c.accessory) {
        "glasses", "shades" -> {
            val dark = c.accessory == "shades"
            val l = s.surf(hx, hy, 0f, ax * 1.04f, ay * 1.04f, az * 1.04f, -0.42f, 0.08f)
            val r = s.surf(hx, hy, 0f, ax * 1.04f, ay * 1.04f, az * 1.04f, 0.42f, 0.08f)
            if (l.facing > 0.08f) s.cmds.add(C3Cmd(l.depth + 0.08f) {
                val ps = s.paint; ps.shader = null
                val w = 0.26f * l.s * s.unit * (0.45f + 0.55f * l.facing); val h = 0.24f * l.s * s.unit
                for (p in listOf(l, r)) {
                    ps.style = Paint.Style.FILL; ps.color = if (dark) 0xEE111111.toInt() else 0x2AFFFFFF
                    s.cv.drawOval(RectF(p.sx - w, p.sy - h, p.sx + w, p.sy + h), ps)
                    ps.style = Paint.Style.STROKE; ps.strokeWidth = 0.035f * l.s * s.unit; ps.color = c3(0x222222)
                    s.cv.drawOval(RectF(p.sx - w, p.sy - h, p.sx + w, p.sy + h), ps)
                }
                s.cv.drawLine(l.sx + w, l.sy, r.sx - w, r.sy, ps)
            })
        }
        "headphones" -> {
            for (i in 0..8) {
                val a = Math.PI * (i / 8.0)
                s.ball(hx + 0.8f * hr * cos(a).toFloat(), hy + 0.12f + 0.98f * hr * sin(a).toFloat(), 0f, 0.07f, 0.07f, 0.07f, c3(0x333333))
            }
            for (sg in listOf(-1f, 1f)) s.ball(hx + sg * 0.86f * hr, hy + 0.1f, 0f, 0.16f, 0.22f, 0.2f, c3(0xE63946))
        }
        "crown" -> {
            s.ball(hx, hy + hr * 0.92f, 0f, 0.42f, 0.1f, 0.42f, c3(0xFFC83D))
            for (i in 0..4) {
                val a = i * 2.0 * Math.PI / 5.0
                val bx = hx + 0.34f * cos(a).toFloat(); val bz = 0.34f * sin(a).toFloat()
                s.cone(bx, hy + hr * 0.95f, bz, 0.09f, bx, hy + hr * 1.3f, bz, c3(0xFFC83D))
            }
        }
        "horns" -> for (sg in listOf(-1f, 1f)) s.cone(hx + sg * 0.42f, hy + hr * 0.78f, 0f, 0.17f, hx + sg * 0.62f, hy + hr * 1.5f, 0f, c3(0xFF7B00))
        "antenna" -> {
            s.limb(hx, hy + hr * 0.9f, 0f, hx, hy + hr * 1.5f, 0f, 0.035f, if (robot) gray else skin, 4)
            val glow = 0.5f + 0.5f * sin(t * 4f)
            s.ball(hx, hy + hr * 1.58f, 0f, 0.1f, 0.1f, 0.1f, mix(c3(0xFF4D4D), c3(0xFFD166), glow), 0.3f)
        }
    }
    if (alien && c.accessory != "antenna") for (sg in listOf(-1f, 1f)) { s.limb(hx + sg * 0.25f, hy + hr * 0.9f, 0f, hx + sg * 0.4f, hy + hr * 1.45f, 0f, 0.03f, skin, 4); s.ball(hx + sg * 0.4f, hy + hr * 1.5f, 0f, 0.09f, 0.09f, 0.09f, c3(0xFFEB3B), 0.3f) }
}

internal fun c3Draw(cv: ACanvas, W: Float, H: Float, cfg: CharCfg, yaw: Float, t: Float, zoom: Float) {
    val ps = Paint(Paint.ANTI_ALIAS_FLAG)
    ps.shader = LinearGradient(0f, 0f, 0f, H, cfg.bg1, cfg.bg2, Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, W, H, ps)
    // ذرات نورانی پس‌زمینه
    ps.shader = null
    for (i in 0 until 16) {
        val x = ((i * 73) % 100) / 100f * W
        val y = ((i * 47) % 68) / 100f * H
        val tw = 0.5f + 0.5f * sin(t * 1.6f + i)
        ps.color = android.graphics.Color.argb((40 + 60 * tw).toInt(), 255, 255, 255)
        cv.drawCircle(x, y, (0.004f + 0.006f * ((i % 4) + 1)) * W * (0.6f + 0.4f * tw), ps)
    }
    val unit = H * 0.215f * zoom
    val gy = H * 0.88f
    // سکوی زیر کاراکتر و سایه
    val rx = unit * 1.45f; val ry = unit * 0.34f
    ps.shader = LinearGradient(0f, gy - ry, 0f, gy + ry, mix(cfg.bg2, WHITE, 0.55f), mix(cfg.bg2, BLACK, 0.2f), Shader.TileMode.CLAMP)
    ps.alpha = 170
    cv.drawOval(RectF(W / 2f - rx, gy - ry, W / 2f + rx, gy + ry), ps)
    ps.alpha = 255
    ps.shader = RadialGradient(W / 2f, gy, unit * 0.9f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
    cv.drawOval(RectF(W / 2f - unit * 0.9f, gy - ry * 0.7f, W / 2f + unit * 0.9f, gy + ry * 0.7f), ps)
    ps.shader = null
    val sc = C3Scene(cv, W, H, unit, gy, yaw)
    buildCharacter(sc, cfg, t)
    sc.flush()
}

private fun c3Bitmap(w: Int, h: Int, cfg: CharCfg, yaw: Float, t: Float, zoom: Float): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    c3Draw(ACanvas(b), w.toFloat(), h.toFloat(), cfg, yaw, t, zoom)
    return b
}

// ---------------------------------------------------------------- صفحه

private val C3_SKINS = listOf(0xF8D9C0, 0xF2C9A0, 0xE0A878, 0xC68642, 0x8D5524, 0x5C3A21, 0xF5F5F5, 0xF2A65A, 0xA9744F, 0x9AA0A6, 0x222222, 0x7CE07C, 0x5BC8F5, 0x9B6CFF, 0xFF7AA2).map { c3(it.toLong()) }
private val C3_CLOTH = listOf(0xFFFFFF, 0x111111, 0xE63946, 0xFF6FA8, 0xFF8A5C, 0xFFD166, 0x5BC97B, 0x12C2A7, 0x3FA2F7, 0x4F7CFF, 0x7C6CFF, 0x9AA0A6).map { c3(it.toLong()) }
private val C3_HAIRC = listOf(0x0B0B0B, 0x3B2A1A, 0x6B4423, 0xB5651D, 0xE6C27A, 0xF5F5F5, 0xC0392B, 0xFF6FA8, 0x7C6CFF, 0x3FA2F7, 0x12C2A7).map { c3(it.toLong()) }
private val C3_BGS = listOf(
    0x8FD3F4 to 0x84FAB0, 0xFBC2EB to 0xA6C1EE, 0xFFE29F to 0xFFA99F, 0x0F2027 to 0x2C5364, 0x41295A to 0x2F0743,
    0xFFEFD5 to 0xF6D365, 0x232526 to 0x414345, 0x56CCF2 to 0x2F80ED, 0xF857A6 to 0xFF5858, 0x56AB2F to 0xA8E063,
).map { c3(it.first.toLong()) to c3(it.second.toLong()) }

@Composable
private fun C3Dots(colors: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { col ->
            val on = (col and 0xFFFFFF) == (current and 0xFFFFFF)
            Box(
                Modifier.size(if (on) 38.dp else 32.dp).clip(CircleShape).background(Color(col)).clickable { onPick(col) },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Rounded.Check, null, tint = if ((col and 0xFFFFFF) > 0x888888) Color.Black else Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun CharStudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var cfg by remember { mutableStateOf(C3_PRESETS[0].second.copy2()) }
    var tick by remember { mutableStateOf(0) }
    var yaw by remember { mutableStateOf(18f) }
    var zoom by remember { mutableStateOf(1f) }
    var auto by remember { mutableStateOf(true) }
    var time by remember { mutableStateOf(0f) }
    var lastTouch by remember { mutableStateOf(-10f) }
    var prompt by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var vprog by remember { mutableStateOf(-1) }
    var shareFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) {
                    val dt = ((n - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.1f)
                    time += dt
                    if (auto && time - lastTouch > 1.2f) yaw += dt * 30f
                }
                last = n
            }
        }
    }
    fun change(f: CharCfg.() -> Unit) { cfg = cfg.copy2().also(f); tick++ }

    fun generate(modify: Boolean) {
        if (busy) return
        if (prompt.isBlank() && photo == null) { toast(ctx, "توضیح بنویس یا یک عکس انتخاب کن"); return }
        busy = true
        scope.launch {
            try {
                val userText = if (modify) "Current character JSON:\n${c3Json(cfg)}\n\nApply this change and return the FULL updated JSON: $prompt"
                else "Character: ${prompt.ifBlank { "a cartoon version of the person in the photo" }}"
                val txt = Ai.generate(userText, system = C3_SYSTEM, image = photo, json = true, temperature = 0.7, maxTokens = 700)
                val n = c3Apply(if (modify) cfg else CharCfg(), txt)
                if (n == null) toast(ctx, "پاسخ هوش مصنوعی خوانده نشد؛ دوباره امتحان کن")
                else { cfg = n; tick++ }
            } catch (e: Exception) { toast(ctx, e.message ?: "ساخت کاراکتر ناموفق بود") }
            busy = false
        }
    }

    ToolScaffold("کاراکتر سه‌بعدی", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(20.dp))) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, z, _ ->
                            yaw += pan.x * 0.45f
                            zoom = (zoom * z).coerceIn(0.6f, 1.8f)
                            lastTouch = time
                        }
                    },
                ) {
                    val tk = tick
                    drawIntoCanvas { c3Draw(it.nativeCanvas, size.width, size.height, cfg, yaw, time, zoom) }
                }
                Text(cfg.name, Modifier.align(Alignment.TopCenter).padding(top = 10.dp), fontSize = 16.sp, color = Color.White)
            }
            Text("با انگشت بچرخان و با دو انگشت بزرگ و کوچک کن.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(auto, { auto = it }); Spacer(Modifier.width(10.dp)); Text("چرخش خودکار", fontSize = 13.sp)
            }

            SectionTitle("کاراکتر از روی عکس خودت")
            Text("یک عکس از صورتت بگیر یا از گالری بردار؛ چهره‌ی واقعی‌ات روی کاراکتر سه‌بعدی می‌نشیند و پوست و رنگ مو هم از عکس برداشته می‌شود.", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.7f))
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b ->
                scope.launch {
                    val r = withContext(Dispatchers.Default) { try { c3FaceFromPhoto(b) } catch (e: Throwable) { null } }
                    if (r == null) toast(ctx, "پردازش عکس ناموفق بود؛ عکس دیگری امتحان کن")
                    else {
                        cfg = cfg.copy2().also { it.face = r.first; it.skin = r.second; it.hair = r.third; it.species = "human"; it.beard = "none"; it.name = "من" }
                        tick++; yaw = 0f; lastTouch = time
                        toast(ctx, "چهره روی کاراکتر نشست؛ حالا لباس و مدل را انتخاب کن")
                    }
                }
            })
            if (cfg.face != null) TextButton({ change { face = null } }, Modifier.padding(horizontal = 12.dp)) { Text("برداشتن چهره‌ی عکس (برگشت به کارتونی)") }

            SectionTitle("کاراکتر آماده")
            ChipRow(C3_PRESETS.map { it.first }, "", { it }) { n -> C3_PRESETS.first { it.first == n }.let { cfg = it.second.copy2(); tick++ } }

            SectionTitle("ساخت با هوش مصنوعی")
            AppField(prompt, { prompt = it }, "توضیح بده؛ مثلاً: دختر با موی بلند صورتی و عینک", Modifier.padding(horizontal = 16.dp), minLines = 1, maxLines = 3)
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradButton(if (busy) "در حال ساخت…" else "بساز", icon = Icons.Rounded.AutoAwesome, modifier = Modifier.weight(1f), enabled = !busy, onClick = { generate(false) })
                OutlinedButton({ generate(true) }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = !busy) { Text("اعمال تغییر روی این کاراکتر", fontSize = 12.sp, maxLines = 2) }
            }

            SectionTitle("ویرایش دستی")
            Text("نوع", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_SPECIES.map { it.first }, cfg.species, { k -> C3_SPECIES.first { it.first == k }.second }) { v -> change { species = v } }
            Text("حرکت", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_POSE.map { it.first }, cfg.pose, { k -> C3_POSE.first { it.first == k }.second }) { v -> change { pose = v } }
            Text("اندازه‌ی بدن", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_SHAPE.map { it.first }, cfg.shape, { k -> C3_SHAPE.first { it.first == k }.second }) { v -> change { shape = v } }
            Text("مدل مو", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_HAIR.map { it.first }, cfg.hairStyle, { k -> C3_HAIR.first { it.first == k }.second }) { v -> change { hairStyle = v } }
            Text("ریش و سبیل", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_BEARD.map { it.first }, cfg.beard, { k -> C3_BEARD.first { it.first == k }.second }) { v -> change { beard = v } }
            Text("اکسسوری", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_ACC.map { it.first }, cfg.accessory, { k -> C3_ACC.first { it.first == k }.second }) { v -> change { accessory = v } }
            Text("گوش", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            ChipRow(C3_EARS.map { it.first }, cfg.ears, { k -> C3_EARS.first { it.first == k }.second }) { v -> change { ears = v } }

            SectionTitle("رنگ‌ها")
            Text("پوست یا پشم", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); C3Dots(C3_SKINS, cfg.skin) { v -> change { skin = v } }
            Text("لباس", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); C3Dots(C3_CLOTH, cfg.body) { v -> change { body = v } }
            Text("شلوار", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); C3Dots(C3_CLOTH, cfg.pants) { v -> change { pants = v } }
            Text("کفش", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); C3Dots(C3_CLOTH, cfg.shoes) { v -> change { shoes = v } }
            Text("مو", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); C3Dots(C3_HAIRC, cfg.hair) { v -> change { hair = v } }
            Text("چشم", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp); C3Dots(listOf(0x3A2A1A, 0x2E7D32, 0x1E88E5, 0x6A1B9A, 0x00E5FF, 0xE63946, 0x111111).map { c3(it.toLong()) }, cfg.eyes) { v -> change { eyes = v } }
            Text("پس‌زمینه", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                C3_BGS.forEach { (a, b) -> Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(a), Color(b)))).clickable { change { bg1 = a; bg2 = b } }) }
            }

            SectionTitle("ذخیره و اشتراک")
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradButton(
                    "ذخیره‌ی عکس در گالری", icon = Icons.Rounded.Download, modifier = Modifier.weight(1f),
                    onClick = {
                        if (canExport()) scope.launch {
                            val cf = cfg.copy2(); val y = yaw; val tt = time; val z = zoom
                            val b = withContext(Dispatchers.Default) { c3Bitmap(1080, 1316, cf, y, tt, z) }
                            toast(ctx, if (saveBitmapToGallery(ctx, b, "AIHub_char_${System.currentTimeMillis()}")) "عکس در گالری ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
                OutlinedButton(
                    onClick = {
                        if (canExport()) scope.launch {
                            try {
                                val cf = cfg.copy2(); val y = yaw; val tt = time; val z = zoom
                                val f = withContext(Dispatchers.IO) {
                                    val b = c3Bitmap(1080, 1316, cf, y, tt, z)
                                    File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "char_share.png").also { out -> out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                                }
                                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                                val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "image/png"; putExtra(android.content.Intent.EXTRA_STREAM, u); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                ctx.startActivity(android.content.Intent.createChooser(i, "اشتراک‌گذاری").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) { toast(ctx, "اشتراک‌گذاری ممکن نشد") }
                        }
                    },
                    modifier = Modifier.weight(0.6f).heightIn(min = 52.dp),
                ) { Icon(Icons.Rounded.Share, null) }
            }
            GradButton(
                if (vprog >= 0) "در حال ساخت ویدیو… ${vprog}٪" else "ذخیره‌ی ویدیوی چرخشی (MP4) در گالری", icon = Icons.Rounded.Movie,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = vprog < 0,
                onClick = {
                    if (canExport()) scope.launch {
                        vprog = 0
                        val cf = cfg.copy2(); val z = zoom; val y0 = yaw
                        val out = File(ctx.cacheDir, "char_${System.currentTimeMillis()}.mp4")
                        val ok = withContext(Dispatchers.Default) {
                            try {
                                val fps = 24; val frames = 6 * fps
                                StoryVideo.encodeVideo(out, 720, 880, fps, frames, { vprog = it }) { i -> c3Bitmap(720, 880, cf, y0 + 360f * i / frames, i / fps.toFloat(), z) }
                            } catch (e: Throwable) { false }
                        }
                        vprog = -1
                        if (ok) {
                            val saved = withContext(Dispatchers.IO) { saveFileToGallery(ctx, out, "AIHub_char_${System.currentTimeMillis()}.mp4", "video/mp4") }
                            toast(ctx, if (saved) "ویدیو در گالری ذخیره شد (Movies/AIHub)" else "ذخیره نشد")
                        } else toast(ctx, "ساخت ویدیو ناموفق بود")
                        out.delete()
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
