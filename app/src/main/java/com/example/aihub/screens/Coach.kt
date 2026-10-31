package com.example.aihub.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class Turn(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val translation: String = "",
    val correction: String = "",
    val isError: Boolean = false,
)

private val scenarios = listOf(
    "گفتگوی آزاد" to "Free friendly conversation about daily life and interests.",
    "رستوران" to "You are a waiter at a restaurant and the learner is a customer ordering food.",
    "فرودگاه و سفر" to "You are an airport / hotel staff member helping a traveler.",
    "مصاحبه‌ی کاری" to "You are an interviewer conducting a friendly job interview.",
    "خرید" to "You are a shop assistant and the learner is a customer shopping.",
    "مطب پزشک" to "You are a receptionist at a doctor's office and the learner is a patient making an appointment.",
    "معرفی خودت" to "You are meeting the learner for the first time at a social event and asking about them.",
)

@Composable
fun CoachScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val speaker = remember { Speaker(ctx) }
    val stt = remember { SpeechInput(ctx) }
    DisposableEffect(Unit) { onDispose { stt.release(); speaker.shutdown() } }

    val levels = listOf("مبتدی", "متوسط", "پیشرفته")
    val langs = remember { Langs.drop(1) }
    var lang by remember { mutableStateOf(langs[0]) }
    var level by remember { mutableStateOf(levels[0]) }
    var scenario by remember { mutableStateOf(scenarios[0]) }
    var started by remember { mutableStateOf(false) }
    val turns = remember { mutableStateListOf<Turn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var autoSpeak by remember { mutableStateOf(Store.autoSpeak) }
    var nextId by remember { mutableStateOf(0L) }
    var histId by remember { mutableStateOf(-1L) }
    var job by remember { mutableStateOf<Job?>(null) }
    var speakJob by remember { mutableStateOf<Job?>(null) }

    fun speak(t: String) {
        speakJob?.cancel()
        speakJob = scope.launch { Tts.say(ctx, speaker, t, lang, 0.9f, onNote = { toast(ctx, it) }) }
    }

    fun saveHistory() {
        val body = turns.filter { !it.isError }.joinToString("\n\n") {
            if (it.isUser) "🧑 ${it.text}" + (if (it.correction.isNotBlank()) "\n✏️ ${it.correction}" else "")
            else "🤖 ${it.text}\n(${it.translation})"
        }
        histId = History.update(histId, "LANG_COACH", "${lang.label} · ${scenario.first}", body)
    }

    /** یک نوبت گفتگو؛ اگر userText خالی باشد، ربات شروع‌کننده است */
    fun ask(userText: String?) {
        if (busy) return
        busy = true
        job = scope.launch {
            try {
                val hist = ArrayList<ChatMsg>()
                hist.add(ChatMsg(0, true, "Start the role-play now with a short greeting and your first simple question."))
                turns.filter { !it.isError }.forEach { hist.add(ChatMsg(it.id + 1, it.isUser, it.text)) }
                val raw = Ai.generateWith(
                    hist,
                    system = "You are a friendly ${lang.name} conversation partner and teacher for a Persian-speaking learner at $level level. " +
                        "Scenario: ${scenario.second} Stay in the role-play. Keep each reply short (1-3 sentences), use vocabulary suited to the level, " +
                        "and end with a simple question. Return ONLY JSON: {\"reply\":\"your reply in ${lang.name}\"," +
                        "\"translation\":\"Persian translation of your reply\"," +
                        "\"correction\":\"If the learner's LAST message has grammar/word mistakes, write the corrected sentence followed by a very short " +
                        "Persian explanation; otherwise an empty string\"}",
                    temperature = 0.7, json = true, maxTokens = 700,
                )
                val o = JSONObject(raw.stripCodeFence())
                val reply = o.optString("reply").trim()
                if (reply.isEmpty()) throw AiException("پاسخی دریافت نشد. دوباره امتحان کن.")
                // اصلاح مربوط به آخرین پیام کاربر است
                val corr = o.optString("correction").trim().let { if (it.equals("null", true)) "" else it }
                if (userText != null && corr.isNotBlank()) {
                    val i = turns.indexOfLast { it.isUser }
                    if (i >= 0) turns[i] = turns[i].copy(correction = corr)
                }
                turns.add(Turn(nextId++, false, reply, o.optString("translation")))
                saveHistory()
                if (autoSpeak) speak(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                turns.add(Turn(nextId++, false, e.message ?: "خطا", isError = true))
            } finally {
                busy = false
            }
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || busy) return
        input = ""
        turns.add(Turn(nextId++, true, t))
        ask(t)
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) stt.start(lang.tag, { input = it }, { toast(ctx, it) }) else toast(ctx, "اجازه‌ی میکروفون لازم است")
    }

    ToolScaffold(
        "مربی زبان", { if (started) { started = false; job?.cancel(); speakJob?.cancel(); turns.clear(); busy = false; histId = -1L } else onBack() },
        actions = {
            if (started) {
                IconButton(onClick = { autoSpeak = !autoSpeak; Store.saveAutoSpeak(autoSpeak) }) {
                    Icon(if (autoSpeak) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, "خواندن خودکار")
                }
            }
        },
    ) {
        if (!started) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "با ربات به زبان مقصد حرف بزن؛ هر جا اشتباه کنی، همان لحظه اصلاح و توضیح فارسی می‌گیری.",
                    Modifier.padding(horizontal = 18.dp), fontSize = 13.5.sp, lineHeight = 22.sp, color = c.onBackground.copy(alpha = 0.8f),
                )
                SectionTitle("زبان"); ChipRow(langs, lang, { it.label }) { lang = it }
                SectionTitle("سطح"); ChipRow(levels, level, { it }) { level = it }
                SectionTitle("موقعیت"); ChipRow(scenarios, scenario, { it.first }) { scenario = it }
                GradButton(
                    "شروع مکالمه", icon = Icons.Rounded.Forum, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = listOf(Color(0xFF00C9A7), Color(0xFF3FA2F7)),
                    onClick = { started = true; turns.clear(); histId = -1L; ask(null) },
                )
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(turns.size, busy) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1) }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(turns, key = { it.id }) { t ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (t.isUser) Alignment.Start else Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = when { t.isError -> c.errorContainer; t.isUser -> c.primary; else -> c.surfaceVariant },
                            modifier = Modifier.widthIn(max = 320.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                val fg = when { t.isError -> c.onErrorContainer; t.isUser -> Color.White; else -> c.onSurfaceVariant }
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(t.text, color = fg, fontSize = 15.5.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f, fill = false))
                                    if (!t.isUser && !t.isError) {
                                        IconButton(onClick = { speak(t.text) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Rounded.VolumeUp, "پخش", tint = fg, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                if (t.translation.isNotBlank()) {
                                    Text(t.translation, color = fg.copy(alpha = 0.7f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        if (t.correction.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF1CC),
                                modifier = Modifier.padding(top = 4.dp).widthIn(max = 320.dp),
                            ) {
                                Text("✏️ ${t.correction}", Modifier.padding(10.dp), fontSize = 13.sp, lineHeight = 21.sp, color = Color(0xFF5A4300))
                            }
                        }
                    }
                }
                if (busy) item { Text("…", Modifier.padding(8.dp), fontSize = 20.sp, color = c.primary) }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (stt.listening) stt.finish()
                    else if (!stt.available()) toast(ctx, "تشخیص گفتار گوگل روی این گوشی نیست")
                    else if (hasMic(ctx)) stt.start(lang.tag, { input = it }, { toast(ctx, it) })
                    else perm.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(if (stt.listening) Icons.Rounded.Stop else Icons.Rounded.Mic, "میکروفون", tint = c.primary) }
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("به ${lang.label} بنویس یا بگو…") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = c.surface.copy(alpha = 0.92f), unfocusedContainerColor = c.surface.copy(alpha = 0.92f),
                    ),
                )
                IconButton(onClick = { send() }, enabled = input.isNotBlank() && !busy) {
                    Icon(Icons.Rounded.Check, "ارسال", tint = c.primary)
                }
            }
        }
    }
}
