package com.example.aihub.screens

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
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private class Scene(val bmp: Bitmap, val text: String)

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
                Text(
                    "این یک ویدیوی اسلایدی داخل اپ است. برای داشتن فایل ویدیو از «ضبط صفحه»ی گوشی استفاده کن.",
                    Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
