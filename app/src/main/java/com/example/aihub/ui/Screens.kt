package com.example.aihub.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val title: String, val sub: String, val icon: ImageVector, val c1: Color, val c2: Color,
) {
    HOME("خانه", "", Icons.Rounded.Home, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    CHAT("چت ساده", "گفتگوی سریع با هوش مصنوعی", Icons.Rounded.Chat, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    VOICE_CHAT("چت صوتی", "حرف بزن، جواب بشنو", Icons.Rounded.RecordVoiceOver, Color(0xFFFF6FB1), Color(0xFFFF9A62)),
    ADV_CHAT("چت پیشرفته", "شخصیت‌ها، عکس و خلاقیت", Icons.Rounded.AutoAwesome, Color(0xFF6C5CE7), Color(0xFFB76CFF)),
    VOICE_CHANGER("تغییر صدا", "صدات رو جالب کن", Icons.Rounded.GraphicEq, Color(0xFF12C2A7), Color(0xFF2EC4F1)),
    IMAGE_EDIT("ویرایش تصویر", "فیلتر، نور و رنگ", Icons.Rounded.PhotoFilter, Color(0xFFFF8A5C), Color(0xFFFF5C8A)),
    STORY("استودیوی استوری", "قالب، متن، استیکر و فیلتر برای اینستا", Icons.Rounded.AutoFixHigh, Color(0xFFFF5C8A), Color(0xFFFFA62B)),
    TRIM("برش و تبدیل", "موزیک و ویدیو رو برش بده", Icons.Rounded.ContentCut, Color(0xFF00C9A7), Color(0xFF5B8DEF)),
    ANIMATE("متحرک‌سازی تصویر", "عکس رو زنده کن", Icons.Rounded.Animation, Color(0xFF22C1C3), Color(0xFF5BC97B)),
    TRANSLATE("مترجم هوشمند", "۱۲ زبان زنده‌ی دنیا", Icons.Rounded.Translate, Color(0xFF3FA2F7), Color(0xFF2ED3C6)),
    SONG("ساخت آهنگ", "متن، آکورد و ملودی", Icons.Rounded.MusicNote, Color(0xFFFF5C8A), Color(0xFFB76CFF)),
    WEBSITE("ساخت سایت", "از توضیح تا صفحه‌ی وب", Icons.Rounded.Language, Color(0xFF4F8CFF), Color(0xFF7C6CFF)),
    DRAW("ساخت نقاشی", "از متن تصویر بساز", Icons.Rounded.Brush, Color(0xFFFFB74D), Color(0xFFFF6F91)),
    CHAR3D("کاراکتر سه‌بعدی", "بساز و بچرخونش", Icons.Rounded.ViewInAr, Color(0xFF8E54E9), Color(0xFF47B8E0)),
    TTS("متن به صدا", "متن رو بخون", Icons.Rounded.VolumeUp, Color(0xFF00C9A7), Color(0xFF3FA2F7)),
    SPEECH_VIDEO("گفتار به ویدیو", "ایده → ویدیوی اسلایدی", Icons.Rounded.Movie, Color(0xFFF857A6), Color(0xFFFF5858)),
    WRITER("دستیار نوشتن", "خلاصه، ایمیل، کپشن…", Icons.Rounded.EditNote, Color(0xFF56AB2F), Color(0xFFA8E063)),
    VISION("عکس‌خوان", "توضیح، متن‌خوانی و ترجمه‌ی عکس", Icons.Rounded.ImageSearch, Color(0xFF00B4DB), Color(0xFF0083B0)),
    SUMMARY("خلاصه‌ساز", "متن و لینک رو خلاصه کن", Icons.Rounded.Summarize, Color(0xFF11998E), Color(0xFF38EF7D)),
    QUIZ("آزمون‌ساز", "تست بساز و خودت رو بسنج", Icons.Rounded.Quiz, Color(0xFFFC5C7D), Color(0xFF6A82FB)),
    PLANNER("برنامه‌ریز", "هدف بده، چک‌لیست بگیر", Icons.Rounded.Checklist, Color(0xFFF7971E), Color(0xFFFFB300)),
    RECIPE("آشپز هوشمند", "با مواد خونه غذا پیشنهاد بگیر", Icons.Rounded.Restaurant, Color(0xFFFF512F), Color(0xFFDD2476)),
    BIZ("ایده‌ساز کسب‌وکار", "اسم، شعار، طرح و محتوا", Icons.Rounded.Lightbulb, Color(0xFF396AFC), Color(0xFF2948FF)),
    NOTES("یادداشت صوتی", "حرف بزن، متن و خلاصه بگیر", Icons.Rounded.KeyboardVoice, Color(0xFF834D9B), Color(0xFFD04ED6)),
    LANG_COACH("مربی زبان", "مکالمه با تصحیح اشتباهات", Icons.Rounded.Forum, Color(0xFF00C9A7), Color(0xFF3FA2F7)),
    SETTINGS("تنظیمات", "", Icons.Rounded.Settings, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    ABOUT("درباره ما", "", Icons.Rounded.Info, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    CONTACT("تماس با ما", "", Icons.Rounded.Email, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    SUPPORT("پشتیبانی", "", Icons.Rounded.HeadsetMic, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    RULES("قوانین", "", Icons.Rounded.Gavel, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    MANAGEMENT("مدیریت", "", Icons.Rounded.AdminPanelSettings, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    HELP("راهنما و آموزش", "", Icons.Rounded.School, Color(0xFF7C6CFF), Color(0xFF3FA2F7)),
    HISTORY("تاریخچه", "", Icons.Rounded.History, Color(0xFF7C6CFF), Color(0xFF3FA2F7));

    /** دسته‌بندی برای فیلتر صفحه‌ی اصلی */
    val category: String
        get() = when (this) {
            CHAT, VOICE_CHAT, ADV_CHAT -> "گفتگو"
            IMAGE_EDIT, STORY, ANIMATE, DRAW, VISION -> "تصویر"
            VOICE_CHANGER, TTS, NOTES, TRIM -> "صدا"
            TRANSLATE, WRITER, SUMMARY, QUIZ, LANG_COACH -> "نوشتن و یادگیری"
            SONG, WEBSITE, CHAR3D, SPEECH_VIDEO -> "ساخت و خلاقیت"
            PLANNER, RECIPE, BIZ -> "کاربردی"
            else -> ""
        }

    companion object {
        val categories = listOf("همه", "گفتگو", "تصویر", "صدا", "نوشتن و یادگیری", "ساخت و خلاقیت", "کاربردی")
        val tools: List<Screen> = values().filter { it !in listOf(HOME, SETTINGS, ABOUT, CONTACT, SUPPORT, RULES, MANAGEMENT, HELP, HISTORY) }
        val drawerItems: List<Screen> = listOf(HISTORY, HELP, CONTACT, SUPPORT, ABOUT, RULES, MANAGEMENT, SETTINGS)
    }
}
