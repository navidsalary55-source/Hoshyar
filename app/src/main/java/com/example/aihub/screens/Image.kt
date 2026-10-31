package com.example.aihub.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- ویرایش تصویر

private enum class Preset(val label: String) {
    NONE("اصلی"), BW("سیاه‌وسفید"), SEPIA("قدیمی"), VIVID("پرانرژی"), WARM("گرم"), COOL("سرد"), INVERT("معکوس"), FADE("محو"),
}

private fun presetMatrix(p: Preset): ColorMatrix = when (p) {
    Preset.NONE -> ColorMatrix()
    Preset.BW -> ColorMatrix().apply { setSaturation(0f) }
    Preset.SEPIA -> ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    Preset.VIVID -> ColorMatrix().apply { setSaturation(1.7f) }
    Preset.WARM -> ColorMatrix(floatArrayOf(
        1.12f, 0f, 0f, 0f, 12f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.85f, 0f, -12f, 0f, 0f, 0f, 1f, 0f))
    Preset.COOL -> ColorMatrix(floatArrayOf(
        0.88f, 0f, 0f, 0f, -10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.15f, 0f, 16f, 0f, 0f, 0f, 1f, 0f))
    Preset.INVERT -> ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
    Preset.FADE -> ColorMatrix(floatArrayOf(
        0.85f, 0f, 0f, 0f, 38f, 0f, 0.85f, 0f, 0f, 38f, 0f, 0f, 0.85f, 0f, 38f, 0f, 0f, 0f, 1f, 0f))
}

