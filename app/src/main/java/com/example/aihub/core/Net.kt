package com.example.aihub.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object Net {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** دانلود بایت‌ها؛ در صورت خطای موقت (محدودیت نرخ) چند بار دوباره تلاش می‌کند. */
    suspend fun getBytes(url: String, tries: Int = 4, waitMs: Long = 7000): ByteArray =
        withContext(Dispatchers.IO) {
            var last: Exception = IOException("خطای ناشناخته در دانلود")
            for (i in 0 until tries) {
                try {
                    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (r.isSuccessful) {
                            val b = r.body?.bytes()
                            if (b != null && b.isNotEmpty()) return@withContext b
                            last = IOException("پاسخ خالی")
                        } else {
                            last = IOException("HTTP ${r.code}")
                        }
                    }
                } catch (e: IOException) {
                    last = e
                }
                if (i < tries - 1) delay(waitMs)
            }
            throw AiException(
                "دریافت از سرویس رایگان ممکن نشد (${last.message}). ممکن است شلوغ باشد؛ چند لحظه بعد دوباره امتحان کن."
            )
        }

    /** متن خوانای یک صفحه‌ی وب (برای خلاصه‌ساز)؛ تگ‌ها و اسکریپت‌ها حذف می‌شوند. */
    suspend fun pageText(url: String, maxChars: Int = 9000): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) AIHub").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw AiException("باز کردن لینک ممکن نشد (HTTP ${r.code}).")
                val html = r.body?.string().orEmpty()
                val text = html
                    .replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("(?s)<[^>]+>"), " ")
                    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                    .replace(Regex("\\s+"), " ").trim()
                if (text.length < 80) throw AiException("متن قابل خواندنی در این لینک پیدا نشد. متن را مستقیم بچسبان.")
                text.take(maxChars)
            }
        } catch (e: IOException) {
            throw AiException("اتصال برقرار نشد یا لینک نامعتبر است.")
        } catch (e: IllegalArgumentException) {
            throw AiException("نشانی لینک درست نیست.")
        }
    }

    /** GET ساده با یک هدر دلخواه؛ متن پاسخ را برمی‌گرداند. */
    suspend fun getText(url: String, headerName: String, headerValue: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header(headerName, headerValue).build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw AiException("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw AiException(e.message ?: "قطع اتصال")
        }
    }

    /** درخواست POST با بدنه‌ی JSON و کلید Bearer؛ فقط پاسخ صوتی معتبر قبول است. */
    suspend fun postAudio(
        url: String, key: String, json: String,
        headerName: String = "Authorization", headerValue: String = "Bearer $key",
        prefix: String = "audio/",
    ): ByteArray = withContext(Dispatchers.IO) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).header(headerName, headerValue).post(body).build()
        try {
            http.newCall(req).execute().use { r ->
                val type = r.header("Content-Type").orEmpty()
                if (!r.isSuccessful) {
                    val raw = try { r.body?.string().orEmpty() } catch (e: Exception) { "" }
                    val nice = try { org.json.JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
                    if (nice.any { it in '\u0600'..'\u06FF' }) throw AiException(nice)
                    throw AiException("HTTP ${r.code} ${raw.take(160)}".trim())
                }
                val b = r.body?.bytes()
                if (!type.startsWith(prefix) || b == null || b.size < 200) throw AiException("پاسخ نامعتبر ($type)")
                b
            }
        } catch (e: IOException) {
            throw AiException(e.message ?: "قطع اتصال")
        }
    }

    /** دانلود از چند نشانی به ترتیب؛ فقط پاسخی که نوع محتوایش با prefix شروع شود (مثلاً image/ یا audio/) قبول است. */
    suspend fun fetchTyped(urls: List<String>, prefix: String, tries: Int = 2, waitMs: Long = 5000, minSize: Int = 200): ByteArray =
        withContext(Dispatchers.IO) {
            var last = "پاسخ نامعتبر"
            for (url in urls) {
                for (i in 0 until tries) {
                    try {
                        val rb = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                        http.newCall(rb.build()).execute().use { r ->
                            val type = r.header("Content-Type").orEmpty()
                            if (r.isSuccessful && type.startsWith(prefix)) {
                                val b = r.body?.bytes()
                                if (b != null && b.size > minSize) return@withContext b
                                last = "پاسخ خالی"
                            } else {
                                last = if (r.isSuccessful) "نوع پاسخ نادرست" else "HTTP ${r.code}"
                            }
                            Unit
                        }
                    } catch (e: IOException) {
                        last = e.message ?: "قطع اتصال"
                    }
                    if (last.startsWith("HTTP 4") && last != "HTTP 429") break
                    if (i < tries - 1) delay(waitMs)
                }
            }
            throw AiException(
                "سرویس جواب نداد ($last). چند دقیقه بعد دوباره امتحان کن."
            )
        }
}

