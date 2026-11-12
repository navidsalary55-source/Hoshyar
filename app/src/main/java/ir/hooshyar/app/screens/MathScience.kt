package ir.hooshyar.app.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

// ---------------------------------------------------------------- داده‌ی بخش‌ها

private class MSubject(
    val id: String, val title: String, val emoji: String, val sub: String,
    val c1: Color, val c2: Color, val focus: String, val examples: List<String>,
)

private val subjects = listOf(
    MSubject(
        "math", "ریاضی و جبر", "🧮", "معادله، نامعادله، تابع، دنباله",
        Color(0xFF7C6CFF), Color(0xFF3FA2F7),
        "algebra and school mathematics: equations, inequalities, systems of equations, polynomials, factoring, functions, sequences and series, exponents, logarithms, trigonometry, absolute value, word problems",
        listOf("x² − 5x + 6 = 0", "دستگاه: 2x + y = 7 و x − y = −1", "مجموع ۲۰ جمله‌ی اول دنباله‌ی 3، 7، 11، ...", "log₂(x) + log₂(x − 2) = 3", "نامعادله‌ی |2x − 3| < 5"),
    ),
    MSubject(
        "geom", "هندسه", "📐", "مثلث، دایره، قضیه، اثبات، حجم",
        Color(0xFF12C2A7), Color(0xFF2EC4F1),
        "geometry: triangles, circles, polygons, similarity, congruence, Pythagorean theorem, angle chasing, areas, perimeters, volumes and surface areas of solids, coordinate geometry, vectors, and formal proofs. Always first describe the figure in words and list the given facts; for proofs write numbered statements with a reason beside each (theorem or definition used)",
        listOf("مساحت مثلثی با اضلاع ۷، ۸ و ۹", "ثابت کن زاویه‌ی محاطی نصف کمان مقابل است", "حجم کره‌ای به شعاع ۶", "معادله‌ی دایره‌ای که از سه نقطه‌ی (0,0)، (4,0) و (0,2) می‌گذرد"),
    ),
    MSubject(
        "phys", "فیزیک", "⚡", "حرکت، نیرو، انرژی، الکتریسیته",
        Color(0xFFFF8A5C), Color(0xFFFF5C8A),
        "physics: kinematics, Newton's laws, work and energy, momentum, rotation, gravitation, fluids, thermodynamics, waves, optics, electricity and magnetism, modern physics. Use the format: given data (with units), unknown, the law or formula used, substitution, calculation, final answer with SI units. Convert units first when needed",
        listOf("خودرویی با شتاب ۲ m/s² از حال سکون شروع می‌کند؛ بعد از ۱۰ ثانیه چقدر جابه‌جا می‌شود؟", "جسمی ۵ کیلوگرمی را با نیروی ۴۰ نیوتن روی سطح بدون اصطکاک می‌کشیم؛ شتاب؟", "مقاومت معادل ۳ مقاومت ۶، ۳ و ۲ اهم موازی", "انرژی پتانسیل جسم ۲ کیلوگرمی در ارتفاع ۱۵ متری"),
    ),
    MSubject(
        "chem", "شیمی", "🧪", "موازنه، مول، استوکیومتری، pH",
        Color(0xFF16A34A), Color(0xFF3FA2F7),
        "chemistry: balancing reactions, mole and molar mass calculations, stoichiometry, limiting reagent, solutions and concentration, gases (ideal gas law), thermochemistry, equilibrium, acids and bases and pH, electrochemistry, organic naming. Show the balanced equation, use atomic masses in the solution, and keep significant figures sensible",
        listOf("موازنه: Fe + O₂ → Fe₂O₃", "چند گرم آب از سوختن کامل ۸ گرم هیدروژن به دست می‌آید؟", "pH محلول ۰٫۰۱ مولار هیدروکلریک اسید", "حجم ۲ مول گاز کامل در شرایط STP"),
    ),
    MSubject(
        "calc", "حسابان و ریاضی عمومی", "∫", "حد، مشتق، انتگرال، سری",
        Color(0xFFB76CFF), Color(0xFF6C5CE7),
        "calculus: limits, continuity, derivatives (rules, chain rule, implicit), tangent lines, optimization, curve sketching, definite and indefinite integrals, integration techniques, areas and volumes by integration, sequences, series, Taylor series, differential equations. State the rule used at each step",
        listOf("مشتق f(x) = x³·sin(x)", "حد lim x→0 از sin(3x)/x", "∫ x·eˣ dx", "بیشینه‌ی مساحت مستطیلی با محیط ۴۰"),
    ),
    MSubject(
        "stat", "آمار، احتمال و ترکیبیات", "📊", "احتمال، میانگین، جایگشت، توزیع",
        Color(0xFFF59E0B), Color(0xFFEF4444),
        "probability, statistics, combinatorics and discrete mathematics: counting principles, permutations, combinations, conditional probability, Bayes, expected value, variance, standard deviation, binomial / normal / Poisson distributions, hypothesis tests, regression, graphs, logic and sets. Give the formula, then substitute the numbers",
        listOf("به چند طریق می‌توان ۵ نفر را دور میز گرد نشاند؟", "دو تاس می‌اندازیم؛ احتمال مجموع ۸", "میانگین و انحراف معیار: 4, 8, 6, 5, 3, 9", "احتمال دقیقاً ۳ شیر در ۵ پرتاب سکه"),
    ),
    MSubject(
        "lin", "جبر خطی و ماتریس", "🧱", "ماتریس، دترمینان، بردار ویژه",
        Color(0xFF00B4DB), Color(0xFF7C6CFF),
        "linear algebra: matrix operations, determinants, inverse, rank, solving linear systems (Gauss elimination), vector spaces, bases, linear transformations, eigenvalues and eigenvectors, diagonalization, dot and cross products. Write matrices row by row like [a b; c d] and show the row operations",
        listOf("دترمینان [1 2; 3 4]", "وارون ماتریس [2 1; 5 3]", "مقادیر ویژه‌ی [4 1; 2 3]", "دستگاه: x + y + z = 6، 2y + 5z = −4، 2x + 5y − z = 27"),
    ),
)

