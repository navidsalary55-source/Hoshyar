package ir.hooshyar.app.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private class Scene(val bmp: Bitmap, val text: String)

private fun convPcm(p: MediaTrim.Pcm, sr: Int, ch: Int): ShortArray {
    val inFrames = p.data.size / p.ch
    val outFrames = (inFrames.toLong() * sr / p.sr).toInt()
    val o = ShortArray(outFrames * ch)
    for (f in 0 until outFrames) {
        val sf = (f.toLong() * p.sr / sr).toInt().coerceAtMost(inFrames - 1)
        for (c in 0 until ch) o[f * ch + c] = p.data[sf * p.ch + minOf(c, p.ch - 1)]
    }
    return o
}

private class SvCtl(val ctx: Context, val scope: CoroutineScope) {
    val speaker = Speaker(ctx)
    val stt = SpeechInput(ctx)
    var input by mutableStateOf("")
    var lang by mutableStateOf(Langs[0])
    var count by mutableStateOf(4)
    var style by mutableStateOf("سینمایی")
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val scenes = mutableStateListOf<Scene>()
    var index by mutableStateOf(0)
    var playing by mutableStateOf(false)
    private var genJob: Job? = null
    private var playJob: Job? = null

    private val styleMap = mapOf(
        "سینمایی" to "cinematic film still, dramatic lighting",
        "انیمیشن" to "3D animated movie style, colorful",
        "نقاشی" to "digital painting, artistic",
        "واقعی" to "photorealistic, natural light",
    )
    val styles = styleMap.keys.toList()

