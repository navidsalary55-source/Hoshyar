package com.example.aihub.core
// (نام فایل برای سازگاری با آپلود روی نسخه‌ی قبلی همان Gemini.kt مانده؛ داخلش کلاینت چندسرویسی Ai است)

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class AiException(message: String) : Exception(message)

data class ChatMsg(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    val image: ByteArray? = null,
)

fun String.stripCodeFence(): String {
    var s = trim()
    if (s.startsWith("```")) {
        s = s.substringAfter('\n', s)
        s = s.substringBeforeLast("```")
    }
    return s.trim()
}

/** رشته‌ی کلید؛ اگر مقدار null یا نبود، رشته‌ی خالی (org.json برای null می‌نویسد "null"!) */
private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

/** یک مقصد برای ارسال درخواست: سرویس + کلید + مدل */
private data class Target(val pr: Provider, val base: String, val key: String, val model: String)

/** کلاینت یکپارچه برای همه‌ی سرویس‌دهنده‌های سازگار با OpenAI (Groq, Cerebras, OpenRouter, Mistral, Gemini, Pollinations) */
object Ai {
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()
    private val THINK = Regex("(?s)<think>.*?</think>")

    private fun pollTarget(): Target =
        if (Store.pollKey.isNotBlank()) Target(Provider.POLLINATIONS, "https://gen.pollinations.ai/v1", Store.pollKey, "openai")
        else Target(Provider.POLLINATIONS, Provider.POLLINATIONS.base, "", "openai")

    /** همه‌ی درخواست‌ها فقط به سرور خودمان می‌رود؛ کلیدها آنجاست */
    private fun targets(needVision: Boolean): List<Target> {
        if (!Store.serverOn) throw AiException("سرویس هنوز راه‌اندازی نشده است. کمی بعد دوباره تلاش کن.")
        return listOf(Target(Provider.SERVER, Provider.SERVER.baseUrl, Store.deviceId, Store.modelFor(Provider.SERVER)))
    }

    private fun content(m: ChatMsg): Any {
        val img = m.image ?: return m.text
        val arr = JSONArray()
        arr.put(JSONObject().put("type", "text").put("text", m.text.ifEmpty { "Describe this image." }))
        arr.put(
            JSONObject().put("type", "image_url").put(
                "image_url",
                JSONObject().put("url", "data:image/jpeg;base64," + Base64.encodeToString(img, Base64.NO_WRAP))
            )
        )
        return arr
    }

    private fun body(
        t: Target, history: List<ChatMsg>, system: String?, temperature: Double, json: Boolean, maxTokens: Int?, stream: Boolean,
        noThink: Boolean = false,
    ): String {
        val pr = t.pr
        val hasImage = history.any { it.image != null }
        val vm = pr.visionModel
        val model = if (hasImage && vm != null && t.model == pr.defaultModel) vm else t.model
        val msgs = JSONArray()
        var sys = system.orEmpty()
        if (json) sys += "\nReturn ONLY one valid JSON object. No markdown fences, no commentary."
        if (sys.isNotBlank()) msgs.put(JSONObject().put("role", "system").put("content", sys.trim()))
        history.forEach { m ->
            msgs.put(JSONObject().put("role", if (m.isUser) "user" else "assistant").put("content", content(m)))
        }
        val o = JSONObject().put("model", model).put("messages", msgs)
            .put("temperature", temperature).put("stream", stream)
        var mt = maxTokens
        if (pr == Provider.GROQ && mt != null) mt = min(mt, 6000)
        if (mt != null) o.put("max_tokens", mt)
        // مدل‌های استدلالی گاهی همه‌ی توکن‌ها را صرف فکر کردن می‌کنند و متن خالی برمی‌گردانند
        if (noThink && pr == Provider.GROQ) {
            val ml = model.lowercase()
            if (ml.contains("qwen")) o.put("reasoning_effort", "none")
            else if (ml.contains("gpt-oss")) o.put("reasoning_effort", "low")
        }
        return o.toString()
    }

    /** بخشی که الان درخواست می‌فرستد؛ سرور فقط «chat» را برای طرح رایگان قبول می‌کند */
    @Volatile private var feature: String = ""

    private fun request(t: Target, path: String, body: String?): Request {
        val b = Request.Builder().url(t.base + path)
        if (t.pr == Provider.SERVER && feature.isNotBlank()) b.addHeader("X-Feature", feature)
        if (t.key.isNotBlank()) b.addHeader("Authorization", "Bearer ${t.key}")
        if (body != null) b.post(body.toRequestBody(JSON_TYPE)) else b.get()
        return b.build()
    }