/** سرویس رایگان و بدون کلید Pollinations برای تصویر و صدا */
object Poll {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun imageUrls(prompt: String, w: Int, h: Int, seed: Int): List<String> {
        val q = enc(prompt.take(700))
        val out = ArrayList<String>()
        if (Store.pollKey.isNotBlank()) {
            out.add("https://gen.pollinations.ai/image/$q?width=$w&height=$h&seed=$seed&model=flux&nologo=true&key=${Store.pollKey}")
        }
        out.add("https://image.pollinations.ai/prompt/$q?width=$w&height=$h&seed=$seed&nologo=true&model=flux")
        return out
    }

    /** ساخت تصویر؛ با کلید (در صورت وجود) و بعد مسیر بدون کلید */
    suspend fun image(prompt: String, w: Int, h: Int, seed: Int = Random.nextInt(1_000_000)): ByteArray {
        if (!Store.serverOn) throw AiException("سرویس هنوز راه‌اندازی نشده است.")
        run {
            val json = org.json.JSONObject().put("prompt", prompt.take(700)).put("w", w).put("h", h).put("seed", seed).toString()
            return Net.postAudio(Provider.SERVER.baseUrl + "/image", Store.deviceId, json, prefix = "image/")
        }
        return Net.fetchTyped(imageUrls(prompt, w, h, seed), "image/", tries = 3, waitMs = 7000)
    }

    /** صدای آنلاین (mp3) با صدای آماده‌ی هوش مصنوعی */
    suspend fun speech(text: String, voice: String, lang: String? = null): ByteArray {
        val l = lang ?: guessLang(text)
        val male = voice in setOf("onyx", "echo", "fable")
        val errs = ArrayList<String>()
        val q = enc(text.take(600))
        val poll = ArrayList<String>()
        if (Store.pollKey.isNotBlank()) {
            poll.add("https://gen.pollinations.ai/audio/$q?voice=$voice&model=openai-audio&key=${Store.pollKey}")
        }
        poll.add("https://text.pollinations.ai/$q?model=openai-audio&voice=$voice")
        // با کلید Pollinations همان اول؛ بدون کلید ابتدا صدای مایکروسافت (فارسی خوب)
        val base = if (Store.pollKey.isBlank()) listOf(1, 0, 2) else listOf(0, 1, 2)
        if (!Store.serverOn) { lastError = "سرور تنظیم نشده"; throw AiException("سرویس صدا هنوز راه‌اندازی نشده است.") }
        val order = listOf(4)
        for (step in order) {
            try {
                val r = when (step) {
                    0 -> pollSpeech(poll, text, voice)
                    1 -> EdgeTts.speak(text.take(600), l, male)
                    3 -> elevenSpeech(text, male)
                    4 -> serverSpeech(text, voice, l)
                    else -> googleSpeech(text, l)
                }
                lastSource = listOf("Pollinations", "Microsoft", "Google", "ElevenLabs", "Server")[step]
                return r
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val raw = e.message ?: "خطا"
                val short = if (raw.contains("(")) raw.substringAfter("(").substringBefore(")") else raw
                errs.add(listOf("Pollinations", "Edge", "Google", "ElevenLabs", "سرور")[step] + ": " + short.take(70))
            }
        }
        lastError = errs.joinToString(" | ").take(300)
        throw AiException("هیچ سرویس صدایی جواب نداد ($lastError)")
    }

    /** با کلید: مسیر رسمی POST /v1/audio/speech؛ بدون کلید: مسیر قدیمی GET */
    private suspend fun pollSpeech(getUrls: List<String>, text: String, voice: String): ByteArray {
        val key = Store.pollKey
        if (key.isNotBlank()) {
            val esc = org.json.JSONObject.quote(text.take(900))
            var last: AiException? = null
            for (model in listOf("", "openai-audio", "elevenlabs/eleven-flash-v2.5")) {
                val m = if (model.isEmpty()) "" else ",\"model\":\"$model\""
                val json = "{\"input\":$esc,\"voice\":\"$voice\",\"response_format\":\"mp3\"$m}"
                try {
                    return Net.postAudio("https://gen.pollinations.ai/v1/audio/speech", key, json)
                } catch (e: AiException) {
                    last = e
                }
            }
            throw last ?: AiException("Pollinations")
        }
        return Net.fetchTyped(getUrls, "audio/", tries = 1, waitMs = 2000)
    }

