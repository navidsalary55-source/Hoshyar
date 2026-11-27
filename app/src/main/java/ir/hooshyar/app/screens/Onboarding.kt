package ir.hooshyar.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private class OPage(val emoji: String, val title: String, val text: String, val c1: Color, val c2: Color)

private val onboardPages = listOf(
    OPage("🤖", "به هوشیار خوش آمدی", "همه‌ی ابزارهای هوش مصنوعی در یک اپ: چت، تصویر، ویدیو، صدا، ترجمه، نوشتن و کلی ابزار کاربردی.", Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    OPage("🎨", "بساز و زیبا کن", "استوری با متن و استیکر، متحرک‌سازی عکس، کارتونی‌کردن، حذف اشیاء از عکس و ویدیو و بهتر کردن کیفیت عکس.", Color(0xFFFF5C8A), Color(0xFFFFA62B)),
    OPage("🎬", "ویدیو با آهنگ و متن", "موزیک بگذار تا متن آهنگ با فونت‌های زیبا و جلوه‌های جذاب هم‌زمان روی ویدیو بیاید؛ یا با گفتار، ویدیوی روایت‌شده بساز.", Color(0xFF8E54E9), Color(0xFF47B8E0)),
    OPage("💎", "رایگان امتحان کن", "همه‌ی بخش‌ها هر روز سهمیه‌ی رایگان دارند. برای ذخیره، دانلود و اشتراک‌گذاری نتیجه، اشتراک بگیر.", Color(0xFF00C9A7), Color(0xFF5B8DEF)),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val state = rememberPagerState { onboardPages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == onboardPages.size - 1
    val pg = onboardPages[state.currentPage]
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pg.c1, pg.c2)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone) { Text("رد کردن", color = Color.White.copy(alpha = 0.85f)) }
            }
            HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { i ->
                val p = onboardPages[i]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(140.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) { Text(p.emoji, fontSize = 68.sp) }
                    Spacer(Modifier.height(32.dp))
                    Text(p.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Text(p.text, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
                onboardPages.indices.forEach { i ->
                    Box(Modifier.padding(4.dp).size(if (i == state.currentPage) 22.dp else 8.dp, 8.dp).background(Color.White.copy(alpha = if (i == state.currentPage) 1f else 0.45f), CircleShape))
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = pg.c1),
            ) { Text(if (last) "شروع کنیم 🚀" else "بعدی", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}