    private fun callOnce(t: Target, history: List<ChatMsg>, system: String?, temperature: Double, json: Boolean, maxTokens: Int?): String {
        try {
            for (attempt in 0..1) {
                val noThink = attempt == 1
                val req = request(t, "/chat/completions", body(t, history, system, temperature, json, maxTokens, false, noThink))
                val out: String = http.newCall(req).execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        // پارامتر تفکر پشتیبانی نشد؛ همان نتیجه‌ی خالی قبلی را نگه می‌داریم
                        if (noThink && r.code == 400) return@use ""
                        throw AiException(friendly(r.code, txt))
                    }
                    val msg = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    var o = (msg?.str("content") ?: "").replace(THINK, "").trim()
                    if (o.isEmpty() && json) {
                        // گاهی JSON داخل فیلد استدلال می‌ماند
                        o = (msg?.str("reasoning") ?: msg?.str("reasoning_content") ?: "").trim()
                    }
                    if (json) {
                        val a = o.indexOf('{')
                        val z = o.lastIndexOf('}')
                        o = if (a != -1 && z > a) o.substring(a, z + 1) else if (o.startsWith("{")) o else ""
                    }
                    o
                }
                if (out.isNotEmpty()) return out
            }
            throw AiException("پاسخی دریافت نشد. اگر مدل شما استدلالی است (مثل qwen)، در تنظیمات مدل را روی llama-3.3-70b-versatile بگذار.")
        } catch (e: IOException) {
            throw AiException("اتصال اینترنت برقرار نیست (اگر لازم است VPN را روشن کن).")
        }
    }

    /** پاسخ کامل (غیر Streaming) با جایگزینی خودکار سرویس در صورت خطا */
    suspend fun generateWith(
        history: List<ChatMsg>,
        system: String? = null,
        temperature: Double = 0.8,
        json: Boolean = false,
        maxTokens: Int? = null,
    ): String = withContext(Dispatchers.IO) {
        feature = ""
        val list = targets(history.any { it.image != null })
        var first: AiException? = null
        for (t in list) {
            try {
                return@withContext callOnce(t, history, system, temperature, json, maxTokens)
            } catch (e: AiException) {
                if (first == null) first = e
                // نام مدل ذخیره‌شده وجود ندارد؛ یک بار با مدل پیش‌فرض همان سرویس امتحان می‌کنیم
                if (e.message.orEmpty().startsWith("مدل پیدا نشد") && t.model != t.pr.defaultModel) {
                    try {
                        return@withContext callOnce(t.copy(model = t.pr.defaultModel), history, system, temperature, json, maxTokens)
                    } catch (e2: AiException) {
                        // ادامه با سرویس بعدی
                    }
                }
            }
        }
        throw first ?: AiException("خطای ناشناخته")
    }

    suspend fun generate(
        prompt: String,
        system: String? = null,
        image: ByteArray? = null,
        temperature: Double = 0.8,
        json: Boolean = false,
        maxTokens: Int? = null,
    ): String = generateWith(listOf(ChatMsg(0, true, prompt, image = image)), system, temperature, json, maxTokens)

    /** پاسخ زنده (Streaming)؛ اگر سرویس اول قبل از ارسال هر متنی خطا بدهد، سرویس بعدی امتحان می‌شود */
    fun stream(history: List<ChatMsg>, system: String?, temperature: Double, feature: String = ""): Flow<String> = callbackFlow {
        this@Ai.feature = feature
        val list = try {
            targets(history.any { it.image != null })
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        var currentCall: okhttp3.Call? = null
        launch(Dispatchers.IO) {
            var firstErr: Exception? = null
            for (t in list) {
                var sent = false
                try {
                    val call = http.newCall(request(t, "/chat/completions", body(t, history, system, temperature, false, null, true)))
                    currentCall = call
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) throw AiException(friendly(resp.code, resp.body?.string()))
                        val source = resp.body!!.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty()) continue
                            if (data == "[DONE]") break
                            val delta = try {
                                JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                            } catch (_: Exception) {
                                null
                            }
                            val txt = delta?.str("content").orEmpty()
                            if (txt.isNotEmpty()) {
                                sent = true
                                trySend(txt)
                            }
                        }
                    }
                    if (sent) {
                        close()
                        return@launch
                    }
                    if (firstErr == null) firstErr = AiException("پاسخی دریافت نشد. دوباره امتحان کن.")
                } catch (e: IOException) {
                    if (sent) {
                        close(AiException("اتصال قطع شد."))
                        return@launch
                    }
                    if (firstErr == null) firstErr = AiException("اتصال اینترنت برقرار نیست یا قطع شد.")
                } catch (e: Exception) {
                    if (sent) {
                        close(e)
                        return@launch
                    }
                    if (firstErr == null) firstErr = e
                }
            }
            close(firstErr ?: AiException("خطای ناشناخته"))
        }
        awaitClose { currentCall?.cancel() }
    }

    /** فهرست مدل‌های در دسترس سرویس‌دهنده‌ی فعلی */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val pr = Store.provider
        if (pr.needsKey && Store.apiKey.isBlank()) throw AiException("اول کلید API را وارد کن.")
        val t = if (pr == Provider.POLLINATIONS) pollTarget() else Target(pr, pr.baseUrl, Store.apiKey, Store.model)
        try {
            http.newCall(request(t, "/models", null)).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw AiException(friendly(r.code, txt))
                val arr = JSONObject(txt).optJSONArray("data") ?: JSONArray()
                val ids = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.str("id").orEmpty().removePrefix("models/")
                    if (id.isNotBlank()) ids.add(id)
                }
                ids.sorted()
            }
        } catch (e: IOException) {
            throw AiException("اتصال اینترنت برقرار نیست (اگر لازم است VPN را روشن کن).")
        }
    }

    /** آیا کلید Groq ذخیره شده (برای تبدیل گفتار به متن با Whisper) */
    fun canTranscribe(): Boolean = Store.serverOn

    /** تبدیل فایل صوتی (WAV) به متن با Whisper سرویس Groq؛ lang مثل fa, en یا خالی برای تشخیص خودکار */
    suspend fun transcribe(wav: ByteArray, lang: String?): String = withContext(Dispatchers.IO) {
        val viaServer = true
        val key = Store.deviceId
        if (!Store.serverOn) throw AiException("سرویس هنوز راه‌اندازی نشده است.")
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("file", "voice.wav", wav.toRequestBody("audio/wav".toMediaType()))
        if (!lang.isNullOrBlank()) mb.addFormDataPart("language", lang)
        val req = Request.Builder().url((if (viaServer) Provider.SERVER.baseUrl else Provider.GROQ.base) + "/audio/transcriptions")
            .addHeader("Authorization", "Bearer $key").post(mb.build()).build()
        try {
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw AiException(friendly(r.code, txt))
                JSONObject(txt).str("text").trim()
            }
        } catch (e: IOException) {
            throw AiException("اتصال اینترنت برقرار نیست (اگر لازم است VPN را روشن کن).")
        }
    }

    /** مثل transcribe ولی خط‌به‌خط با زمان شروع و پایان (ثانیه) برمی‌گرداند؛ برای متن آهنگ */
    suspend fun transcribeTimed(wav: ByteArray, lang: String?): List<Triple<Float, Float, String>> = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("سرویس هنوز راه‌اندازی نشده است.")
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("file", "song.wav", wav.toRequestBody("audio/wav".toMediaType()))
        if (!lang.isNullOrBlank()) mb.addFormDataPart("language", lang)
        val req = Request.Builder().url(Provider.SERVER.baseUrl + "/audio/transcriptions")
            .addHeader("Authorization", "Bearer " + Store.deviceId).post(mb.build()).build()
        try {
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw AiException(friendly(r.code, txt))
                val o = JSONObject(txt)
                val arr = o.optJSONArray("segments")
                val out = ArrayList<Triple<Float, Float, String>>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val tx = s.optString("text").trim()
                    if (tx.isNotEmpty()) out.add(Triple(s.optDouble("start").toFloat(), s.optDouble("end").toFloat(), tx))
                }
                if (out.isEmpty() && o.optString("text").isNotBlank()) out.add(Triple(0f, 5f, o.optString("text").trim()))
                out
            }
        } catch (e: IOException) {
            throw AiException("اتصال اینترنت برقرار نیست (اگر لازم است VPN را روشن کن).")
        }
    }

    private fun friendly(code: Int, body: String?): String {
        val apiMsg = try {
            val o = JSONObject(body ?: "")
            o.optJSONObject("error")?.str("message") ?: o.str("error")
        } catch (_: Exception) {
            ""
        }
        if (apiMsg.any { it in '\u0600'..'\u06FF' }) return apiMsg // پیام فارسی خود سرور هوشیار
        return when (code) {
            400 -> "درخواست نامعتبر بود. $apiMsg".trim()
            401 -> "ورود به سرور ناموفق بود. برنامه را به‌روز کن."
            402, 403 -> "دسترسی رد شد. $apiMsg".trim()
            404 -> "سرویس پیدا نشد. بعداً دوباره تلاش کن."
            413 -> "درخواست برای این مدل خیلی بزرگ است. متن کوتاه‌تر یا مدل دیگری انتخاب کن."
            429 -> "تعداد درخواست‌ها زیاد است. کمی صبر کن و دوباره امتحان کن."
            500, 502, 503 -> "سرور سرویس شلوغ است. چند لحظه بعد دوباره تلاش کن."
            else -> "خطا ($code) $apiMsg".trim()
        }
    }
}
