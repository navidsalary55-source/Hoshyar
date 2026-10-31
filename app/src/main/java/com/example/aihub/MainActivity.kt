package com.example.aihub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.core.Server
import com.example.aihub.core.Store
import com.example.aihub.screens.*
import com.example.aihub.ui.AIHubTheme
import com.example.aihub.ui.AppBackground
import com.example.aihub.ui.HelpScreen
import com.example.aihub.ui.HistoryScreen
import com.example.aihub.ui.Screen

class MainActivity : ComponentActivity() {
    /** بخشی که از ویجت، میان‌بر یا اعلان خواسته شده */
    private var launchScreen by mutableStateOf<Screen?>(null)

    private fun readIntent(i: android.content.Intent?) {
        val name = i?.getStringExtra("screen") ?: return
        launchScreen = try { Screen.valueOf(name) } catch (_: Exception) { null }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        Store.init(this)
        com.example.aihub.core.History.init(this)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            AIHubTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                LaunchedEffect(launchScreen) {
                    launchScreen?.let { screen = it; launchScreen = null }
                }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                val back = { screen = Screen.HOME }
                LaunchedEffect(Unit) { if (Store.serverOn) Server.refresh() }
                if (Store.showPaywall) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { Store.showPaywall = false },
                        title = { androidx.compose.material3.Text("نیاز به اشتراک") },
                        text = {
                            androidx.compose.material3.Text(
                                "ذخیره، دانلود، کپی و اشتراک‌گذاری نتیجه فقط با اشتراک ممکن است. همه‌ی بخش‌ها را می‌توانی رایگان امتحان کنی.",
                                lineHeight = 24.sp,
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { Store.showPaywall = false; screen = Screen.SETTINGS }) {
                                androidx.compose.material3.Text("وارد کردن کد اشتراک")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { Store.showPaywall = false }) {
                                androidx.compose.material3.Text("بعداً")
                            }
                        },
                    )
                }
                AppBackground(showImage = screen != Screen.HOME) {
                    when (screen) {
                        Screen.HOME -> HomeScreen { screen = it }
                        Screen.CHAT -> ChatScreen(advanced = false, onBack = back)
                        Screen.ADV_CHAT -> ChatScreen(advanced = true, onBack = back)
                        Screen.VOICE_CHAT -> VoiceChatScreen(back)
                        Screen.VOICE_CHANGER -> VoiceChangerScreen(back)
                        Screen.IMAGE_EDIT -> ImageEditScreen(back)
                        Screen.ANIMATE -> AnimateScreen(back)
                        Screen.STORY -> StoryScreen(back)
                        Screen.TRIM -> TrimScreen(back)
                        Screen.TRANSLATE -> TranslateScreen(back)
                        Screen.SONG -> SongScreen(back)
                        Screen.WEBSITE -> WebsiteScreen(back)
                        Screen.DRAW -> DrawScreen(back)
                        Screen.CHAR3D -> CharStudioScreen(back)
                        Screen.TTS -> TtsScreen(back)
                        Screen.SPEECH_VIDEO -> SpeechVideoScreen(back)
                        Screen.WRITER -> WriterScreen(back)
                        Screen.SETTINGS -> SettingsScreen(back)
                        Screen.ABOUT -> AboutScreen(back)
                        Screen.CONTACT -> ContactScreen(back)
                        Screen.SUPPORT -> SupportScreen(back)
                        Screen.RULES -> RulesScreen(back)
                        Screen.MANAGEMENT -> ManagementScreen(back)
                        Screen.HELP -> HelpScreen(back)
                        Screen.HISTORY -> HistoryScreen(back)
                        Screen.VISION -> VisionScreen(back)
                        Screen.SUMMARY -> SummaryScreen(back)
                        Screen.QUIZ -> QuizScreen(back)
                        Screen.PLANNER -> PlannerScreen(back)
                        Screen.RECIPE -> RecipeScreen(back)
                        Screen.BIZ -> BizScreen(back)
                        Screen.NOTES -> NotesScreen(back)
                        Screen.LANG_COACH -> CoachScreen(back)
                    }
                }
            }
        }
    }
}

