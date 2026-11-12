package ir.hooshyar.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** ارتباط با سرور هوشیار: وضعیت سهمیه و فعال‌سازی اشتراک با کد */
object Server {
    private fun fail(code: Int, raw: String): AiException {
        val m = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
        return AiException(m.ifBlank { "خطای سرور ($code)" })
    }

    /** متن آماده‌ی نمایش وضعیت (طرح و سهمیه‌ی امروز) */
    suspend fun status(): String = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("آدرس سرور تنظیم نشده است.")
        val req = Request.Builder().url(Provider.SERVER.baseUrl + "/me")
            .header("Authorization", "Bearer ${Store.deviceId}").build()
        try {
            Net.http.newCall(req).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw fail(r.code, raw)
                val o = JSONObject(raw)
                fun q(k: String) = o.optJSONArray(k)?.let { "${it.optInt(0)}/${it.optInt(1)}" } ?: "-"
                val until = o.optLong("until")
                Store.savePaid(until)
                val plan = if (o.optString("plan") == "paid") {
                    val days = ((until - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                    "اشتراک فعال ($days روز مانده)"
                } else "طرح رایگان"
                "$plan\nامروز: پیام ${q("chat")} • خواندن متن ${q("tts")} • تشخیص گفتار ${q("stt")}" +
                    (if (o.optString("plan") == "paid") "\nآواز حرفه‌ای این ماه: ${q("music")}" else "")
            }
        } catch (e: IOException) {
            throw AiException("اتصال به سرور برقرار نشد.")
        }
    }

    /** فقط وضعیت اشتراک را از سرور می‌گیرد و ذخیره می‌کند (بی‌صدا) */
    suspend fun refresh() {
        try { status() } catch (e: Exception) { /* بدون اینترنت: وضعیت قبلی می‌ماند */ }
    }

    /** فعال‌سازی اشتراک با کد؛ در صورت موفقیت true */
    suspend fun redeem(code: String): Unit = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("آدرس سرور تنظیم نشده است.")
        val body = JSONObject().put("code", code.trim()).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(Provider.SERVER.baseUrl + "/redeem")
            .header("Authorization", "Bearer ${Store.deviceId}").post(body).build()
        try {
            Net.http.newCall(req).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw fail(r.code, raw)
            }
        } catch (e: IOException) {
            throw AiException("اتصال به سرور برقرار نشد.")
        }
    }
}

/** اطلاعات دعوت دوستان */
class RefInfo(val code: String, val invited: Int, val rewarded: Int, val earnedDays: Int, val max: Int, val inviterDays: Int, val inviteeDays: Int, val usedCode: Boolean)

object Referral {
    private fun call(path: String, body: String?): String {
        val b = Request.Builder().url(Provider.SERVER.baseUrl + path).header("Authorization", "Bearer ${Store.deviceId}")
        if (body != null) b.post(body.toRequestBody("application/json".toMediaType()))
        try {
            Net.http.newCall(b.build()).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val m = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
                    throw AiException(m.ifBlank { "خطای سرور (${r.code})" })
                }
                return raw
            }
        } catch (e: IOException) { throw AiException("اتصال به سرور برقرار نشد.") }
    }

    suspend fun info(): RefInfo = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("آدرس سرور تنظیم نشده است.")
        val o = JSONObject(call("/ref", null))
        RefInfo(o.optString("code"), o.optInt("invited"), o.optInt("rewarded"), o.optInt("earnedDays"), o.optInt("max"), o.optInt("inviterDays"), o.optInt("inviteeDays"), o.optBoolean("usedCode"))
    }

    /** ثبت کد دعوت دوست؛ تعداد روز هدیه را برمی‌گرداند */
    suspend fun use(code: String): Int = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("آدرس سرور تنظیم نشده است.")
        JSONObject(call("/ref/use", JSONObject().put("code", code.trim()).toString())).optInt("days")
    }
}

/** حساب کاربری با ایمیل و کد تأیید (فقط هنگام خرید یا بازیابی اشتراک لازم است) */
object Account {
    private fun call(path: String, body: JSONObject?): JSONObject {
        val b = Request.Builder().url(Provider.SERVER.baseUrl + path).header("Authorization", "Bearer ${Store.deviceId}")
        if (body != null) b.post(body.toString().toRequestBody("application/json".toMediaType()))
        try {
            Net.http.newCall(b.build()).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val m = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
                    throw AiException(m.ifBlank { "خطای سرور (${r.code})" })
                }
                return JSONObject(raw)
            }
        } catch (e: IOException) { throw AiException("اتصال به سرور برقرار نشد.") }
    }

    suspend fun sendCode(email: String) = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("آدرس سرور تنظیم نشده است.")
        call("/auth/request", JSONObject().put("email", email.trim()))
        Unit
    }

    suspend fun verify(email: String, code: String) = withContext(Dispatchers.IO) {
        val o = call("/auth/verify", JSONObject().put("email", email.trim()).put("code", code.trim()))
        Store.saveAccount(o.optString("label").ifBlank { o.optString("email") })
        Store.savePaid(o.optLong("until"))
    }

    /** شروع ورود با تلگرام: (توکن، لینک ربات) */
    suspend fun tgStart(): Pair<String, String> = withContext(Dispatchers.IO) {
        if (!Store.serverOn) throw AiException("آدرس سرور تنظیم نشده است.")
        val o = call("/auth/tg/start", JSONObject())
        o.optString("token") to o.optString("link")
    }

    /** بررسی اینکه کاربر در ربات تأیید کرده یا نه؛ true یعنی وارد شد */
    suspend fun tgCheck(token: String): Boolean = withContext(Dispatchers.IO) {
        val o = call("/auth/tg/check", JSONObject().put("token", token))
        if (!o.optBoolean("done")) return@withContext false
        Store.saveAccount(o.optString("label").ifBlank { o.optString("email") })
        Store.savePaid(o.optLong("until"))
        true
    }

    /** وضعیت حساب را از سرور می‌گیرد (بی‌صدا) */
    suspend fun sync() = withContext(Dispatchers.IO) {
        try {
            val o = call("/auth/me", null)
            Store.saveAccount(if (o.isNull("email")) "" else o.optString("label").ifBlank { o.optString("email") })
            Store.savePaid(o.optLong("until"))
        } catch (e: Exception) { /* آفلاین */ }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        call("/auth/logout", JSONObject())
        Store.saveAccount("")
        try { Store.savePaid(call("/auth/me", null).optLong("until")) } catch (e: Exception) { }
    }
}
