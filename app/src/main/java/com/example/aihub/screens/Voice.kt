package com.example.aihub.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VState { IDLE, LISTENING, THINKING, SPEAKING }

/** یک پیام صوتی در گفتگو؛ pcm ضبط کاربر، mp3 تکه‌های صدای ربات */
private data class VMsg(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val pcm: ShortArray? = null,
    val mp3: List<ByteArray> = emptyList(),
    val isError: Boolean = false,
)

private class VoiceCtl(val ctx: Context, val scope: CoroutineScope) {
    val speaker = Speaker(ctx)
    val stt = SpeechInput(ctx)
    val rec = PcmRecorder()
    val msgs = mutableStateListOf<VMsg>()
    var state by mutableStateOf(VState.IDLE)
    var info by mutableStateOf("برای شروع روی میکروفون بزن")
    var continuous by mutableStateOf(false)
    var lang by mutableStateOf(Langs[0])
    var level by mutableStateOf(0f)
    var playingId by mutableStateOf(-1L)
    private var id = 0L
    private var job: Job? = null
    private var playJob: Job? = null
    private var histId = -1L
    var voiceMissing by mutableStateOf(false)
    var detail by mutableStateOf("")

    private fun saveHistory() {
        val ok = msgs.filter { !it.isError && it.text.isNotBlank() }
        if (ok.isEmpty()) return
        val body = ok.joinToString("\n\n") { (if (it.isUser) "🎙️ " else "🤖 ") + it.text }
        histId = History.update(histId, "VOICE_CHAT", ok.first { it.isUser }.text, body)
    }

    /** با Whisper (کلید Groq) ضبط و تشخیص می‌دهیم؛ وگرنه با تشخیص گفتار گوگل گوشی */
    val useWhisper: Boolean get() = Ai.canTranscribe()

    fun listen() {
        stopPlayback()
        if (useWhisper) listenWhisper() else listenDevice()
    }

    private fun listenDevice() {
        if (!stt.available()) {
            info = "تشخیص گفتار گوگل روی این گوشی نیست. برنامه‌ی Google را نصب کن."
            return
        }
        state = VState.LISTENING
        info = "در حال گوش دادن… (برای ارسال دوباره بزن)"
        stt.start(lang.tag, onText = { handleText(it, null) }, onFail = { state = VState.IDLE; info = it })
    }

