package com.example.aihub.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Config {
    const val APP_VERSION = "3.1.1"
    /** آواز حرفه‌ای (ElevenLabs Music) — فعلاً خاموش؛ با داشتن پلن پولی true شود */
    const val PRO_SONG = false
    const val APP_NAME = "هوش مصنوعی هوشیار"
    /** نشانی Worker خودت (بعد از راه‌اندازی سرور اینجا بگذار؛ مثلاً https://hooshyar.NAME.workers.dev). خالی = فقط کلید خود کاربر */
    const val SERVER_URL = "https://hooshyar.hurehsamadi.workers.dev"
    const val OWNER = "نوید سالاری"
    const val CONTACT_EMAIL = "navidsalary55@gmail.com"
    const val ID_TELEGRAM = "@na112233vi"
    const val ID_RUBIKA = "@na112233vi"
    const val ID_WHATSAPP = "@na11223344vad"
    const val URL_TELEGRAM = "https://t.me/na112233vi"
    const val URL_RUBIKA = "https://rubika.ir/na112233vi"
}

/** سرویس‌دهنده‌های هوش مصنوعی (همه با فرمت سازگار با OpenAI) */
enum class Provider(
    val label: String,
    val base: String,
    val defaultModel: String,
    val visionModel: String?,
    val keyUrl: String?,
    val needsKey: Boolean,
    val note: String,
) {
    SERVER(
        "سرور هوشیار", "", "llama-3.3-70b-versatile", "meta-llama/llama-4-scout-17b-16e-instruct", null, false,
        "بدون نیاز به کلید؛ سهمیه‌ی رایگان روزانه دارد و با اشتراک بیشتر می‌شود. صدا و آواز حرفه‌ای هم از همین سرور می‌آید.",
    ),
    GROQ(
        "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
        "meta-llama/llama-4-scout-17b-16e-instruct", "https://console.groq.com/keys", true,
        "خیلی سریع و رایگان؛ حدود ۱۰۰۰ درخواست در روز؛ از عکس هم پشتیبانی می‌کند. (پیشنهاد اصلی)",
    ),
    CEREBRAS(
        "Cerebras", "https://api.cerebras.ai/v1", "gpt-oss-120b", null, "https://cloud.cerebras.ai", true,
        "سهمیه‌ی روزانه‌ی زیاد (حدود ۱ میلیون توکن)؛ فقط متن. برای ساخت سایت و متن‌های بلند عالی است.",
    ),
    OPENROUTER(
        "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-oss-20b:free", null, "https://openrouter.ai/keys", true,
        "مدل‌های رایگان متنوع؛ حدود ۵۰ درخواست در روز.",
    ),
    MISTRAL(
        "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", null, "https://console.mistral.ai/api-keys", true,
        "پلن رایگان Experiment؛ فارسی خوب و پشتیبانی از عکس.",
    ),
    GEMINI(
        "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", null,
        "https://aistudio.google.com/apikey", true,
        "گوگل؛ بدون VPN ممکن است در بعضی مناطق کار نکند.",
    ),
    POLLINATIONS(
        "Pollinations", "https://text.pollinations.ai/openai", "openai", null, null, false,
        "بدون کلید و بدون ثبت‌نام؛ کندتر و محدود به سرعت. برای تست سریع خوب است.",
    );

    /** نشانی واقعی؛ برای سرور هوشیار از تنظیمات خوانده می‌شود */
    val baseUrl: String get() = if (this == SERVER) Store.serverUrl.trimEnd('/') + "/v1" else base

    /** آیا این سرویس عکس را می‌فهمد */
    val supportsVision: Boolean get() = visionModel != null || this == MISTRAL || this == GEMINI
}

object Store {
    private lateinit var p: SharedPreferences

    var provider by mutableStateOf(Provider.GROQ)
        private set
    var apiKey by mutableStateOf("")
        private set
    var model by mutableStateOf(Provider.GROQ.defaultModel)
        private set
    /** 0 = سیستم، 1 = روشن، 2 = تاریک */
    var themeMode by mutableStateOf(0)
        private set

    val needsKey: Boolean get() = provider.needsKey && apiKey.isBlank()

    /** آدرس سرور خودمان و شناسه‌ی ناشناس این گوشی (برای سهمیه و اشتراک) */
    var serverUrl by mutableStateOf(Config.SERVER_URL)
        private set
    var deviceId by mutableStateOf("")
        private set
    val serverOn: Boolean get() = serverUrl.isNotBlank()

    /** پنجره‌ی «برای ذخیره اشتراک لازم است» */
    var showPaywall by mutableStateOf(false)

    /** تا چه زمانی اشتراک فعال است (میلی‌ثانیه)؛ از سرور گرفته و ذخیره می‌شود */
    var paidUntil by mutableStateOf(0L)
        private set
    val paid: Boolean get() = paidUntil > System.currentTimeMillis()
    fun savePaid(until: Long) {
        paidUntil = until
        p.edit().putLong("paid_until", until).apply()
    }

    fun saveServer(url: String) {
        serverUrl = url.trim().trimEnd('/')
        p.edit().putString("server_url", serverUrl).apply()
    }