private val figureSubjects = setOf("geom", "phys", "math", "calc", "lin")
private val levels = listOf("دبستان", "متوسطه اول", "متوسطه دوم", "کنکور", "دانشگاه")
private val levelEn = mapOf("دبستان" to "elementary school", "متوسطه اول" to "middle school (grades 7-9)", "متوسطه دوم" to "high school (grades 10-12)", "کنکور" to "Iranian university entrance exam (Konkur)", "دانشگاه" to "university")

private val modes = listOf(
    "حل کامل قدم‌به‌قدم" to "Solve it completely, step by step, explaining the reason of each step briefly.",
    "فقط پاسخ نهایی" to "Give only the final answer plus a one-line justification. No long steps.",
    "توضیح مفهوم و فرمول" to "Do not solve a specific exercise; explain the concept behind the question, list the key formulas and give one small worked example.",
    "بررسی حل من" to "The user wrote their own solution. Check it line by line, point to the first mistake, explain why it is wrong and show the correct continuation.",
    "حل با دو روش" to "Solve it with two different methods and compare them; confirm both give the same answer.",
    "تمرین مشابه" to "Solve the question briefly, then create 3 similar practice problems of increasing difficulty and give their final answers at the end.",
)

private val symbols = listOf("√", "²", "³", "π", "∫", "Σ", "∞", "±", "×", "÷", "≤", "≥", "≠", "≈", "°", "θ", "α", "β", "Δ", "→", "ⁿ", "₁", "₂")

private fun systemFor(s: MSubject, level: String) =
    "You are a brilliant, patient teacher and problem solver specialised in ${s.focus}. " +
        "Student level: ${levelEn[level] ?: level}. Reply in Persian (Farsi). " +
        "Write all mathematics as plain Unicode text (x², √x, π, ≤, ∫, Σ, a/b, x₁). NEVER use LaTeX, never use dollar signs or backslashes. " +
        "Structure the answer with short markdown headings: **داده‌ها**, **خواسته**, **حل**, **پاسخ نهایی** (the final answer in bold). " +
        "Number the steps. Double-check every calculation and, when possible, verify the result by substituting back. " +
        "If the problem is ambiguous or data is missing, say which assumption you make. If an attached image is unclear, say so and say what you could read."

// ---------------------------------------------------------------- صفحه‌ی اصلی بخش

@Composable
fun MathScreen(onBack: () -> Unit) {
    var section by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) { section = null }
    val back = { if (section != null) section = null else onBack() }
    when (val sec = section) {
        null -> MathHub(onBack, onPick = { section = it })
        "calc" -> CalculatorScreen(back)
        "units" -> UnitsScreen(back)
        "formulas" -> FormulasScreen(back)
        else -> SolverScreen(subjects.first { it.id == sec }, back)
    }
}