    fun generate() {
        if (busy || input.isBlank()) return
        stopPlay()
        error = null
        busy = true
        genJob = scope.launch {
            try {
                status = "نوشتن فیلم‌نامه…"
                val raw = Ai.generate(
                    "Story/idea: $input",
                    system = "You are a video director. Break the user's idea into exactly $count short scenes. " +
                        "Return ONLY JSON: {\"scenes\":[{\"narration\":\"1-2 sentences in ${lang.name}\",\"image_prompt\":\"detailed English visual description of the scene, no text in image\"}]}",
                    json = true, temperature = 0.9, maxTokens = 3000,
                )
                val arr = JSONObject(raw.stripCodeFence()).getJSONArray("scenes")
                val items = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    o.optString("narration") to o.optString("image_prompt")
                }
                scenes.clear()
                index = 0
                for ((i, it) in items.withIndex()) {
                    status = "ساخت تصویر ${i + 1} از ${items.size}… (سرویس رایگان کمی کند است)"
                    val bytes = Poll.image("${it.second}, ${styleMap[style]}", 1280, 720)
                    val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        ?: throw AiException("یکی از تصویرها ساخته نشد. دوباره امتحان کن.")
                    scenes.add(Scene(bmp, it.first))
                    if (i < items.size - 1) delay(2000)
                }
                status = "ویدیو آماده است ✔"
                val thumb = withContext(Dispatchers.Default) { scenes.firstOrNull()?.bmp?.toJpegBytes(80) }
                History.add(
                    "SPEECH_VIDEO", input,
                    "💡 $input\n(${lang.label}، $style)\n\n" + scenes.mapIndexed { i, s -> "صحنه ${i + 1}: ${s.text}" }.joinToString("\n\n"),
                    thumb, "jpg",
                )
                play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "خطا"
                status = ""
            } finally {
                busy = false
            }
        }
    }

    fun play() {
        if (scenes.isEmpty()) return
        stopPlay()
        playing = true
        playJob = scope.launch {
            try {
                for (i in 0 until scenes.size) {
                    index = i
                    Tts.say(ctx, speaker, scenes[i].text, lang, onNote = { error = it })
                    delay(300)
                }
            } finally {
                playing = false
            }
        }
    }

    var musicUri by mutableStateOf<android.net.Uri?>(null)
    var musicName by mutableStateOf("")
    var lastVideo by mutableStateOf<java.io.File?>(null)
    var exporting by mutableStateOf(false)
    var expProg by mutableStateOf(0)

    /** ویدیوی MP4 واقعی با روایت صوتی و زیرنویس می‌سازد. null یعنی ناموفق. */
    suspend fun exportVideo(): java.io.File? = withContext(Dispatchers.Default) {
        val tmp = ArrayList<java.io.File>()
        try {
            val tag = lang.tag.substringBefore('-')
            val pcms = ArrayList<MediaTrim.Pcm?>()
            for ((i, s) in scenes.withIndex()) {
                expProg = i * 20 / scenes.size
                var acc: ShortArray? = null; var sr = 0; var ch = 0
                if (Store.voiceOnline) {
                    try {
                        val clean = s.text.replace(Regex("\\[.*?]"), " ").trim()
                        for (part in splitSpeech(clean, 220)) {
                            val bytes = Poll.speech(part, Store.botVoice, tag)
                            val f = java.io.File(ctx.cacheDir, "sv_${i}_${System.nanoTime()}.mp3").also { tmp.add(it) }
                            f.writeBytes(bytes)
                            val pc = MediaTrim.decodeRange(ctx, android.net.Uri.fromFile(f), 0L, 600_000_000L) {} ?: continue
                            if (acc == null) { acc = pc.data; sr = pc.sr; ch = pc.ch } else if (pc.sr == sr && pc.ch == ch) acc = acc + pc.data
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                }
                pcms.add(acc?.let { MediaTrim.Pcm(it, sr, ch) })
            }
            val dur = FloatArray(scenes.size) { i ->
                val pc = pcms[i]
                if (pc != null) (pc.data.size.toFloat() / (pc.sr * pc.ch) + 0.7f).coerceAtLeast(3.5f)
                else (scenes[i].text.length * 0.08f).coerceAtLeast(4f)
            }
            val start = FloatArray(scenes.size); var tot = 0f
            for (i in dur.indices) { start[i] = tot; tot += dur[i] }
            var aUri: android.net.Uri? = null
            val ref = pcms.firstOrNull { it != null }
            val music = musicUri?.let { MediaTrim.decodeRange(ctx, it, 0L, (tot * 1_000_000L).toLong() + 1_000_000L) {} }
            val tsr = music?.sr ?: ref?.sr
            val tch = music?.ch ?: ref?.ch
            if (tsr != null && tch != null) {
                val all = IntArray((tot * tsr).toInt() * tch + tch * 2)
                for (i in pcms.indices) {
                    val pc = pcms[i] ?: continue
                    val cv2 = convPcm(pc, tsr, tch)
                    val off = (start[i] * tsr).toInt() * tch
                    for (k in 0 until minOf(cv2.size, all.size - off)) all[off + k] += cv2[k].toInt()
                }
                if (music != null) {
                    val m = convPcm(music, tsr, tch)
                    if (m.isNotEmpty()) {
                        val g = if (ref != null) 0.22f else 0.7f
                        val fadeN = (1.5f * tsr).toInt() * tch
                        for (k in all.indices) {
                            var s = m[k % m.size] * g
                            val left = all.size - k
                            if (left < fadeN) s *= left.toFloat() / fadeN
                            all[k] += s.toInt()
                        }
                    }
                }
                val out16 = ShortArray(all.size) { all[it].coerceIn(-32768, 32767).toShort() }
                val wav = java.io.File(ctx.cacheDir, "sv_a_${System.nanoTime()}.wav").also { tmp.add(it) }
                MediaTrim.writeWav(MediaTrim.Pcm(out16, tsr, tch), wav)
                aUri = android.net.Uri.fromFile(wav)
            }
            val W = 1280; val H = 720; val fps = 24
            val frames = (tot * fps).toInt().coerceAtLeast(2)
            val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 34f }
            val layouts = scenes.map {
                android.text.StaticLayout.Builder.obtain(it.text, 0, it.text.length, tp, W - 140)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.2f).build()
            }
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            val dst = android.graphics.Rect(0, 0, W, H)
            val v = java.io.File(ctx.cacheDir, "sv_v_${System.nanoTime()}.mp4").also { tmp.add(it) }
            val ok = StoryVideo.encodeVideo(v, W, H, fps, frames, { expProg = 20 + it * 70 / 100 }) { f ->
                val tt = f.toFloat() / fps
                var idx = 0
                for (k in start.indices) if (start[k] <= tt) idx = k
                val lt = tt - start[idx]
                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bmp)
                cv.drawColor(android.graphics.Color.BLACK)
                fun drawScene(i: Int, local: Float, alpha: Int) {
                    val z = 1f + 0.15f * (local / dur[i]).coerceIn(0f, 1f)
                    paint.alpha = alpha
                    cv.save(); cv.scale(z, z, W / 2f, H / 2f)
                    cv.drawBitmap(scenes[i].bmp, null, dst, paint)
                    cv.restore()
                }
                if (idx > 0 && lt < 0.5f) { drawScene(idx - 1, dur[idx - 1], 255); drawScene(idx, lt, (lt / 0.5f * 255).toInt().coerceIn(0, 255)) }
                else drawScene(idx, lt, 255)
                val lay = layouts[idx]
                val a = (lt / 0.4f).coerceIn(0f, 1f)
                val barH = lay.height + 44f
                val bp = android.graphics.Paint().apply { color = android.graphics.Color.argb((150 * a).toInt(), 0, 0, 0) }
                cv.drawRect(0f, H - barH - 24f, W.toFloat(), H - 24f + 20f, bp)
                cv.save(); cv.translate(70f, H - barH - 24f + 22f)
                tp.alpha = (255 * a).toInt(); lay.draw(cv); cv.restore()
                bmp
            }
            if (!ok) return@withContext null
            expProg = 95
            val out = java.io.File(ctx.cacheDir, "sv_out_${System.currentTimeMillis()}.mp4")
            if (StoryVideo.mux(ctx, v, aUri, (tot * 1_000_000L).toLong(), out) && out.length() > 0) out else null
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
        finally { tmp.forEach { it.delete() } }
    }

    fun stopPlay() { playJob?.cancel(); speaker.stop(); playing = false }
    fun cancelAll() { genJob?.cancel(); stopPlay(); stt.release(); busy = false }
    fun release() { genJob?.cancel(); playJob?.cancel(); stt.release(); speaker.shutdown() }
}