    private fun listenWhisper() {
        state = VState.LISTENING
        info = "در حال گوش دادن… (بعد از سکوت خودکار ارسال می‌شود)"
        job = scope.launch {
            try {
                val pcm = rec.record(30000, autoStop = true) { level = it }
                if (pcm.size < rec.sampleRate / 3) {
                    state = VState.IDLE; info = "چیزی نشنیدم، دوباره امتحان کن."
                    return@launch
                }
                state = VState.THINKING
                info = "در حال تشخیص گفتار…"
                val text = Ai.transcribe(pcmToWav(pcm, rec.sampleRate), lang.tag.substringBefore('-'))
                if (text.isBlank()) {
                    state = VState.IDLE; info = "چیزی نشنیدم، دوباره امتحان کن."
                    return@launch
                }
                handleText(text, pcm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msgs.add(VMsg(id++, false, e.message ?: "خطا", isError = true))
                state = VState.IDLE
                info = "دوباره امتحان کن"
            }
        }
    }

    /** پایان دادن به ضبط و ارسال (وقتی کاربر دوباره روی دکمه می‌زند) */
    fun finishListening() {
        if (useWhisper) rec.stop() else stt.finish()
    }

    private fun handleText(text: String, pcm: ShortArray?) {
        msgs.add(VMsg(id++, true, text, pcm = pcm))
        state = VState.THINKING
        info = "در حال فکر کردن…"
        val history = msgs.filter { !it.isError }.map { ChatMsg(it.id, it.isUser, it.text) }
        job = scope.launch {
            try {
                val reply = Ai.generateWith(
                    history,
                    system = "You are a friendly, smart voice assistant. Answer in ${lang.name}. Talk naturally like a person, " +
                        "in clear spoken sentences (usually 2 to 6). If the user asks for a story, explanation or details, " +
                        "give a complete answer. Never use markdown, lists, emojis or symbols.",
                    temperature = 0.8,
                    maxTokens = 900,
                )
                val msgId = id++
                msgs.add(VMsg(msgId, false, reply))
                saveHistory()
                state = VState.SPEAKING
                info = "در حال صحبت…"
                voiceMissing = false
                speakReply(msgId, plainForSpeech(reply))
                state = VState.IDLE
                if (voiceMissing) {
                    detail = Poll.lastError.ifBlank { "صدای آنلاین خاموش است" }
                    info = "صدای فارسی پخش نشد؛ راهنمای نصب صدا را ببین"
                } else {
                    info = "دوباره روی میکروفون بزن"
                }
                if (continuous) listen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msgs.add(VMsg(id++, false, e.message ?: "خطا", isError = true))
                state = VState.IDLE
                info = "دوباره امتحان کن"
            }
        }
    }

    /** خواندن پاسخ: اول صدای آنلاین (طبیعی)، اگر نشد صدای خود گوشی */
    private suspend fun speakReply(msgId: Long, text: String) {
        if (text.isBlank()) return
        var rest = splitSpeech(text, 200)
        if (Store.voiceOnline) {
            val got = ArrayList<ByteArray>()
            try {
                coroutineScope {
                    var next = async { Poll.speech(rest[0], Store.botVoice, lang.tag.substringBefore('-')) }
                    for (i in rest.indices) {
                        val bytes = next.await()
                        if (i + 1 < rest.size) next = async { Poll.speech(rest[i + 1], Store.botVoice, lang.tag.substringBefore('-')) }
                        got.add(bytes)
                        playingId = msgId
                        try { playAudioBytes(ctx, bytes) } finally { playingId = -1 }
                    }
                }
                saveAudio(msgId, got)
                return
            } catch (e: CancellationException) {
                saveAudio(msgId, got)
                throw e
            } catch (e: Exception) {
                saveAudio(msgId, got)
                info = "صدای آنلاین در دسترس نبود؛ از صدای گوشی استفاده می‌شود"
                rest = rest.drop(got.size)
            }
        }
        if (!speaker.hasVoice(lang.locale)) {
            voiceMissing = true
            return
        }
        for (part in rest) speaker.speak(part, locale = lang.locale)
    }

    private fun saveAudio(msgId: Long, parts: List<ByteArray>) {
        if (parts.isEmpty()) return
        val i = msgs.indexOfFirst { it.id == msgId }
        if (i >= 0) msgs[i] = msgs[i].copy(mp3 = parts)
    }

    /** پخش دوباره‌ی یک پیام */
    fun replay(m: VMsg) {
        if (state == VState.LISTENING || state == VState.THINKING) return
        if (playingId == m.id) { stopPlayback(); return }
        stopPlayback()
        playJob = scope.launch {
            playingId = m.id
            try {
                if (m.isUser) {
                    m.pcm?.let { playPcm(it, rec.sampleRate) }
                } else if (m.mp3.isNotEmpty()) {
                    for (b in m.mp3) playAudioBytes(ctx, b)
                } else {
                    for (part in splitSpeech(plainForSpeech(m.text))) speaker.speak(part, locale = lang.locale)
                }
            } finally {
                playingId = -1
            }
        }
    }

    fun stopPlayback() { playJob?.cancel(); speaker.stop(); playingId = -1 }

    fun stopAll() {
        job?.cancel(); rec.stop(); stt.release(); stopPlayback()
        state = VState.IDLE; level = 0f; info = "متوقف شد"
    }

    fun release() { job?.cancel(); playJob?.cancel(); rec.stop(); stt.release(); speaker.shutdown() }
}

@Composable
fun VoiceChatScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember { VoiceCtl(ctx, scope) }
    DisposableEffect(Unit) { onDispose { ctl.release() } }
    var online by remember { mutableStateOf(Store.voiceOnline) }
    var voice by remember { mutableStateOf(BotVoices.firstOrNull { it.id == Store.botVoice } ?: BotVoices[0]) }
    val c = MaterialTheme.colorScheme

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ctl.listen() else toast(ctx, "بدون اجازه‌ی میکروفون چت صوتی کار نمی‌کند")
    }
    fun onOrb() {
        when (ctl.state) {
            VState.LISTENING -> ctl.finishListening()
            VState.IDLE -> if (hasMic(ctx)) ctl.listen() else perm.launch(Manifest.permission.RECORD_AUDIO)
            else -> ctl.stopAll()
        }
    }

    if (ctl.voiceMissing) VoiceHelpDialog(lang = ctl.lang.label, detail = ctl.detail) { ctl.voiceMissing = false }
    ToolScaffold("چت صوتی", onBack) {
        ChipRow(Langs.take(8), ctl.lang, { it.label }) { ctl.lang = it }
        Spacer(Modifier.height(6.dp))
        if (online) {
            ChipRow(BotVoices, voice, { it.label }) {
                voice = it
                Store.saveFlags(Store.autoFallback, online, it.id)
            }
        }
        Text(
            if (ctl.useWhisper) "تشخیص گفتار: Whisper (دقیق، خودکار بعد از سکوت)" else "تشخیص گفتار: گوگل گوشی (برای دقت بیشتر در تنظیمات کلید Groq بگذار)",
            Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 11.5.sp,
            color = c.onBackground.copy(alpha = 0.6f),
        )

        val listState = rememberLazyListState()
        LaunchedEffect(ctl.msgs.size) { if (ctl.msgs.isNotEmpty()) listState.animateScrollToItem(ctl.msgs.size - 1) }
        if (ctl.msgs.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.RecordVoiceOver, null, tint = c.primary, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(10.dp))
                Text("با ربات حرف بزن", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "میکروفون را بزن، سوالت را بگو؛ ربات با صدا جواب می‌دهد. هر پیام را می‌توانی دوباره پخش کنی.",
                    fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ctl.msgs, key = { it.id }) { m -> VoiceBubble(m, ctl.playingId == m.id) { ctl.replay(m) } }
            }
        }
        if (ctl.stt.listening && ctl.stt.partial.isNotBlank()) {
            Text(
                ctl.stt.partial, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 14.sp,
                color = c.primary,
            )
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Orb(ctl.state, maxOf(ctl.level, ctl.stt.level), ::onOrb)
            Spacer(Modifier.height(6.dp))
            Text(ctl.info, fontSize = 13.5.sp, color = c.onBackground.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ctl.continuous, onCheckedChange = { ctl.continuous = it })
                Spacer(Modifier.width(8.dp))
                Text("گفتگوی پیوسته", fontSize = 12.5.sp)
                Spacer(Modifier.width(18.dp))
                Switch(checked = online, onCheckedChange = {
                    online = it
                    Store.saveFlags(Store.autoFallback, it, voice.id)
                })
                Spacer(Modifier.width(8.dp))
                Text("صدای آنلاین", fontSize = 12.5.sp)
            }
        }
    }
}