@Composable
private fun MathHub(onBack: () -> Unit, onPick: (String) -> Unit) {
    ToolScaffold("ریاضی و علوم", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3FA2F7))))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", fontSize = 40.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("مسئله‌ات رو بنویس یا عکس بگیر", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("حل قدم‌به‌قدم با توضیح، بررسی حل خودت و تمرین مشابه", color = Color.White.copy(alpha = 0.9f), fontSize = 12.5.sp, lineHeight = 20.sp)
                    }
                }
            }
            SectionTitle("درس‌ها")
            subjects.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { s -> HubCard(s.emoji, s.title, s.sub, s.c1, s.c2, Modifier.weight(1f)) { onPick(s.id) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionTitle("ابزارها (بدون نیاز به اینترنت)")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubCard("🔢", "ماشین‌حساب علمی", "sin، log، توان، فاکتوریل…", Color(0xFF0F766E), Color(0xFF22C1C3), Modifier.weight(1f)) { onPick("calc") }
                HubCard("📏", "تبدیل واحد", "طول، جرم، دما، سرعت، داده…", Color(0xFFDB2777), Color(0xFFFF8A5C), Modifier.weight(1f)) { onPick("units") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubCard("📚", "فرمول‌نامه", "جبر، هندسه، فیزیک، شیمی…", Color(0xFF4F46E5), Color(0xFF9333EA), Modifier.weight(1f)) { onPick("formulas") }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HubCard(emoji: String, title: String, sub: String, c1: Color, c2: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 138.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.height(10.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(3.dp))
            Text(sub, color = Color.White.copy(alpha = 0.88f), fontSize = 11.5.sp, lineHeight = 17.sp)
        }
    }
}

// ---------------------------------------------------------------- حل‌کننده

@Composable
private fun SolverScreen(s: MSubject, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val fontRes = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val task = remember { TaskState() }
    var level by remember { mutableStateOf(levels[2]) }
    var mode by remember { mutableStateOf(modes[0]) }
    var question by remember { mutableStateOf("") }
    val bmps = remember { mutableStateListOf<Bitmap>() }
    val imgs = remember { mutableStateListOf<ByteArray>() }
    var thread by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }
    var follow by remember { mutableStateOf("") }
    var fig by remember { mutableStateOf<Fig?>(null) }
    val figTask = remember { TaskState() }
    val sys = remember(level) { systemFor(s, level) }

    fun answer(history: List<ChatMsg>) {
        task.launch(scope) {
            val text = Ai.generateWith(history, system = sys, temperature = 0.2, maxTokens = 3000)
            thread = history + ChatMsg(history.size + 1L, false, text)
            if (history.size == 1) History.add("MATH", s.title + ": " + question.trim().take(60), text, imgs.firstOrNull(), if (imgs.isNotEmpty()) "jpg" else null)
        }
    }

    ToolScaffold(s.title, onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(s.c1, s.c2))).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(s.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(s.sub, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
            SectionTitle("سطح تو")
            ChipRow(levels, level, { it }) { level = it }
            SectionTitle("چه کاری انجام بشه؟")
            ChipRow(modes, mode, { it.first }) { mode = it }

            MediaBar(
                Modifier.padding(horizontal = 16.dp),
                maxImages = MAX_CHAT_IMAGES,
                onImage = { b ->
                    if (bmps.size >= MAX_CHAT_IMAGES) toast(ctx, "حداکثر $MAX_CHAT_IMAGES عکس")
                    else scope.launch {
                        val by = withContext(Dispatchers.Default) { b.toJpegBytes(if (bmps.size >= 5) 82 else 88) }
                        bmps.add(b); imgs.add(by)
                    }
                },
                onSpeech = { t -> question = (question + " " + t).trim() },
            )
            MediaPreview(bmps, { i -> if (i in bmps.indices) { bmps.removeAt(i); imgs.removeAt(i) } }, MAX_CHAT_IMAGES)
            AppField(
                question, { question = it },
                if (bmps.isNotEmpty()) "توضیح اضافه یا شماره‌ی سؤال (اختیاری)" else "مسئله را بنویس…",
                Modifier.padding(horizontal = 16.dp), minLines = 3, maxLines = 8,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                symbols.forEach { sym ->
                    Surface(onClick = { question += sym }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(sym, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 16.sp)
                    }
                }
            }
            if (thread.isEmpty()) {
                SectionTitle("نمونه سؤال (بزن تا پر بشه)")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.examples.forEach { ex ->
                        Surface(onClick = { question = ex }, shape = RoundedCornerShape(14.dp), color = s.c1.copy(alpha = 0.14f)) {
                            Text(ex, Modifier.widthIn(max = 220.dp).padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.5.sp, maxLines = 2)
                        }
                    }
                }
            }
            GradButton(
                "حل کن", icon = Icons.Rounded.AutoAwesome, loading = task.loading,
                enabled = question.isNotBlank() || imgs.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = listOf(s.c1, s.c2),
                onClick = {
                    fig = null
                    val multi = if (imgs.size > 1) "\n(${imgs.size} images are attached in order. Treat them as consecutive parts of one problem unless they are clearly separate problems; in that case solve each one under its own heading «سؤال ۱»، «سؤال ۲»…)" else ""
                    val q = mode.second + multi + "\n\nProblem:\n" + question.trim().ifEmpty { "Solve the problem shown in the attached image(s)." }
                    answer(listOf(ChatMsg(1, true, q, images = imgs.toList())))
                },
            )
            ErrorText(task.error)

            val shown = thread.filter { !it.isUser }
            if (shown.isNotEmpty() && s.id in figureSubjects) {
                OutlinedButton(
                    onClick = {
                        figTask.launch(scope) {
                            val summary = shown.first().text.take(1500)
                            val ask = "Problem:\n" + question.trim().ifEmpty { "(see the attached image)" } +
                                "\n\nSolution summary (keep the numbers consistent with it):\n" + summary +
                                "\n\nDraw the figure or graph for this problem. Output the JSON only."
                            val raw = Ai.generateWith(listOf(ChatMsg(1, true, ask, images = imgs.toList())), system = FIGURE_SYSTEM, temperature = 0.1, json = true, maxTokens = 2500)
                            val f = try { parseFig(raw) } catch (e: Exception) { throw AiException("شکل خوانده نشد؛ یک بار دیگر امتحان کن.") }
                            if (f.isEmpty) throw AiException("برای این سؤال شکلی پیدا نشد.")
                            fig = f
                            try {
                                History.add("MATH", "شکل: " + question.trim().take(50), "شکل و نمودار این سؤال", renderFig(f, s.c1, fontRes).toJpegBytes(92), "jpg")
                            } catch (_: Exception) {}
                        }
                    },
                    enabled = !figTask.loading, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    if (figTask.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (fig == null) "🎨 رسم شکل / نمودار" else "🔄 رسم دوباره‌ی شکل")
                }
                ErrorText(figTask.error)
                fig?.let { f ->
                    FigureView(f, s.c1, Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradButton(
                            "ذخیره در گالری", icon = Icons.Rounded.Download, modifier = Modifier.weight(1f), colors = listOf(s.c1, s.c2),
                            onClick = {
                                if (canExport()) {
                                    val ok = try { saveBitmapToGallery(ctx, renderFig(f, s.c1, fontRes), "Hooshyar_Figure_${System.currentTimeMillis()}") } catch (e: Exception) { false }
                                    toast(ctx, if (ok) "شکل در گالری ذخیره شد ✅" else "ذخیره نشد")
                                }
                            },
                        )
                        OutlinedButton(
                            onClick = {
                                if (canExport()) try { shareBitmap(ctx, renderFig(f, s.c1, fontRes)) } catch (e: Exception) { toast(ctx, "اشتراک‌گذاری نشد") }
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("اشتراک") }
                    }
                    Text(
                        "شکل را هوش مصنوعی می‌کشد و ممکن است کمی تقریبی باشد؛ اندازه‌ها را با خود سؤال بسنج.",
                        Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
            }
            if (shown.isNotEmpty()) {
                thread.forEachIndexed { i, m ->
                    if (!m.isUser) {
                        if (i > 1) SectionTitle("پاسخ به سؤال بعدی")
                        ResultCard(m.text, Modifier.padding(horizontal = 16.dp))
                    } else if (i > 0) {
                        Text("❓ " + m.text, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                }
                AppField(follow, { follow = it }, "سؤال درباره‌ی همین حل (مثلاً: چرا این مرحله؟)", Modifier.padding(horizontal = 16.dp), maxLines = 3)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradButton(
                        "بپرس", icon = Icons.Rounded.Send, loading = task.loading, enabled = follow.isNotBlank(),
                        modifier = Modifier.weight(1f), colors = listOf(s.c1, s.c2),
                        onClick = {
                            val h = thread + ChatMsg(thread.size + 1L, true, follow.trim())
                            follow = ""
                            thread = h
                            answer(h)
                        },
                    )
                    OutlinedButton(
                        onClick = { thread = emptyList(); follow = ""; question = ""; bmps.clear(); imgs.clear(); fig = null },
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("سؤال جدید") }
                }
            }
            Text(
                "پاسخ‌ها را همیشه یک بار با کتاب یا معلم هم مقایسه کن؛ هوش مصنوعی هم ممکن است اشتباه کند.",
                Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- ماشین‌حساب علمی

internal class Calc(src: String, val deg: Boolean, private val xv: Double? = null) {
    private val s = src.replace(" ", "")
    private var i = 0

    fun run(): Double {
        val v = expr()
        if (i < s.length) throw IllegalArgumentException("نماد نامعتبر")
        return v
    }

    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

    private fun expr(): Double {
        var v = term()
        while (true) {
            when (peek()) {
                '+' -> { i++; v += term() }
                '-' -> { i++; v -= term() }
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = unary()
        while (true) {
            when (peek()) {
                '*' -> { i++; v *= unary() }
                '/' -> { i++; v /= unary() }
                '(', 'π', '√' -> v *= power()
                else -> if (peek().isLetter()) v *= power() else return v
            }
        }
    }

    private fun unary(): Double = when (peek()) {
        '-' -> { i++; -unary() }
        '+' -> { i++; unary() }
        else -> power()
    }

    private fun power(): Double {
        val b = postfix()
        return if (peek() == '^') { i++; b.pow(unary()) } else b
    }

    private fun postfix(): Double {
        var v = primary()
        while (true) {
            when (peek()) {
                '!' -> { i++; v = fact(v) }
                '%' -> { i++; v /= 100.0 }
                else -> return v
            }
        }
    }

    private fun fact(x: Double): Double {
        if (x < 0 || x != floor(x) || x > 170) throw IllegalArgumentException("فاکتوریل فقط برای عدد صحیح ۰ تا ۱۷۰")
        var r = 1.0
        for (k in 2..x.toInt()) r *= k
        return r
    }

    private fun primary(): Double {
        val c = peek()
        if (c.isDigit() || c == '.') {
            val st = i
            while (peek().isDigit() || peek() == '.') i++
            if ((peek() == 'e' || peek() == 'E') && (s.getOrNull(i + 1)?.isDigit() == true || ((s.getOrNull(i + 1) == '-' || s.getOrNull(i + 1) == '+') && s.getOrNull(i + 2)?.isDigit() == true))) {
                i += 2
                while (peek().isDigit()) i++
            }
            return s.substring(st, i).toDoubleOrNull() ?: throw IllegalArgumentException("عدد نامعتبر")
        }
        if (c == '(') {
            i++
            val v = expr()
            if (peek() != ')') throw IllegalArgumentException("پرانتز بسته نشده")
            i++
            return v
        }
        if (c == '√') { i++; return sqrt(primary()) }
        if (c == 'π') { i++; return PI }
        if (c.isLetter()) {
            val st = i
            while (peek().isLetter()) i++
            val name = s.substring(st, i)
            if (name == "pi") return PI
            if (name == "x") return xv ?: throw IllegalArgumentException("x تعریف نشده")
            if (name == "e") return E
            if (peek() != '(') throw IllegalArgumentException("بعد از $name باید پرانتز بیاید")
            i++
            val a = expr()
            if (peek() != ')') throw IllegalArgumentException("پرانتز بسته نشده")
            i++
            val r = if (deg) PI / 180.0 else 1.0
            return when (name) {
                "sin" -> sin(a * r)
                "cos" -> cos(a * r)
                "tan" -> tan(a * r)
                "asin" -> asin(a) / r
                "acos" -> acos(a) / r
                "atan" -> atan(a) / r
                "sqrt" -> sqrt(a)
                "cbrt" -> Math.cbrt(a)
                "ln" -> ln(a)
                "log" -> log10(a)
                "exp" -> exp(a)
                "abs" -> abs(a)
                "floor" -> floor(a)
                "ceil" -> ceil(a)
                "round" -> round(a)
                else -> throw IllegalArgumentException("تابع «$name» را نمی‌شناسم")
            }
        }
        throw IllegalArgumentException(if (c == '\u0000') "عبارت ناتمام است" else "نماد «$c» نامعتبر است")
    }
}

internal fun normalizeExpr(t: String): String {
    val sb = StringBuilder()
    for (ch in t) sb.append(
        when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            '×', '·', '٭' -> '*'
            '÷', ':' -> '/'
            '−', '–', '—' -> '-'
            '٫', ',', '،' -> '.'
            '[', '{' -> '('
            ']', '}' -> ')'
            else -> ch
        }
    )
    return sb.toString().lowercase()
}

internal fun fmtNum(v: Double): String {
    if (v.isNaN()) return "تعریف‌نشده"
    if (v.isInfinite()) return if (v > 0) "∞" else "−∞"
    if (v == 0.0) return "0"
    val a = abs(v)
    if (a >= 1e12 || a < 1e-6) return String.format(java.util.Locale.US, "%.8e", v).replace(Regex("0+e"), "e").replace(".e", "e")
    val s = String.format(java.util.Locale.US, "%.10f", v).trimEnd('0').trimEnd('.')
    val parts = s.split('.')
    val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed().replace("-,", "-")
    return if (parts.size > 1) grouped + "." + parts[1] else grouped
}

private fun evalOrNull(text: String, deg: Boolean): Pair<Double?, String?> {
    if (text.isBlank()) return null to null
    return try { Calc(normalizeExpr(text), deg).run() to null } catch (e: Exception) { null to (e.message ?: "عبارت نامعتبر") }
}

@Composable
private fun CalculatorScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var deg by remember { mutableStateOf(true) }
    var hist by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val (value, err) = remember(text, deg) { evalOrNull(text, deg) }
    val keys = listOf("sin(", "cos(", "tan(", "asin(", "acos(", "atan(", "√(", "sqrt(", "cbrt(", "ln(", "log(", "exp(", "abs(", "π", "e", "^", "!", "%", "(", ")")

    ToolScaffold("ماشین‌حساب علمی", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppField(text, { text = it }, "عبارت را بنویس؛ مثلاً 2^10 + sin(30) × √(81)", ltr = true, minLines = 2, maxLines = 4)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("نتیجه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(
                        when {
                            value != null -> "= " + fmtNum(value)
                            err != null && text.isNotBlank() -> "…"
                            else -> "—"
                        },
                        fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
                    )
                    if (err != null && text.isNotBlank()) Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = deg, onClick = { deg = true }, label = { Text("درجه") })
                FilterChip(selected = !deg, onClick = { deg = false }, label = { Text("رادیان") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { if (text.isNotEmpty()) text = text.dropLast(1) }) { Text("⌫ پاک‌کن") }
                TextButton(onClick = { text = "" }) { Text("همه") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.forEach { k ->
                    Surface(onClick = { text += k }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(k, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 15.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradButton(
                    "ذخیره در سابقه", enabled = value != null, modifier = Modifier.weight(1f),
                    colors = listOf(Color(0xFF0F766E), Color(0xFF22C1C3)),
                    onClick = { value?.let { hist = (listOf(text.trim() to fmtNum(it)) + hist).take(10) } },
                )
                OutlinedButton(
                    onClick = {
                        value?.let { clip.setText(androidx.compose.ui.text.AnnotatedString(fmtNum(it).replace(",", ""))); toast(ctx, "نتیجه کپی شد") }
                    },
                    enabled = value != null, shape = RoundedCornerShape(16.dp),
                ) { Text("کپی") }
            }
            if (hist.isNotEmpty()) {
                SectionTitle("سابقه (بزن تا دوباره استفاده شود)")
                hist.forEach { (e, r) ->
                    Surface(onClick = { text = e }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(e, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("= $r", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                "اعداد فارسی هم قبول است. ضرب: ×  یا  *   تقسیم: ÷  یا  /   توان: ^   اعشار: نقطه یا «٫»",
                fontSize = 11.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- تبدیل واحد

private class UnitCat(val name: String, val units: List<Pair<String, Double>>, val temp: Boolean = false)

private val unitCats = listOf(
    UnitCat("طول", listOf("متر" to 1.0, "کیلومتر" to 1000.0, "سانتی‌متر" to 0.01, "میلی‌متر" to 0.001, "مایل" to 1609.344, "فوت" to 0.3048, "اینچ" to 0.0254, "یارد" to 0.9144)),
    UnitCat("جرم", listOf("کیلوگرم" to 1.0, "گرم" to 0.001, "تن" to 1000.0, "پوند" to 0.45359237, "اونس" to 0.028349523125, "مثقال" to 0.004608, "سیر" to 0.075)),
    UnitCat("دما", listOf("سلسیوس" to 1.0, "فارنهایت" to 1.0, "کلوین" to 1.0), temp = true),
    UnitCat("زمان", listOf("ثانیه" to 1.0, "دقیقه" to 60.0, "ساعت" to 3600.0, "روز" to 86400.0, "هفته" to 604800.0, "سال" to 31536000.0)),
    UnitCat("سرعت", listOf("متر بر ثانیه" to 1.0, "کیلومتر بر ساعت" to 1.0 / 3.6, "مایل بر ساعت" to 0.44704, "گره دریایی" to 0.514444)),
    UnitCat("مساحت", listOf("متر مربع" to 1.0, "کیلومتر مربع" to 1e6, "هکتار" to 1e4, "سانتی‌متر مربع" to 1e-4, "فوت مربع" to 0.09290304, "جریب" to 4046.8564224)),
    UnitCat("حجم", listOf("لیتر" to 1.0, "میلی‌لیتر" to 0.001, "متر مکعب" to 1000.0, "گالن (آمریکا)" to 3.785411784)),
    UnitCat("داده", listOf("بایت" to 1.0, "بیت" to 0.125, "کیلوبایت" to 1024.0, "مگابایت" to 1048576.0, "گیگابایت" to 1073741824.0, "ترابایت" to 1099511627776.0)),
    UnitCat("انرژی", listOf("ژول" to 1.0, "کالری" to 4.184, "کیلوکالری" to 4184.0, "کیلووات‌ساعت" to 3.6e6)),
    UnitCat("فشار", listOf("پاسکال" to 1.0, "کیلوپاسکال" to 1000.0, "بار" to 1e5, "اتمسفر" to 101325.0, "psi" to 6894.757)),
)

private fun convertTemp(v: Double, from: String, to: String): Double {
    val c = when (from) { "فارنهایت" -> (v - 32) * 5 / 9; "کلوین" -> v - 273.15; else -> v }
    return when (to) { "فارنهایت" -> c * 9 / 5 + 32; "کلوین" -> c + 273.15; else -> c }
}

@Composable
private fun UnitsScreen(onBack: () -> Unit) {
    var cat by remember { mutableStateOf(unitCats[0]) }
    var from by remember { mutableStateOf(unitCats[0].units[0].first) }
    var to by remember { mutableStateOf(unitCats[0].units[1].first) }
    var amount by remember { mutableStateOf("1") }
    val n = normalizeExpr(amount).toDoubleOrNull()
    val result: Double? = n?.let { x ->
        if (cat.temp) convertTemp(x, from, to)
        else {
            val f = cat.units.firstOrNull { it.first == from }?.second
            val t = cat.units.firstOrNull { it.first == to }?.second
            if (f != null && t != null) x * f / t else null
        }
    }

    ToolScaffold("تبدیل واحد", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("نوع کمیت")
            ChipRow(unitCats, cat, { it.name }) { c -> cat = c; from = c.units[0].first; to = c.units[1].first }
            AppField(amount, { amount = it }, "مقدار", Modifier.padding(horizontal = 16.dp), ltr = true)
            SectionTitle("از")
            ChipRow(cat.units, cat.units.first { it.first == from }, { it.first }) { from = it.first }
            SectionTitle("به")
            ChipRow(cat.units, cat.units.first { it.first == to }, { it.first }) { to = it.first }
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("$amount $from برابر است با", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (result != null) fmtNum(result) + " " + to else "مقدار را درست وارد کن",
                        fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { val t = from; from = to; to = t }, shape = RoundedCornerShape(16.dp)) { Text("⇄ جابه‌جا کن") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- فرمول‌نامه

private class FormulaCat(val name: String, val items: List<Pair<String, String>>)

private val formulaCats = listOf(
    FormulaCat("جبر", listOf(
        "اتحادهای مهم" to "(a + b)² = a² + 2ab + b²\n(a − b)² = a² − 2ab + b²\na² − b² = (a − b)(a + b)\n(a + b)³ = a³ + 3a²b + 3ab² + b³\na³ + b³ = (a + b)(a² − ab + b²)\na³ − b³ = (a − b)(a² + ab + b²)",
        "معادله‌ی درجه‌ی دو" to "ax² + bx + c = 0\nΔ = b² − 4ac\nx = (−b ± √Δ) / 2a\nمجموع ریشه‌ها = −b/a ، حاصل‌ضرب ریشه‌ها = c/a",
        "دنباله‌ی حسابی" to "aₙ = a₁ + (n − 1)d\nSₙ = n(a₁ + aₙ) / 2",
        "دنباله‌ی هندسی" to "aₙ = a₁·rⁿ⁻¹\nSₙ = a₁(rⁿ − 1) / (r − 1)\nمجموع بی‌نهایت جمله (|r| < 1): S = a₁ / (1 − r)",
        "توان" to "aᵐ · aⁿ = aᵐ⁺ⁿ\naᵐ / aⁿ = aᵐ⁻ⁿ\n(aᵐ)ⁿ = aᵐⁿ\na⁻ⁿ = 1 / aⁿ",
        "لگاریتم" to "log(ab) = log a + log b\nlog(a/b) = log a − log b\nlog aⁿ = n·log a\nlog_a b = ln b / ln a",
    )),
    FormulaCat("مثلثات", listOf(
        "اتحادهای پایه" to "sin²θ + cos²θ = 1\ntan θ = sin θ / cos θ\n1 + tan²θ = 1 / cos²θ",
        "جمع و تفاضل زاویه" to "sin(a ± b) = sin a cos b ± cos a sin b\ncos(a ± b) = cos a cos b ∓ sin a sin b",
        "زاویه‌ی دو برابر" to "sin 2a = 2 sin a cos a\ncos 2a = cos²a − sin²a = 1 − 2sin²a = 2cos²a − 1",
        "قانون سینوس‌ها و کسینوس‌ها" to "a / sin A = b / sin B = c / sin C = 2R\nc² = a² + b² − 2ab·cos C",
        "جدول زاویه‌های مهم" to "30°: sin = 1/2 ، cos = √3/2 ، tan = √3/3\n45°: sin = √2/2 ، cos = √2/2 ، tan = 1\n60°: sin = √3/2 ، cos = 1/2 ، tan = √3",
    )),
    FormulaCat("هندسه", listOf(
        "فیثاغورس" to "a² + b² = c²  (c وتر)",
        "مساحت مثلث" to "S = ½ × قاعده × ارتفاع\nهرون: S = √(p(p − a)(p − b)(p − c)) ، p = (a + b + c) / 2\nS = ½ · a · b · sin C",
        "مساحت چندضلعی‌ها" to "مستطیل = a·b\nمتوازی‌الاضلاع = قاعده × ارتفاع\nلوزی = ½ d₁d₂\nذوزنقه = ½ (a + b)·h",
        "دایره" to "محیط = 2πr\nمساحت = πr²\nطول کمان = rθ (θ برحسب رادیان)\nمساحت قطاع = ½ r²θ",
        "حجم‌ها" to "مکعب‌مستطیل = abc\nاستوانه = πr²h\nمخروط = ⅓ πr²h\nکره = ⁴⁄₃ πr³\nهرم = ⅓ × مساحت قاعده × ارتفاع",
        "مساحت سطح" to "کره = 4πr²\nاستوانه (کل) = 2πr(r + h)\nمخروط (جانبی) = πrl",
        "هندسه‌ی تحلیلی" to "فاصله‌ی دو نقطه = √((x₂ − x₁)² + (y₂ − y₁)²)\nوسط پاره‌خط = ((x₁ + x₂)/2 ، (y₁ + y₂)/2)\nشیب خط m = (y₂ − y₁) / (x₂ − x₁)\nفاصله‌ی نقطه از خط = |ax₀ + by₀ + c| / √(a² + b²)\nدایره: (x − a)² + (y − b)² = r²",
        "زاویه‌ها" to "مجموع زاویه‌های داخلی n ضلعی = (n − 2) × 180°\nزاویه‌ی خارجی مثلث = مجموع دو زاویه‌ی داخلی غیرمجاور",
    )),
    FormulaCat("حسابان", listOf(
        "مشتق توابع" to "(xⁿ)′ = n·xⁿ⁻¹\n(eˣ)′ = eˣ\n(ln x)′ = 1 / x\n(sin x)′ = cos x\n(cos x)′ = −sin x\n(tan x)′ = 1 + tan²x",
        "قواعد مشتق" to "(u·v)′ = u′v + uv′\n(u / v)′ = (u′v − uv′) / v²\n[f(g(x))]′ = f′(g(x)) · g′(x)",
        "انتگرال" to "∫ xⁿ dx = xⁿ⁺¹ / (n + 1) + C  (n ≠ −1)\n∫ 1/x dx = ln|x| + C\n∫ eˣ dx = eˣ + C\n∫ sin x dx = −cos x + C\n∫ cos x dx = sin x + C",
        "حدهای مهم" to "lim (x→0) sin x / x = 1\nlim (n→∞) (1 + 1/n)ⁿ = e\nlim (x→0) (eˣ − 1) / x = 1",
    )),
    FormulaCat("آمار و احتمال", listOf(
        "شمارش" to "جایگشت: P(n, r) = n! / (n − r)!\nترکیب: C(n, r) = n! / (r!(n − r)!)\nنشستن دور میز گرد: (n − 1)!",
        "احتمال" to "P(A) = حالت‌های مطلوب / کل حالت‌ها\nP(A ∪ B) = P(A) + P(B) − P(A ∩ B)\nشرطی: P(A|B) = P(A ∩ B) / P(B)\nبیز: P(A|B) = P(B|A)·P(A) / P(B)",
        "میانگین و پراکندگی" to "میانگین x̄ = Σxᵢ / n\nواریانس σ² = Σ(xᵢ − x̄)² / n\nانحراف معیار σ = √(واریانس)",
        "توزیع دوجمله‌ای" to "P(X = k) = C(n, k)·pᵏ·(1 − p)ⁿ⁻ᵏ\nE(X) = np ، Var(X) = np(1 − p)",
    )),
    FormulaCat("فیزیک", listOf(
        "حرکت با شتاب ثابت" to "v = v₀ + at\nx = x₀ + v₀t + ½at²\nv² = v₀² + 2aΔx",
        "نیرو" to "F = ma\nوزن: W = mg  (g ≈ 9٫8 m/s²)\nاصطکاک: f = μN",
        "کار، انرژی و توان" to "W = F·d·cos θ\nانرژی جنبشی K = ½mv²\nانرژی پتانسیل گرانشی U = mgh\nتوان P = W / t",
        "تکانه" to "p = mv\nضربه: F·Δt = Δp",
        "گرانش" to "F = G·m₁m₂ / r²  (G = 6٫674 × 10⁻¹¹ N·m²/kg²)",
        "شاره‌ها" to "فشار P = F / A\nفشار در عمق: P = P₀ + ρgh\nنیروی شناوری: F = ρ·V·g",
        "گرما" to "Q = m·c·Δθ\nتغییر حالت: Q = m·L",
        "الکتریسیته" to "قانون اهم: V = IR\nتوان: P = VI = I²R\nمقاومت سری: R = R₁ + R₂ + …\nمقاومت موازی: 1/R = 1/R₁ + 1/R₂ + …\nقانون کولن: F = k·q₁q₂ / r²  (k = 9 × 10⁹)",
        "موج و نوسان" to "v = λf ، T = 1 / f\nتندی نور c = 3 × 10⁸ m/s\nجرم و فنر: T = 2π√(m / k)\nآونگ ساده: T = 2π√(L / g)",
    )),
    FormulaCat("شیمی", listOf(
        "مول" to "n = m / M\nn = N / Nₐ  (Nₐ = 6٫022 × 10²³)\nحجم گاز در STP: V = n × 22٫4 L",
        "گاز کامل" to "PV = nRT\nR = 0٫0821 L·atm/(mol·K) = 8٫314 J/(mol·K)",
        "غلظت" to "مولاریته M = n / V (لیتر)\nرقیق‌سازی: M₁V₁ = M₂V₂",
        "اسید و باز" to "pH = −log[H⁺]\npOH = −log[OH⁻]\npH + pOH = 14  (در ۲۵°C)",
        "بازده" to "بازده درصدی = (مقدار واقعی / مقدار نظری) × 100",
        "جرم اتمی چند عنصر" to "H = 1 ، C = 12 ، N = 14 ، O = 16 ، Na = 23 ، Mg = 24\nAl = 27 ، S = 32 ، Cl = 35٫5 ، K = 39 ، Ca = 40 ، Fe = 56 ، Cu = 63٫5 ، Zn = 65",
    )),
)

@Composable
private fun FormulasScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var cat by remember { mutableStateOf(formulaCats[0]) }
    var q by remember { mutableStateOf("") }
    val all = remember { formulaCats.flatMap { c -> c.items.map { c.name to it } } }
    val shown = if (q.isBlank()) cat.items.map { cat.name to it } else all.filter { (_, it) -> it.first.contains(q.trim()) || it.second.contains(q.trim()) }

    ToolScaffold("فرمول‌نامه", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(q, { q = it }, "جستجو (مثلاً: مساحت، pH، مشتق)", Modifier.padding(horizontal = 16.dp))
            if (q.isBlank()) ChipRow(formulaCats, cat, { it.name }) { cat = it }
            if (shown.isEmpty()) Text("چیزی پیدا نشد", Modifier.padding(20.dp), fontSize = 13.sp)
            shown.forEach { (catName, item) ->
                Surface(
                    onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(item.first + "\n" + item.second)); toast(ctx, "کپی شد") },
                    shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.first, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            if (q.isNotBlank()) Text(catName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(item.second, fontSize = 14.sp, lineHeight = 24.sp)
                    }
                }
            }
            Text("برای کپی هر فرمول رویش بزن.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            Spacer(Modifier.height(16.dp))
        }
    }
}
