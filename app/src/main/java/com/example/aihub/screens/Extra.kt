package com.example.aihub.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.Reminder
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val FA_SYS = "Reply in Persian (Farsi) unless the user clearly asks for another language. Be clear, practical and well organized. Use simple markdown (bold, bullet lists)."

// ---------------------------------------------------------------- عکس‌خوان

private val visionTasks = listOf(
    "توضیح تصویر" to "Describe this image in detail.",
    "استخراج متن (OCR)" to "Extract ALL text visible in this image exactly as written, preserving line breaks. Output only the text.",
    "ترجمه‌ی متن عکس" to "Read all text in this image and translate it to Persian. Show the original text first, then the Persian translation.",
    "تشخیص (شیء، گیاه، غذا)" to "Identify the main objects, plants, animals or food in this image and give short useful facts.",
    "حل سوال" to "If the image contains a question or exercise, solve it step by step and explain clearly.",
    "کپشن و هشتگ" to "Write 3 catchy social media captions for this photo, each with fitting emojis and hashtags.",
)

@Composable
fun VisionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var sel by remember { mutableStateOf(visionTasks[0]) }
    var extra by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, uri, 1280) }
            if (b != null) {
                bmp = b; bytes = withContext(Dispatchers.Default) { b.toJpegBytes(85) }; result = ""
            } else toast(ctx, "تصویر خوانده نشد")
        }
    }

    ToolScaffold("عکس‌خوان", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaBar(Modifier.padding(horizontal = 16.dp), onImage = { b ->
                bmp = b; result = ""
                scope.launch { bytes = withContext(Dispatchers.Default) { b.toJpegBytes(85) } }
            })
            bmp?.let { b ->
                BitmapImage(b, Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(20.dp)))
                SectionTitle("چه کاری انجام بشه؟")
                ChipRow(visionTasks, sel, { it.first }) { sel = it }
                AppField(extra, { extra = it }, "سوال اضافه (اختیاری)", Modifier.padding(horizontal = 16.dp), maxLines = 3)
                GradButton(
                    "تحلیل کن", icon = Icons.Rounded.AutoAwesome, loading = task.loading,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
                    onClick = {
                        task.launch(scope) {
                            val q = sel.second + if (extra.isNotBlank()) "\nUser question: $extra" else ""
                            result = Ai.generate(q, system = FA_SYS, image = bytes, temperature = 0.4, maxTokens = 1800)
                            History.add("VISION", sel.first, result, bytes, "jpg")
                        }
                    },
                )
            } ?: Text(
                "یک عکس انتخاب کن؛ می‌توانم توضیحش بدهم، متنش را بخوانم، ترجمه کنم یا سوال داخلش را حل کنم.",
                Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
            ErrorText(task.error)
            if (result.isNotBlank()) ResultCard(result, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- خلاصه‌ساز

private val summaryModes = listOf(
    "خلاصه‌ی کوتاه" to "Summarize the following in 3-4 clear sentences.",
    "نکات کلیدی" to "Extract the 6-10 most important key points as a bullet list.",
    "خلاصه‌ی کامل با تیتر" to "Write a structured summary with short headings and bullet points covering all main ideas.",
    "سوال و جواب" to "Create 5 important questions about this text, each with a short answer.",
    "ساده‌سازی" to "Rewrite the text in very simple words as if explaining to a 12-year-old.",
    "کارها و تاریخ‌ها" to "Extract every action item, deadline, date, number and name mentioned, as a checklist.",
)

@Composable
fun SummaryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    val clip = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(summaryModes[0]) }
    var result by remember { mutableStateOf("") }

    ToolScaffold("خلاصه‌ساز", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppField(
                input, { input = it }, "متن را بچسبان یا لینک مقاله بگذار", Modifier.padding(horizontal = 16.dp),
                minLines = 6, maxLines = 14,
                trailing = {
                    IconButton(onClick = { clip.getText()?.text?.let { input = it } }) {
                        Icon(Icons.Rounded.ContentPaste, "چسباندن")
                    }
                },
            )
            ChipRow(summaryModes, sel, { it.first }) { sel = it }
            GradButton(
                "خلاصه کن", icon = Icons.Rounded.Summarize, loading = task.loading, enabled = input.isNotBlank(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                onClick = {
                    task.launch(scope) {
                        val raw = input.trim()
                        val src = if (raw.startsWith("http://") || raw.startsWith("https://")) Net.pageText(raw.substringBefore(' ')) else raw.take(12000)
                        result = Ai.generate("${sel.second}\n\nTEXT:\n$src", system = FA_SYS, temperature = 0.3, maxTokens = 1800)
                        History.add("SUMMARY", "${sel.first}: ${raw.take(40)}", result)
                    }
                },
            )
            ErrorText(task.error)
            if (result.isNotBlank()) ResultCard(result, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- آزمون‌ساز

private class QItem(val q: String, val opts: List<String>, val ans: Int, val exp: String)

private fun parseQuiz(raw: String): List<QItem> {
    val arr = JSONObject(raw.stripCodeFence()).getJSONArray("questions")
    val out = ArrayList<QItem>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val op = o.getJSONArray("options")
        val opts = (0 until op.length()).map { op.optString(it) }
        if (opts.size < 2) continue
        out.add(QItem(o.optString("q"), opts, o.optInt("answer", 0).coerceIn(0, opts.size - 1), o.optString("explanation")))
    }
    if (out.isEmpty()) throw AiException("آزمون ساخته نشد. دوباره امتحان کن.")
    return out
}

@Composable
fun QuizScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    val c = MaterialTheme.colorScheme
    var topic by remember { mutableStateOf("") }
    val levels = listOf("آسان", "متوسط", "سخت")
    var level by remember { mutableStateOf(levels[1]) }
    var count by remember { mutableStateOf(5) }
    var quiz by remember { mutableStateOf<List<QItem>>(emptyList()) }
    var idx by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(-1) }
    var score by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val letters = listOf("الف", "ب", "ج", "د", "ه")

    ToolScaffold("آزمون‌ساز", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (quiz.isEmpty()) {
                AppField(topic, { topic = it }, "موضوع آزمون (مثلاً تاریخ ایران، فیزیک، زبان انگلیسی)", Modifier.padding(horizontal = 16.dp), minLines = 2)
                MediaBar(Modifier.padding(horizontal = 16.dp), onOcrText = { topic = it }, onText = { topic = it }, onSpeech = { topic = (topic + " " + it).trim() })
                SectionTitle("سطح"); ChipRow(levels, level, { it }) { level = it }
                SectionTitle("تعداد سوال"); ChipRow(listOf(5, 8, 10), count, { "$it سوال" }) { count = it }
                GradButton(
                    "بساز", icon = Icons.Rounded.Quiz, loading = task.loading, enabled = topic.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB)),
                    onClick = {
                        task.launch(scope) {
                            val raw = Ai.generate(
                                "Topic: $topic\nLevel: $level\nNumber of questions: $count",
                                system = "You create multiple-choice quizzes in Persian. Return ONLY JSON: {\"questions\":[{\"q\":\"question\"," +
                                    "\"options\":[\"a\",\"b\",\"c\",\"d\"],\"answer\":0,\"explanation\":\"one short sentence why\"}]}. " +
                                    "answer is the zero-based index of the correct option. Exactly 4 options. Vary the correct position. Facts must be accurate.",
                                json = true, temperature = 0.7, maxTokens = 4000,
                            )
                            quiz = parseQuiz(raw); idx = 0; chosen = -1; score = 0; done = false
                        }
                    },
                )
                ErrorText(task.error)
            } else if (done) {
                Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = c.surfaceVariant) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val pct = score * 100 / quiz.size
                        Text(if (pct >= 80) "🏆" else if (pct >= 50) "👏" else "💪", fontSize = 56.sp)
                        Text("نمره‌ی تو: $score از ${quiz.size}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.onSurfaceVariant)
                        Text(
                            if (pct >= 80) "عالی بود!" else if (pct >= 50) "خوب بود؛ با مرور بهتر هم می‌شود." else "نگران نباش، دوباره امتحان کن.",
                            color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                SaveTextButton(
                    quiz.mapIndexed { n, it ->
                        "${n + 1}. ${it.q}\n" + it.opts.mapIndexed { j, o -> "   ${letters.getOrElse(j) { "" }}) $o" }.joinToString("\n") +
                            "\nپاسخ: ${letters.getOrElse(it.ans) { "" }}\n${it.exp}"
                    }.joinToString("\n\n"),
                    "quiz", Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
                GradButton("آزمون جدید", icon = Icons.Rounded.Refresh, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    onClick = { quiz = emptyList(); topic = "" })
            } else {
                val q = quiz[idx]
                LinearProgressIndicator(
                    progress = { (idx + 1f) / quiz.size }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
                Text("سوال ${idx + 1} از ${quiz.size}", Modifier.padding(horizontal = 18.dp), fontSize = 12.5.sp, color = c.primary)
                Text(q.q, Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 27.sp)
                q.opts.forEachIndexed { i, o ->
                    val bg = when {
                        chosen == -1 -> c.surfaceVariant
                        i == q.ans -> Color(0xFF1E9E5A)
                        i == chosen -> Color(0xFFD64545)
                        else -> c.surfaceVariant
                    }
                    val fg = if (chosen != -1 && (i == q.ans || i == chosen)) Color.White else c.onSurfaceVariant
                    Surface(
                        onClick = { if (chosen == -1) { chosen = i; if (i == q.ans) score++ } },
                        shape = RoundedCornerShape(16.dp), color = bg, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${letters.getOrElse(i) { "" }}) ", fontWeight = FontWeight.Bold, color = fg)
                            Text(o, color = fg, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
                if (chosen != -1) {
                    if (q.exp.isNotBlank()) {
                        Text("💡 ${q.exp}", Modifier.padding(horizontal = 18.dp), fontSize = 13.5.sp, lineHeight = 22.sp)
                    }
                    GradButton(
                        if (idx == quiz.lastIndex) "پایان و دیدن نمره" else "سوال بعدی",
                        icon = Icons.Rounded.SkipNext, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        onClick = {
                            if (idx == quiz.lastIndex) {
                                done = true
                                History.add(
                                    "QUIZ", topic, "نمره: $score از ${quiz.size} ($level)\n\n" +
                                        quiz.mapIndexed { n, it -> "${n + 1}. ${it.q}\n✔ ${it.opts[it.ans]}" }.joinToString("\n\n"),
                                )
                            } else { idx++; chosen = -1 }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- برنامه‌ریز

private data class PlanItem(val t: String, val done: Boolean)
private data class Plan(val id: Long, val title: String, val items: List<PlanItem>)

private fun loadPlans(): List<Plan> = try {
    val arr = JSONArray(Store.getStr("plans").ifBlank { "[]" })
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val its = o.getJSONArray("items")
        Plan(o.getLong("id"), o.optString("title"), (0 until its.length()).map { j ->
            val x = its.getJSONObject(j); PlanItem(x.optString("t"), x.optBoolean("d"))
        })
    }
} catch (_: Exception) {
    emptyList()
}

private fun savePlans(list: List<Plan>) {
    val arr = JSONArray()
    list.forEach { p ->
        val its = JSONArray()
        p.items.forEach { its.put(JSONObject().put("t", it.t).put("d", it.done)) }
        arr.put(JSONObject().put("id", p.id).put("title", p.title).put("items", its))
    }
    Store.putStr("plans", arr.toString())
}

/** کارت یادآور روزانه: هر روز اعلان کار بعدیِ برنامه */
@Composable
private fun ReminderCard() {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val hours = listOf(7, 9, 12, 18, 21)
    var on by remember { mutableStateOf(Store.reminderOn) }
    var hour by remember { mutableStateOf(Store.reminderHour) }
    fun apply(newOn: Boolean, newHour: Int) {
        on = newOn; hour = newHour
        Store.saveReminder(newOn, newHour)
        if (newOn) Reminder.schedule(ctx, newHour) else Reminder.cancel(ctx)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) toast(ctx, "بدون اجازه‌ی اعلان، یادآور نشان داده نمی‌شود")
        apply(true, hour)
    }
    Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.surface.copy(alpha = 0.93f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏰", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("یادآور روزانه", fontWeight = FontWeight.Bold, color = c.onSurface)
                    Text("هر روز کار بعدی برنامه‌ات را یادآوری می‌کنم", fontSize = 12.sp, color = c.onSurface.copy(alpha = 0.65f))
                }
                Switch(checked = on, onCheckedChange = { v ->
                    if (v && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else apply(v, hour)
                })
            }
            if (on) {
                ChipRow(hours, hour, { "ساعت ${it}" }) { apply(true, it) }
            }
        }
    }
}

@Composable
fun PlannerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    val c = MaterialTheme.colorScheme
    val plans = remember { mutableStateListOf<Plan>().also { it.addAll(loadPlans()) } }
    var openId by remember { mutableStateOf(-1L) }
    var goal by remember { mutableStateOf("") }
    val kinds = listOf("مطالعه و امتحان", "ورزش و تناسب اندام", "سفر", "پروژه و کار", "یادگیری مهارت", "برنامه‌ی روزانه")
    var kind by remember { mutableStateOf(kinds[0]) }
    val spans = listOf("۷ روز" to 7, "۱۴ روز" to 14, "۳۰ روز" to 30)
    var span by remember { mutableStateOf(spans[0]) }

    fun update(p: Plan) {
        val i = plans.indexOfFirst { it.id == p.id }
        if (i >= 0) plans[i] = p
        savePlans(plans)
    }

    val open = plans.firstOrNull { it.id == openId }
    ToolScaffold("برنامه‌ریز", { if (open != null) openId = -1L else onBack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (open != null) {
                val doneCount = open.items.count { it.done }
                Text(open.title, Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                LinearProgressIndicator(
                    progress = { if (open.items.isEmpty()) 0f else doneCount.toFloat() / open.items.size },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
                Text("$doneCount از ${open.items.size} انجام شد", Modifier.padding(horizontal = 18.dp), fontSize = 12.5.sp, color = c.primary)
                SaveTextButton(
                    open.title + "\n\n" + open.items.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.t },
                    "plan", Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
                open.items.forEachIndexed { i, it ->
                    Surface(
                        onClick = { update(open.copy(items = open.items.toMutableList().also { l -> l[i] = it.copy(done = !it.done) })) },
                        shape = RoundedCornerShape(16.dp), color = c.surface.copy(alpha = 0.93f),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = it.done, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(
                                it.t, fontSize = 14.5.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f).padding(end = 8.dp),
                                textDecoration = if (it.done) TextDecoration.LineThrough else null,
                                color = if (it.done) c.onSurface.copy(alpha = 0.5f) else c.onSurface,
                            )
                        }
                    }
                }
            } else {
                AppField(goal, { goal = it }, "هدفت چیه؟ (مثلاً آمادگی برای کنکور ریاضی)", Modifier.padding(horizontal = 16.dp), minLines = 2)
                SectionTitle("نوع برنامه"); ChipRow(kinds, kind, { it }) { kind = it }
                SectionTitle("مدت"); ChipRow(spans, span, { it.first }) { span = it }
                GradButton(
                    "ساخت برنامه", icon = Icons.Rounded.Checklist, loading = task.loading, enabled = goal.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = listOf(Color(0xFFF7971E), Color(0xFFFFB300)),
                    onClick = {
                        task.launch(scope) {
                            val raw = Ai.generate(
                                "Goal: $goal\nType: $kind\nDuration: ${span.second} days",
                                system = "You are a practical planner. Create a realistic day-by-day plan in Persian. Return ONLY JSON: " +
                                    "{\"title\":\"short title\",\"items\":[\"روز ۱: concrete task\", ...]} with one concise actionable item per day " +
                                    "(max ${span.second} items). Mix effort levels and include rest/review days when sensible.",
                                json = true, temperature = 0.7, maxTokens = 3500,
                            )
                            val o = JSONObject(raw.stripCodeFence())
                            val its = o.getJSONArray("items")
                            val items = (0 until its.length()).map { PlanItem(its.optString(it), false) }.filter { it.t.isNotBlank() }
                            if (items.isEmpty()) throw AiException("برنامه ساخته نشد. دوباره امتحان کن.")
                            val plan = Plan(System.currentTimeMillis(), o.optString("title", goal).ifBlank { goal }, items)
                            plans.add(0, plan); savePlans(plans); openId = plan.id; goal = ""
                            History.add("PLANNER", plan.title, items.joinToString("\n") { "☐ ${it.t}" })
                        }
                    },
                )
                ErrorText(task.error)
                ReminderCard()
                if (plans.isNotEmpty()) {
                    SectionTitle("برنامه‌های من")
                    plans.toList().forEach { p ->
                        val d = p.items.count { it.done }
                        Surface(
                            onClick = { openId = p.id }, shape = RoundedCornerShape(18.dp), color = c.surface.copy(alpha = 0.93f),
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.onSurface)
                                    LinearProgressIndicator(
                                        progress = { if (p.items.isEmpty()) 0f else d.toFloat() / p.items.size },
                                        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                                    )
                                    Text("$d از ${p.items.size}", fontSize = 11.5.sp, color = c.primary)
                                }
                                IconButton(onClick = { plans.remove(p); savePlans(plans) }) {
                                    Icon(Icons.Rounded.DeleteOutline, "حذف", tint = c.onSurface.copy(alpha = 0.55f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- آشپز هوشمند

@Composable
fun RecipeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    var items by remember { mutableStateOf("") }
    val styles = listOf("هر نوعی", "سریع (زیر ۳۰ دقیقه)", "رژیمی و سبک", "ایرانی", "بدون گوشت", "دسر و شیرینی")
    var style by remember { mutableStateOf(styles[0]) }
    var people by remember { mutableStateOf(2) }
    var result by remember { mutableStateOf("") }

    ToolScaffold("آشپز هوشمند", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppField(items, { items = it }, "چه موادی داری؟ (مثلاً مرغ، برنج، پیاز، ماست)", Modifier.padding(horizontal = 16.dp), minLines = 3)
            MediaBar(Modifier.padding(horizontal = 16.dp), ocrPrompt = "List the food ingredients visible in this image as a short comma-separated list in Persian. Output only the list.", onOcrText = { items = if (items.isBlank()) it else items + "، " + it }, onSpeech = { items = (items + " " + it).trim() })
            SectionTitle("نوع غذا"); ChipRow(styles, style, { it }) { style = it }
            SectionTitle("برای چند نفر"); ChipRow(listOf(1, 2, 4, 6), people, { "$it نفر" }) { people = it }
            GradButton(
                "غذا پیشنهاد بده", icon = Icons.Rounded.Restaurant, loading = task.loading, enabled = items.isNotBlank(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
                onClick = {
                    task.launch(scope) {
                        result = Ai.generate(
                            "Ingredients available: $items\nStyle: $style\nServings: $people",
                            system = "You are a friendly home chef. $FA_SYS First suggest 3 dish names with one line each, then give the full recipe " +
                                "of the best one: ingredients with amounts for the given servings, numbered steps, cooking time and a rough calorie estimate. " +
                                "You may assume basic pantry items (salt, oil, water, common spices).",
                            temperature = 0.8, maxTokens = 2200,
                        )
                        History.add("RECIPE", items, result)
                    }
                },
            )
            ErrorText(task.error)
            if (result.isNotBlank()) ResultCard(result, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- ایده‌ساز کسب‌وکار

private val bizTasks = listOf(
    "ایده‌ی استارتاپ" to "Give 6 creative, realistic startup or side-business ideas for this field, each with target customer, how it earns money and the first step.",
    "اسم و شعار برند" to "Suggest 10 memorable brand names (Persian and English options) and 5 catchy slogans for this business, with a one-line reason for the best three.",
    "طرح کسب‌وکار یک‌صفحه‌ای" to "Write a one-page business plan: problem, solution, customers, revenue, marketing, costs, risks and a 3-month action plan.",
    "تقویم محتوای ۳۰ روزه" to "Create a 30-day social media content calendar (one idea per day with post type and a short hook) for this business.",
    "متن آگهی و فروش" to "Write 3 persuasive sales ad texts (short, medium, long) for this product/service with a clear call to action.",
    "پاسخ به نظر مشتری" to "Write polite, professional replies to this customer message or complaint in 3 tones: formal, friendly, apologetic.",
)

@Composable
fun BizScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    var sel by remember { mutableStateOf(bizTasks[0]) }
    var input by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    ToolScaffold("ایده‌ساز کسب‌وکار", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipRow(bizTasks, sel, { it.first }) { sel = it }
            AppField(input, { input = it }, "درباره‌ی کسب‌وکار، محصول یا پیام مشتری", Modifier.padding(horizontal = 16.dp), minLines = 4, maxLines = 10)
            MediaBar(Modifier.padding(horizontal = 16.dp), onOcrText = { input = it }, onText = { input = it }, onSpeech = { input = (input + " " + it).trim() })
            AppField(audience, { audience = it }, "مخاطب یا شهر (اختیاری)", Modifier.padding(horizontal = 16.dp), maxLines = 2)
            GradButton(
                "بساز", icon = Icons.Rounded.Lightbulb, loading = task.loading, enabled = input.isNotBlank(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = listOf(Color(0xFF396AFC), Color(0xFF2948FF)),
                onClick = {
                    task.launch(scope) {
                        result = Ai.generate(
                            "${sel.second}\n\nBusiness / input: $input" + if (audience.isNotBlank()) "\nAudience/location: $audience" else "",
                            system = "You are an experienced business and marketing consultant. $FA_SYS",
                            temperature = 0.85, maxTokens = 2500,
                        )
                        History.add("BIZ", "${sel.first}: ${input.take(40)}", result)
                    }
                },
            )
            ErrorText(task.error)
            if (result.isNotBlank()) ResultCard(result, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- یادداشت صوتی

@Composable
fun NotesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rec = remember { PcmRecorder() }
    val task = remember { TaskState() }
    val ai = remember { TaskState() }
    val c = MaterialTheme.colorScheme
    var recording by remember { mutableStateOf(false) }
    var secs by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var histId by remember { mutableStateOf(-1L) }
    var recJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { rec.stop(); recJob?.cancel() } }

    fun start() {
        if (recording) return
        recording = true; secs = 0; out = ""
        recJob = scope.launch {
            val timer = launch { while (true) { delay(1000); secs++ } }
            try {
                val pcm = rec.record(180000)
                timer.cancel(); recording = false
                if (pcm.size > rec.sampleRate / 2) {
                    task.launch(scope) {
                        val t = Ai.transcribe(pcmToWav(pcm, rec.sampleRate), null)
                        text = if (text.isBlank()) t else text + "\n" + t
                        histId = History.add("NOTES", text.take(50), text)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                task.error = e.message ?: "خطا در ضبط"
            } finally {
                timer.cancel(); recording = false
            }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else toast(ctx, "اجازه‌ی میکروفون لازم است")
    }

    fun run(label: String, prompt: String) {
        ai.launch(scope) {
            out = Ai.generate("$prompt\n\nNOTE:\n$text", system = FA_SYS, temperature = 0.4, maxTokens = 1500)
            histId = History.update(histId, "NOTES", text.take(50), "$text\n\n— $label —\n$out")
        }
    }

    ToolScaffold("یادداشت صوتی", onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!Ai.canTranscribe()) {
                Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.primary.copy(alpha = 0.14f)) {
                    Text(
                        "اتصال به سرور برقرار نیست. تا آن موقع می‌توانی متن را تایپ کنی و خلاصه بگیری.",
                        Modifier.padding(12.dp), fontSize = 12.5.sp, lineHeight = 20.sp,
                    )
                }
            }
            Box(
                Modifier.size(104.dp).background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        if (recording) listOf(Color(0xFFFF5C8A), Color(0xFFFF9A62)) else listOf(Color(0xFF834D9B), Color(0xFFD04ED6))
                    ), CircleShape,
                ).clickable {
                    if (recording) rec.stop()
                    else if (hasMic(ctx)) start() else perm.launch(Manifest.permission.RECORD_AUDIO)
                },
                contentAlignment = Alignment.Center,
            ) { Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.KeyboardVoice, null, tint = Color.White, modifier = Modifier.size(46.dp)) }
            Text(
                if (recording) "در حال ضبط… $secs ثانیه (برای پایان بزن)" else if (task.loading) "در حال تبدیل به متن…" else "برای ضبط یادداشت بزن (تا ۳ دقیقه)",
                fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            )
            ErrorText(task.error)
            AppField(text, { text = it }, "متن یادداشت", Modifier.padding(horizontal = 16.dp), minLines = 5, maxLines = 14)
            MediaBar(Modifier.padding(horizontal = 16.dp), onOcrText = { text = (text + "\n" + it).trim() }, onText = { text = (text + "\n" + it).trim() })
            val acts = listOf(
                "خلاصه" to "Summarize this voice note clearly in a few sentences.",
                "لیست کارها" to "Extract all tasks and to-dos from this note as a checklist with deadlines if mentioned.",
                "ایمیل رسمی" to "Turn this note into a polite professional email with a subject line.",
                "بازنویسی تمیز" to "Rewrite this spoken note as clean, well-punctuated written text without changing its meaning.",
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                acts.take(2).forEach { a ->
                    OutlinedButton({ run(a.first, a.second) }, Modifier.weight(1f), enabled = text.isNotBlank() && !ai.loading) { Text(a.first, fontSize = 13.sp) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                acts.drop(2).forEach { a ->
                    OutlinedButton({ run(a.first, a.second) }, Modifier.weight(1f), enabled = text.isNotBlank() && !ai.loading) { Text(a.first, fontSize = 13.sp) }
                }
            }
            if (ai.loading) CircularProgressIndicator()
            ErrorText(ai.error)
            if (out.isNotBlank()) ResultCard(out, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}
