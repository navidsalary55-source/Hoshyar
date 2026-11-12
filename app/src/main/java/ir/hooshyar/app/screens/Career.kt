package ir.hooshyar.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*

private const val CAREER_SYS = "Reply in Persian (Farsi) unless the user clearly asks for another language. Use a polished, professional tone and simple markdown (bold, bullet lists). Never invent facts the user did not give; use clear placeholders like [نام شرکت] where information is missing."

private val careerTasks = listOf(
    "رزومه" to "Write a professional one-page resume (CV) from the information below: summary, skills, experience, education, extra items. Use strong action wording and a clean structure.",
    "نامه‌ی انگیزه‌نامه" to "Write a convincing cover letter / motivation letter for the position described, based on the information below.",
    "نامه‌ی اداری" to "Write a formal Persian administrative letter (with subject, greeting, body, respectful closing) for the situation below.",
    "آماده‌سازی مصاحبه" to "Prepare me for a job interview: list the 10 most likely questions for this role, a strong sample answer for each, and 5 smart questions I can ask the interviewer.",
    "بیو لینکدین و اینستا" to "Write 3 versions (short, medium, long) of a professional bio for LinkedIn/Instagram from the information below, plus 5 skill keywords.",
    "ایمیل کاری" to "Write a polished work email (subject line + body) for the situation below, in a formal version and a friendly version.",
)

@Composable
fun CareerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val task = remember { TaskState() }
    var sel by remember { mutableStateOf(careerTasks[0]) }
    var input by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    ToolScaffold("رزومه و نامه‌نگاری", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipRow(careerTasks, sel, { it.first }) { sel = it }
            AppField(role, { role = it }, "عنوان شغل یا موضوع (مثلاً برنامه‌نویس اندروید)", Modifier.padding(horizontal = 16.dp), maxLines = 2)
            AppField(input, { input = it }, "اطلاعات خودت: سابقه، مهارت‌ها، تحصیلات یا توضیح موضوع نامه", Modifier.padding(horizontal = 16.dp), minLines = 5, maxLines = 12)
            MediaBar(Modifier.padding(horizontal = 16.dp), onOcrText = { input = it }, onText = { input = it }, onSpeech = { input = (input + " " + it).trim() })
            GradButton(
                "بساز", icon = Icons.Rounded.Work, loading = task.loading, enabled = input.isNotBlank() || role.isNotBlank(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = listOf(Color(0xFF0F9D8A), Color(0xFF3FA2F7)),
                onClick = {
                    task.launch(scope) {
                        result = Ai.generate(
                            "${sel.second}\n\nRole / topic: $role\n\nInformation:\n$input",
                            system = CAREER_SYS,
                            temperature = 0.7, maxTokens = 2500,
                        )
                        History.add("CAREER", "${sel.first}: ${role.ifBlank { input }.take(40)}", result)
                    }
                },
            )
            ErrorText(task.error)
            if (result.isNotBlank()) ResultCard(result, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}