/** راهنمای نصب صدای زبان وقتی نه صدای آنلاین جواب داد نه صدای گوشی */
@Composable
private fun VoiceHelpDialog(lang: String, detail: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var showDetail by remember { mutableStateOf(false) }
    fun open(intent: android.content.Intent) {
        try {
            ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(ctx, "باز نشد؛ از تنظیمات گوشی دستی انجام بده")
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("صدای $lang نصب نیست") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "نه سرویس آنلاین جواب داد و نه گوشی صدای $lang دارد. با یک موتور صدای رایگان مثل eSpeak NG حل می‌شود:\n\n" +
                        "۱) eSpeak NG را نصب کن.\n" +
                        "۲) «تنظیمات صدای گوشی» را باز کن و موتور پیش‌فرض را روی eSpeak NG بگذار.\n" +
                        "۳) برگرد و دوباره امتحان کن.\n\n" +
                        "صدای eSpeak کمی رباتی است. با اشتراک، صدای طبیعی‌تر از سرور پخش می‌شود.",
                    fontSize = 13.5.sp,
                )
                if (showDetail) {
                    Spacer(Modifier.height(8.dp))
                    Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    open(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://f-droid.org/packages/com.reecedunn.espeak/")))
                }) { Text("نصب eSpeak NG") }
                TextButton(onClick = { open(android.content.Intent("com.android.settings.TTS_SETTINGS")) }) { Text("تنظیمات صدای گوشی") }
                TextButton(onClick = { showDetail = !showDetail }) { Text("جزئیات فنی") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("بستن") } },
    )
}

