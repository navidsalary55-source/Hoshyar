package com.example.aihub.core

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