    /** سرویسی که آخرین صدا را ساخت (برای نمایش در تست) */
    @Volatile var lastSource: String = ""

    private val elVoices = HashMap<Boolean, String>()

    /** صدای زن/مرد از فهرست صداهای حساب ElevenLabs (اگر نشد، شناسه‌ی پیش‌فرض) */
    private suspend fun elevenVoice(male: Boolean): String {
        elVoices[male]?.let { return it }
        var id = if (male) "JBFqnCBsd6RMkjVDRZzb" else "EXAVITQu4vr4xnSDxMaL"
        try {
            val txt = Net.getText("https://api.elevenlabs.io/v1/voices", "xi-api-key", Store.musicKey)
            val arr = org.json.JSONObject(txt).optJSONArray("voices")
            if (arr != null) {
                val want = if (male) "male" else "female"
                var pick: String? = null
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val g = v.optJSONObject("labels")?.optString("gender").orEmpty()
                    if (g == want) {
                        val vid = v.optString("voice_id")
                        if (vid.isNotBlank()) { pick = vid; if (v.optString("category") == "premade") break }
                    }
                }
                if (pick != null) id = pick
            }
        } catch (e: Exception) {
            // همان شناسه‌ی پیش‌فرض
        }
        elVoices[male] = id
        return id
    }

    /** صدا از سرور خودمان (ElevenLabs برای اشتراک‌داران، در غیر این صورت صدای مایکروسافت) */
    private suspend fun serverSpeech(text: String, voice: String, lang: String): ByteArray {
        val json = org.json.JSONObject().put("text", text.take(1200)).put("voice", voice).put("lang", lang).toString()
        return Net.postAudio(Provider.SERVER.baseUrl + "/tts", Store.deviceId, json)
    }

    private suspend fun elevenSpeech(text: String, male: Boolean): ByteArray {
        val vid = elevenVoice(male)
        var last: AiException? = null
        for (model in listOf("eleven_v3", "eleven_multilingual_v2")) {
            val json = org.json.JSONObject().put("text", text.take(2500)).put("model_id", model).toString()
            try {
                return Net.postAudio(
                    "https://api.elevenlabs.io/v1/text-to-speech/$vid?output_format=mp3_44100_128", "", json,
                    "xi-api-key", Store.musicKey,
                )
            } catch (e: AiException) {
                last = e
                val m = e.message.orEmpty()
                if (m.contains("HTTP 401") || m.contains("HTTP 402")) break
            }
        }
        throw last ?: AiException("ElevenLabs")
    }

    /** آخرین علت خطای صدا (برای نمایش به کاربر) */
    @Volatile var lastError: String = ""

    private fun guessLang(t: String) =
        if (t.any { it in '؀'..'ۿ' }) "fa" else "en"

    /** تکه‌های کوتاه (حداکثر ~180 نویسه) را از صدای مترجم گوگل می‌گیرد و پشت هم می‌چسباند */
    private suspend fun googleSpeech(text: String, lang: String): ByteArray {
        val parts = splitSpeech(text, 180)
        val all = java.io.ByteArrayOutputStream()
        for (p in parts) {
            val u = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=$lang&q=${enc(p)}"
            all.write(Net.fetchTyped(listOf(u), "audio/", tries = 2, waitMs = 1500, minSize = 100))
        }
        return all.toByteArray()
    }

    /** تبدیل توضیح فارسی به پرامپت انگلیسی دقیق (اگر کلید جمینای باشد) */
    suspend fun betterPrompt(userText: String, style: String): String {
        return try {
            Ai.generate(
                "User idea: $userText\nStyle: $style",
                system = "You write prompts for an AI image generator. Convert the user's idea (any language) into ONE detailed " +
                    "English prompt (max 60 words) including subject, composition, lighting and the requested style. " +
                    "Output only the prompt, nothing else.",
                temperature = 0.7,
            ).trim()
        } catch (e: Exception) {
            "$userText, $style style"
        }
    }
}