@Composable
private fun VoiceBubble(m: VMsg, playing: Boolean, onPlay: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val bg = when {
        m.isError -> c.errorContainer
        m.isUser -> c.primary
        else -> c.surfaceVariant
    }
    val fg = when {
        m.isError -> c.onErrorContainer
        m.isUser -> Color.White
        else -> c.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.isUser) Arrangement.Start else Arrangement.End) {
        Surface(color = bg, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!m.isError) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            if (playing) "توقف" else "پخش", tint = fg,
                        )
                    }
                }
                Text(m.text, color = fg, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun Orb(state: VState, level: Float, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "orb")
    val pulse by inf.animateFloat(
        1f, 1.1f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p",
    )
    val lv by animateFloatAsState(level, label = "lv")
    val scale = when (state) {
        VState.LISTENING -> 1f + lv * 0.28f
        VState.THINKING, VState.SPEAKING -> pulse
        VState.IDLE -> 1f
    }
    val colors = when (state) {
        VState.LISTENING -> listOf(Color(0xFFFF6FB1), Color(0xFFFF9A62))
        VState.THINKING -> listOf(Color(0xFFFFB74D), Color(0xFFFF8A5C))
        VState.SPEAKING -> listOf(Color(0xFF12C2A7), Color(0xFF2EC4F1))
        VState.IDLE -> Brand
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        Box(
            Modifier.size(132.dp).graphicsLayer { scaleX = scale * 1.08f; scaleY = scale * 1.08f }
                .background(colors[0].copy(alpha = 0.18f), CircleShape)
        )
        Box(
            Modifier.size(92.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .background(grad(colors), CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (state) {
                    VState.IDLE -> Icons.Rounded.Mic
                    VState.LISTENING -> Icons.Rounded.Check
                    else -> Icons.Rounded.Stop
                },
                null, tint = Color.White, modifier = Modifier.size(40.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- تغییر صدا

private class VcCtl(val scope: CoroutineScope) {
    val rec = PcmRecorder()
    var recording by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var data by mutableStateOf<ShortArray?>(null)
    var fx by mutableStateOf(VoiceFx.CHIPMUNK)
    var secs by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)
    private var job: Job? = null

    fun start() {
        if (recording) return
        stopPlay()
        error = null; data = null; secs = 0; recording = true
        job = scope.launch {
            val timer = launch { while (true) { delay(1000); secs++ } }
            try {
                data = rec.record(15000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "خطا در ضبط"
            } finally {
                timer.cancel(); recording = false
            }
        }
    }

    fun stopRec() { rec.stop() }

    fun play() {
        val d = data ?: return
        stopPlay()
        playing = true
        job = scope.launch {
            try {
                val (s, r) = withContext(Dispatchers.Default) { applyFx(d, fx, rec.sampleRate) }
                playPcm(s, r)
            } finally {
                playing = false
            }
        }
    }

    fun stopPlay() {
        if (playing) { job?.cancel(); playing = false }
    }

    fun release() { rec.stop(); job?.cancel() }
}

@Composable
fun VoiceChangerScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(0) }
    ToolScaffold("تغییر صدا", onBack) {
        ChipRow(listOf(0, 1), mode, { if (it == 0) "افکت روی صدای من" else "صدای دیگر (هوش مصنوعی)" }) { mode = it }
        Spacer(Modifier.height(8.dp))
        if (mode == 0) EffectMode() else OtherVoiceMode()
    }
}

@Composable
private fun EffectMode() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember { VcCtl(scope) }
    DisposableEffect(Unit) { onDispose { ctl.release() } }
    val c = MaterialTheme.colorScheme

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ctl.start() else toast(ctx, "اجازه‌ی میکروفون لازم است")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "یک جمله بگو (تا ۱۵ ثانیه)، بعد افکت رو انتخاب کن و بشنو!",
            fontSize = 14.sp, color = c.onBackground.copy(alpha = 0.7f),
        )
        RecordButton(ctl.recording) {
            if (ctl.recording) ctl.stopRec()
            else if (hasMic(ctx)) ctl.start() else perm.launch(Manifest.permission.RECORD_AUDIO)
        }
        Text(
            if (ctl.recording) "در حال ضبط… ${ctl.secs} ثانیه (برای پایان بزن)"
            else if (ctl.data != null) "ضبط آماده است ✔" else "برای شروع ضبط بزن",
            fontWeight = FontWeight.Bold,
        )
        ErrorText(ctl.error)
        if (ctl.data != null && !ctl.recording) {
            SectionTitle("افکت صدا")
            val rows = VoiceFx.values().toList().chunked(4)
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { fx ->
                        val sel = ctl.fx == fx
                        Surface(
                            onClick = { ctl.fx = fx },
                            shape = RoundedCornerShape(50),
                            color = if (sel) c.primary else c.surfaceVariant,
                        ) {
                            Text(
                                fx.label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = if (sel) Color.White else c.onSurfaceVariant, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            GradButton(
                text = if (ctl.playing) "توقف" else "پخش با افکت",
                icon = if (ctl.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                onClick = { if (ctl.playing) ctl.stopPlay() else ctl.play() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(110.dp)
            .background(
                grad(if (recording) listOf(Color(0xFFFF5C8A), Color(0xFFFF9A62)) else listOf(Color(0xFF12C2A7), Color(0xFF2EC4F1))),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(48.dp))
    }
}

/** حالت دوم: گفته‌ی تو (یا متن نوشته‌شده) با یک صدای آماده‌ی دیگر خوانده می‌شود */
@Composable
private fun OtherVoiceMode() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember { VcCtl(scope) }
    DisposableEffect(Unit) { onDispose { ctl.release() } }
    val c = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf(BotVoices[3]) }
    var result by remember { mutableStateOf<ByteArray?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    val recog = remember { TaskState() }
    val gen = remember { TaskState() }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ctl.start() else toast(ctx, "اجازه‌ی میکروفون لازم است")
    }

    // وقتی ضبط تمام شد، اگر کلید Groq هست خودکار به متن تبدیل کن
    LaunchedEffect(ctl.data, ctl.recording) {
        val d = ctl.data
        if (d != null && !ctl.recording && Ai.canTranscribe()) {
            recog.launch(scope) {
                text = Ai.transcribe(pcmToWav(d, ctl.rec.sampleRate), null)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.surfaceVariant) {
            Text(
                "جمله‌ات را بگو یا بنویس؛ برنامه آن را با صدای دیگری (زن، مرد، بم، قصه‌گو…) می‌خواند و می‌توانی ذخیره کنی. " +
                    "این صداها آماده‌ی خود هوش مصنوعی‌اند؛ شبیه‌سازی صدای افراد واقعی (کلون صدا) پشتیبانی نمی‌شود.",
                Modifier.padding(12.dp), fontSize = 12.5.sp, color = c.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordButton(ctl.recording) {
                if (ctl.recording) ctl.stopRec()
                else if (hasMic(ctx)) ctl.start() else perm.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Text(
            when {
                ctl.recording -> "در حال ضبط… ${ctl.secs} ثانیه (برای پایان بزن)"
                recog.loading -> "در حال تبدیل گفتار به متن…"
                !Ai.canTranscribe() -> "اتصال به سرور برقرار نیست؛ متن را همین پایین بنویس."
                else -> "ضبط کن یا متن را بنویس"
            },
            Modifier.fillMaxWidth(), fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.75f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        ErrorText(ctl.error ?: recog.error)
        AppField(text, { text = it }, "متنی که باید گفته شود", minLines = 3, maxLines = 8)
        SectionTitle("صدای مقصد")
        ChipRow(BotVoices, voice, { it.label }) { voice = it }
        GradButton(
            "ساخت با این صدا", icon = Icons.Rounded.GraphicEq, loading = gen.loading, enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(Color(0xFF12C2A7), Color(0xFF2EC4F1)),
            onClick = {
                gen.launch(scope) {
                    val b = Poll.speech(text, voice.id)
                    result = b
                    History.add("VOICE_CHANGER", text, "صدای انتخابی: ${voice.label}\n\n$text", b, "mp3")
                    playJob?.cancel()
                    playing = true
                    playJob = scope.launch { try { playAudioBytes(ctx, b) } finally { playing = false } }
                }
            },
        )
        ErrorText(gen.error)
        result?.let { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GradButton(
                    if (playing) "توقف" else "پخش دوباره",
                    icon = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (playing) { playJob?.cancel(); playing = false } else {
                            playing = true
                            playJob = scope.launch { try { playAudioBytes(ctx, r) } finally { playing = false } }
                        }
                    },
                )
                GradButton(
                    "ذخیره", icon = Icons.Rounded.Download, modifier = Modifier.weight(1f),
                    onClick = {
                        if (canExport()) {
                            val ok = saveBytesToDownloads(ctx, "AIHub_voice_${System.currentTimeMillis()}.mp3", "audio/mpeg", r)
                            toast(ctx, if (ok) "در پوشه‌ی Download/AIHub ذخیره شد" else "ذخیره نشد")
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