    /** کلید اختیاری Pollinations (از enter.pollinations.ai) برای تصویر و صدای پایدارتر */
    var pollKey by mutableStateOf("")
        private set
    /** اگر سرویس انتخابی خطا داد، خودکار سرویس‌های دیگرِ دارای کلید را امتحان کن */
    var autoFallback by mutableStateOf(true)
        private set
    /** نام صدای ربات در چت صوتی (alloy, nova, shimmer, echo, onyx, fable) */
    var botVoice by mutableStateOf("nova")
        private set
    /** ربات با صدای آنلاین (طبیعی‌تر) جواب بدهد؟ در غیر این صورت صدای خود گوشی */
    var voiceOnline by mutableStateOf(true)
        private set
    /** پاسخ ربات در چت متنی هم بلند خوانده شود؟ */
    var autoSpeak by mutableStateOf(false)
        private set

    /** ضریب سرعت و تُن صدای خود گوشی (برای eSpeak و موتورهای دیگر) */
    var ttsRate by mutableStateOf(0.9f)
        private set
    var ttsPitch by mutableStateOf(0.9f)
        private set

    fun saveTts(rate: Float, pitch: Float) {
        ttsRate = rate; ttsPitch = pitch
        p.edit().putFloat("tts_rate", rate).putFloat("tts_pitch", pitch).apply()
    }

    /** کلید اختیاری و پولی ElevenLabs برای ساخت آواز واقعی (فقط روی همین گوشی ذخیره می‌شود) */
    var musicKey by mutableStateOf("")
        private set

    fun saveMusicKey(key: String) {
        musicKey = key.trim()
        p.edit().putString("music_key", musicKey).apply()
    }

    fun getStr(key: String): String = p.getString(key, "") ?: ""
    fun putStr(key: String, value: String) { p.edit().putString(key, value).apply() }

    fun guideSeen(id: String): Boolean = p.getBoolean("guide_$id", false)
    fun markGuide(id: String) { p.edit().putBoolean("guide_$id", true).apply() }

    var reminderOn by mutableStateOf(false)
        private set
    var reminderHour by mutableStateOf(9)
        private set

    fun saveReminder(on: Boolean, hour: Int) {
        reminderOn = on; reminderHour = hour
        p.edit().putBoolean("rem_on", on).putInt("rem_hour", hour).apply()
    }

    fun savePoll(key: String) {
        pollKey = key.trim()
        p.edit().putString("poll_key", pollKey).apply()
    }
    fun saveFlags(fallback: Boolean, online: Boolean, voice: String) {
        autoFallback = fallback; voiceOnline = online; botVoice = voice
        p.edit().putBoolean("fallback", fallback).putBoolean("v_online", online).putString("bot_voice", voice).apply()
    }
    fun saveAutoSpeak(v: Boolean) {
        autoSpeak = v
        p.edit().putBoolean("auto_speak", v).apply()
    }

    fun keyFor(pr: Provider): String =
        if (pr == Provider.SERVER) (if (serverOn) deviceId else "") else p.getString("key_${pr.name}", "") ?: ""
    fun modelFor(pr: Provider): String = p.getString("model_${pr.name}", pr.defaultModel) ?: pr.defaultModel

    fun init(ctx: Context) {
        p = ctx.applicationContext.getSharedPreferences("aihub", Context.MODE_PRIVATE)
        serverUrl = p.getString("server_url", Config.SERVER_URL) ?: Config.SERVER_URL
        var did = p.getString("device_id", "") ?: ""
        if (did.length < 16) {
            did = java.util.UUID.randomUUID().toString()
            p.edit().putString("device_id", did).apply()
        }
        deviceId = did
        paidUntil = p.getLong("paid_until", 0L)
        val firstProv = if (serverOn) Provider.SERVER.name else Provider.GROQ.name
        provider = try {
            if (Config.SERVER_URL.isNotBlank()) Provider.SERVER else Provider.valueOf(p.getString("provider", firstProv) ?: firstProv)
        } catch (e: Exception) {
            Provider.GROQ
        }
        apiKey = keyFor(provider)
        model = modelFor(provider)
        themeMode = p.getInt("theme", 0)
        pollKey = p.getString("poll_key", "") ?: ""
        autoFallback = p.getBoolean("fallback", true)
        voiceOnline = p.getBoolean("v_online", true)
        botVoice = p.getString("bot_voice", "nova") ?: "nova"
        autoSpeak = p.getBoolean("auto_speak", false)
        musicKey = p.getString("music_key", "") ?: ""
        ttsRate = p.getFloat("tts_rate", 0.9f)
        ttsPitch = p.getFloat("tts_pitch", 0.9f)
        reminderOn = p.getBoolean("rem_on", false)
        reminderHour = p.getInt("rem_hour", 9)
    }

    fun save(pr: Provider, key: String, modelName: String) {
        val m = modelName.ifBlank { pr.defaultModel }
        p.edit().putString("provider", pr.name)
            .putString("key_${pr.name}", key)
            .putString("model_${pr.name}", m).apply()
        provider = pr
        apiKey = key
        model = m
    }

    fun saveTheme(mode: Int) {
        themeMode = mode
        p.edit().putInt("theme", mode).apply()
    }
}
