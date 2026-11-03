package ir.hooshyar.app

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
import ir.hooshyar.app.core.Server
import ir.hooshyar.app.core.Store
import ir.hooshyar.app.screens.*
import ir.hooshyar.app.ui.AIHubTheme
import ir.hooshyar.app.ui.AppBackground
import ir.hooshyar.app.ui.HelpScreen
import ir.hooshyar.app.ui.HistoryScreen
import ir.hooshyar.app.ui.Screen

class MainActivity : ComponentActivity() {
    /** بخشی که از ویجت، میان‌بر یا اعلان خواسته شده */
    private var launchScreen by mutableStateOf<Screen?>(null)

    private fun readIntent(i: android.content.Intent?) {
        val name = i?.getStringExtra("screen") ?: return
        launchScreen = try { Screen.valueOf(name) } catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (!ir.hooshyar.app.core.Billing.onActivityResult(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        Store.init(this)
        ir.hooshyar.app.core.History.init(this)
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
                            androidx.compose.material3.TextButton(onClick = { Store.showPaywall = false; screen = Screen.PLANS }) {
                                androidx.compose.material3.Text("قیمت‌ها و خرید اشتراک")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { Store.showPaywall = false }) {
                                androidx.compose.material3.Text("بعداً")
                            }
                        },
                    )
                }
                if (!Store.onboarded) {
                    ir.hooshyar.app.screens.OnboardingScreen { Store.saveOnboarded() }
                    return@AIHubTheme
                }
                AppBackground(showImage = screen != Screen.HOME) {
                    when (screen) {
                        Screen.HOME -> HomeScreen { screen = it }
                        Screen.CHAT -> ChatScreen(advanced = false, onBack = back)
                        Screen.ADV_CHAT -> ChatScreen(advanced = true, onBack = back)
                        Screen.VOICE_CHAT -> VoiceChatScreen(back)
                        Screen.VOICE_CHANGER -> VoiceChangerScreen(back)
                        Screen.IMAGE_EDIT -> ImageEditScreen(back)
                        Screen.ANIMATE -> StoryScreen(back, animate = true)
                        Screen.STORY -> StoryScreen(back)
                        Screen.QR -> QrScreen(back)
                        Screen.PASSPORT -> PassportScreen(back)
                        Screen.SCAN -> ScanScreen(back)
                        Screen.SHRINK -> ShrinkScreen(back)
                        Screen.REMOVE -> RemoveScreen(back)
                        Screen.ENHANCE -> EnhanceScreen(back)
                        Screen.ART -> ArtScreen(back)
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
                        Screen.INVITE -> ir.hooshyar.app.screens.InviteScreen(back)
                        Screen.ACCOUNT -> ir.hooshyar.app.screens.AccountScreen(back)
                        Screen.PLANS -> ir.hooshyar.app.screens.PlansScreen(back, { screen = Screen.SETTINGS }, { screen = Screen.ACCOUNT })
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