private fun buildMatrix(p: Preset, bright: Float, contrast: Float, sat: Float): ColorMatrix {
    val m = ColorMatrix()
    m.setSaturation(sat)
    val t = 128f * (1f - contrast) + bright
    m.postConcat(ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    m.postConcat(presetMatrix(p))
    return m
}

private fun render(src: Bitmap, rot: Int, flip: Boolean, cm: ColorMatrix): Bitmap {
    val mx = Matrix()
    mx.postRotate(rot.toFloat())
    if (flip) mx.postScale(-1f, 1f)
    val t = Bitmap.createBitmap(src, 0, 0, src.width, src.height, mx, true)
    val out = Bitmap.createBitmap(t.width, t.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    Canvas(out).drawBitmap(t, 0f, 0f, paint)
    return out
}

private fun downscale(b: Bitmap, maxDim: Int): Bitmap {
    val m = max(b.width, b.height)
    if (m <= maxDim) return b
    val s = maxDim.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
}

@Composable
fun ImageEditScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    var orig by remember { mutableStateOf<Bitmap?>(null) }
    var preset by remember { mutableStateOf(Preset.NONE) }
    var bright by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var sat by remember { mutableStateOf(1f) }
    var rot by remember { mutableStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var advice by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, uri, 2048) }
            if (b != null) {
                orig = b; preset = Preset.NONE; bright = 0f; contrast = 1f; sat = 1f; rot = 0; flip = false; advice = ""
            } else toast(ctx, "تصویر خوانده نشد")
        }
    }
    val base = remember(orig) { orig?.let { downscale(it, 1024) } }
    val preview = remember(base, preset, bright, contrast, sat, rot, flip) {
        base?.let { render(it, rot, flip, buildMatrix(preset, bright, contrast, sat)) }
    }
    val c = MaterialTheme.colorScheme

    ToolScaffold("ویرایش تصویر", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b ->
                orig = b; preset = Preset.NONE; bright = 0f; contrast = 1f; sat = 1f; rot = 0; flip = false; advice = ""
            })
            if (preview != null) {
                BitmapImage(
                    preview,
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(20.dp)),
                )
                SectionTitle("فیلتر")
                ChipRow(Preset.values().toList(), preset, { it.label }) { preset = it }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("روشنایی", fontSize = 13.sp)
                    Slider(bright, { bright = it }, valueRange = -80f..80f)
                    Text("کنتراست", fontSize = 13.sp)
                    Slider(contrast, { contrast = it }, valueRange = 0.5f..1.6f)
                    Text("اشباع رنگ", fontSize = 13.sp)
                    Slider(sat, { sat = it }, valueRange = 0f..2f)
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ rot = (rot + 90) % 360 }) { Icon(Icons.Rounded.Rotate90DegreesCcw, null); Spacer(Modifier.width(6.dp)); Text("چرخش") }
                    OutlinedButton({ flip = !flip }) { Icon(Icons.Rounded.Flip, null); Spacer(Modifier.width(6.dp)); Text("برعکس") }
                    OutlinedButton({ preset = Preset.NONE; bright = 0f; contrast = 1f; sat = 1f; rot = 0; flip = false }) {
                        Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("ریست")
                    }
                }
                GradButton(
                    "ذخیره در گالری", icon = Icons.Rounded.Download,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        if (canExport()) scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                val full = render(orig!!, rot, flip, buildMatrix(preset, bright, contrast, sat))
                                val saved = saveBitmapToGallery(ctx, full)
                                History.add(
                                    "IMAGE_EDIT", "ویرایش تصویر",
                                    "فیلتر: ${preset.label} · روشنایی ${"%.0f".format(bright)} · کنتراست ${"%.1f".format(contrast)} · اشباع ${"%.1f".format(sat)}",
                                    downscale(full, 1600).toJpegBytes(88), "jpg",
                                )
                                saved
                            }
                            toast(ctx, if (ok) "در گالری (Pictures/AIHub) ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
                GradButton(
                    "پیشنهاد ویرایش با هوش مصنوعی", icon = Icons.Rounded.AutoAwesome, loading = task.loading,
                    colors = listOf(androidx.compose.ui.graphics.Color(0xFF8E54E9), androidx.compose.ui.graphics.Color(0xFF47B8E0)),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        task.launch(scope) {
                            val bytes = withContext(Dispatchers.Default) { base!!.toJpegBytes(80) }
                            advice = Ai.generate(
                                "این عکس را تحلیل کن و پیشنهاد مشخص برای بهتر شدنش بده (نور، رنگ، کادر، فیلتر مناسب). کوتاه و کاربردی به فارسی.",
                                image = bytes,
                            )
                            History.add("IMAGE_EDIT", "پیشنهاد هوش مصنوعی برای عکس", advice, bytes, "jpg")
                        }
                    },
                )
                ErrorText(task.error)
                if (advice.isNotBlank()) ResultCard(advice, Modifier.padding(horizontal = 16.dp))
            } else {
                Text(
                    "یک عکس انتخاب کن تا فیلتر، نور، رنگ و چرخش رو روش اعمال کنی.",
                    Modifier.padding(24.dp), color = c.onBackground.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- متحرک‌سازی تصویر

private enum class Motion(val label: String) {
    KENBURNS("زوم سینمایی"), PAN("حرکت افقی"), PULSE("ضربان"), FLOAT("شناور"), SWING("تاب"), SHAKE("لرزش"), FLIP3D("چرخش سه‌بعدی"),
}

private enum class Overlay(val label: String) { NONE("بدون افکت"), SNOW("برف"), STARS("ستاره"), BUBBLES("حباب") }

private class Particle(val x: Float, val y: Float, val r: Float, val speed: Int, val phase: Float)

@Composable
fun AnimateScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var motion by remember { mutableStateOf(Motion.KENBURNS) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var speed by remember { mutableStateOf(1f) }
    var area by remember { mutableStateOf(android.graphics.Rect()) }
    var recProg by remember { mutableStateOf(-1) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, uri, 1600) }
            if (b != null) bmp = b else toast(ctx, "تصویر خوانده نشد")
        }
    }
    val particles = remember { List(46) { Particle(Random.nextFloat(), Random.nextFloat(), 2f + Random.nextFloat() * 5f, 1 + Random.nextInt(3), Random.nextFloat()) } }

    val dur = (6000 / speed).toInt().coerceAtLeast(600)
    val t = rememberInfiniteTransition(label = "anim")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(dur, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    val loop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(dur * 2, easing = LinearEasing)), label = "l")
    val e = FastOutSlowInEasing.transform(p)
    val twoPi = (2 * PI).toFloat()

    ToolScaffold("متحرک‌سازی تصویر", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b -> bmp = b })
            val b = bmp
            if (b != null) {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(0.85f).clip(RoundedCornerShape(24.dp))
                        .onGloballyPositioned { c -> val r = c.boundsInWindow(); area = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()) },
                ) {
                    BitmapImage(
                        b, crop = true,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            when (motion) {
                                Motion.KENBURNS -> { scaleX = 1f + 0.28f * e; scaleY = scaleX; translationX = -40f * e }
                                Motion.PAN -> { scaleX = 1.25f; scaleY = 1.25f; translationX = (e - 0.5f) * 160f }
                                Motion.PULSE -> { scaleX = 1f + abs(sin(loop * twoPi * 2)) * 0.08f; scaleY = scaleX }
                                Motion.FLOAT -> { scaleX = 1.06f; scaleY = 1.06f; translationY = sin(loop * twoPi) * 28f }
                                Motion.SWING -> { scaleX = 1.1f; scaleY = 1.1f; rotationZ = sin(loop * twoPi) * 4f }
                                Motion.SHAKE -> { scaleX = 1.05f; scaleY = 1.05f; translationX = sin(loop * twoPi * 14) * 8f }
                                Motion.FLIP3D -> { rotationY = loop * 360f; cameraDistance = 14f * density }
                            }
                        },
                    )
                    if (overlay != Overlay.NONE) {
                        ComposeCanvas(Modifier.fillMaxSize()) {
                            particles.forEach { q ->
                                val prog = (q.y + loop * q.speed) % 1f
                                val sway = sin((loop * q.speed + q.phase) * twoPi) * 14f
                                when (overlay) {
                                    Overlay.SNOW -> drawCircle(
                                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), q.r,
                                        Offset(q.x * size.width + sway, prog * size.height),
                                    )
                                    Overlay.BUBBLES -> drawCircle(
                                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f), q.r * 2.2f,
                                        Offset(q.x * size.width + sway, (1f - prog) * size.height),
                                    )
                                    Overlay.STARS -> drawCircle(
                                        androidx.compose.ui.graphics.Color(0xFFFFE9A8).copy(alpha = 0.25f + 0.75f * abs(sin((loop * q.speed + q.phase) * twoPi * 2))),
                                        q.r * 0.8f, Offset(q.x * size.width, q.y * size.height),
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }
                }
                SectionTitle("حرکت")
                ChipRow(Motion.values().toList(), motion, { it.label }) { motion = it }
                SectionTitle("افکت روی تصویر")
                ChipRow(Overlay.values().toList(), overlay, { it.label }) { overlay = it }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("سرعت", fontSize = 13.sp)
                    Slider(speed, { speed = it }, valueRange = 0.4f..3f)
                }
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradButton(
                        if (recProg >= 0) "در حال ضبط… ${recProg}٪" else "ذخیره‌ی ویدیو (MP4)", icon = Icons.Rounded.Download,
                        modifier = Modifier.weight(1f), enabled = recProg < 0,
                        onClick = {
                            if (canExport()) scope.launch {
                                recProg = 0
                                val f = VideoRec.record(ctx, { area }, (dur * 2L).coerceIn(2000L, 8000L)) { recProg = it }
                                recProg = -1
                                if (f == null) toast(ctx, "ضبط ویدیو ناموفق بود")
                                else {
                                    val bytes = withContext(Dispatchers.IO) { f.readBytes().also { f.delete() } }
                                    val ok = saveBytesToGallery(ctx, bytes, "AIHub_anim_${System.currentTimeMillis()}.mp4", "video/mp4")
                                    toast(ctx, if (ok) "ویدیو در گالری ذخیره شد (پوشه‌ی Movies/AIHub)" else "ذخیره نشد")
                                }
                            }
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            if (canExport()) scope.launch {
                                val shot = VideoRec.snapshot(ctx, area)
                                if (shot != null) toast(ctx, if (saveBitmapToGallery(ctx, shot)) "عکس در گالری ذخیره شد" else "ذخیره نشد")
                                else toast(ctx, "گرفتن عکس ممکن نشد")
                            }
                        },
                        modifier = Modifier.weight(0.7f).heightIn(min = 52.dp),
                        enabled = recProg < 0,
                    ) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(6.dp)); Text("عکس", maxLines = 1) }
                }
                Text(
                    "هنگام ضبط، صفحه را حرکت نده تا ویدیو کامل ضبط شود.",
                    Modifier.padding(horizontal = 20.dp), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    "یک عکس انتخاب کن تا با افکت‌های حرکتی و ذرات متحرک زنده بشه.",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