@Composable
fun SpeechVideoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember { SvCtl(ctx, scope) }
    DisposableEffect(Unit) { onDispose { ctl.release() } }
    val c = MaterialTheme.colorScheme

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ctl.stt.start(ctl.lang.tag, { ctl.input = it }, { toast(ctx, it) }) else toast(ctx, "اجازه‌ی میکروفون لازم است")
    }

    val musicPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { ctl.musicUri = u; ctl.musicName = try { MediaTrim.info(ctx, u)?.name.orEmpty() } catch (e: Exception) { "" } }
    }

    ToolScaffold("گفتار به ویدیو", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "ایده یا داستانت رو بگو یا بنویس؛ هوش مصنوعی صحنه‌ها رو می‌نویسه، تصویر می‌سازه و با صدا روایت می‌کنه.",
                Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f),
            )
            AppField(
                ctl.input, { ctl.input = it }, "ایده‌ی ویدیو (می‌تونی با میکروفون بگی)", Modifier.padding(horizontal = 16.dp), minLines = 3,
                trailing = {
                    IconButton(onClick = {
                        if (ctl.stt.listening) ctl.stt.finish()
                        else if (hasMic(ctx)) ctl.stt.start(ctl.lang.tag, { ctl.input = it }, { toast(ctx, it) })
                        else perm.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Icon(if (ctl.stt.listening) Icons.Rounded.Stop else Icons.Rounded.Mic, "میکروفون") }
                },
            )
            SectionTitle("زبان روایت و گفتار"); ChipRow(Langs.take(6), ctl.lang, { it.label }) { ctl.lang = it }
            SectionTitle("تعداد صحنه"); ChipRow(listOf(3, 4, 5, 6), ctl.count, { "$it صحنه" }) { ctl.count = it }
            SectionTitle("سبک تصویر"); ChipRow(ctl.styles, ctl.style, { it }) { ctl.style = it }
            SectionTitle("صدای گوینده"); ChipRow(listOf("nova", "onyx"), Store.botVoice.let { if (it in setOf("onyx", "echo", "fable")) "onyx" else "nova" }, { if (it == "onyx") "مرد" else "زن" }) { Store.saveFlags(Store.autoFallback, Store.voiceOnline, it) }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ if (ctl.musicUri == null) musicPick.launch(arrayOf("audio/*")) else { ctl.musicUri = null; ctl.musicName = "" } }) {
                    Icon(if (ctl.musicUri == null) Icons.Rounded.LibraryMusic else Icons.Rounded.Close, null); Spacer(Modifier.width(6.dp))
                    Text(if (ctl.musicUri == null) "موزیک پس‌زمینه (اختیاری)" else "حذف موزیک")
                }
                if (ctl.musicName.isNotBlank()) Text(ctl.musicName, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            }
            GradButton(
                if (ctl.busy) "در حال ساخت…" else "بساز", icon = Icons.Rounded.Movie, loading = ctl.busy,
                enabled = ctl.input.isNotBlank(), colors = listOf(Color(0xFFF857A6), Color(0xFFFF5858)),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                onClick = { ctl.generate() },
            )
            if (ctl.status.isNotBlank()) Text(ctl.status, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.primary)
            ErrorText(ctl.error)

            if (ctl.scenes.isNotEmpty()) {
                val i = ctl.index.coerceIn(0, ctl.scenes.lastIndex)
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                    Crossfade(targetState = i, animationSpec = tween(700), label = "scene") { idx ->
                        val zoom = remember(idx) { Animatable(1f) }
                        LaunchedEffect(idx) { zoom.snapTo(1f); zoom.animateTo(1.2f, tween(9000, easing = LinearEasing)) }
                        BitmapImage(
                            ctl.scenes[idx.coerceIn(0, ctl.scenes.lastIndex)].bmp, crop = true,
                            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(10.dp)) {
                        Text(ctl.scenes[i].text, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradButton(
                        if (ctl.playing) "توقف" else "پخش از ابتدا",
                        icon = if (ctl.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = { if (ctl.playing) ctl.stopPlay() else ctl.play() },
                    )
                    Text("${i + 1} / ${ctl.scenes.size}", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveTextButton(ctl.scenes.mapIndexed { n, sc -> "صحنه ${n + 1}: ${sc.text}" }.joinToString("\n\n"), "story", Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            if (canExport()) {
                                var okCount = 0
                                ctl.scenes.forEach { if (saveBitmapToGallery(ctx, it.bmp)) okCount++ }
                                toast(ctx, "$okCount تصویر در گالری ذخیره شد")
                            }
                        },
                        Modifier.weight(1f),
                    ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("ذخیره‌ی تصاویر") }
                }
                GradButton(
                    if (ctl.exporting) "در حال ساخت ویدیو… ${ctl.expProg}٪" else "ساخت و ذخیره‌ی ویدیو (MP4 با صدا)",
                    icon = Icons.Rounded.Movie, loading = ctl.exporting, enabled = !ctl.exporting && !ctl.busy,
                    colors = listOf(Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = {
                        if (canExport()) scope.launch {
                            ctl.stopPlay(); ctl.exporting = true; ctl.expProg = 0
                            val f = ctl.exportVideo()
                            ctl.exporting = false
                            if (f != null) {
                                val saved = withContext(Dispatchers.IO) { saveFileToGallery(ctx, f, "AIHub_video_${System.currentTimeMillis()}.mp4", "video/mp4") }
                                ctl.lastVideo = f
                                toast(ctx, if (saved) "ویدیو در گالری ذخیره شد (Movies/AIHub)" else "ساخته شد ولی ذخیره نشد")
                            } else toast(ctx, "ساخت ویدیو ناموفق بود. دوباره امتحان کن")
                        }
                    },
                )
                ctl.lastVideo?.let { f -> OutlinedButton({ shareFile(ctx, f, "video/mp4") }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک‌گذاری (واتساپ، استوری …)") } }
                Text(
                    "ویدیو با روایت صوتی و زیرنویس ساخته می‌شود. اگر صدای آنلاین در تنظیمات خاموش یا قطع باشد، ویدیو بی‌صدا ساخته می‌شود.",
                    Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
