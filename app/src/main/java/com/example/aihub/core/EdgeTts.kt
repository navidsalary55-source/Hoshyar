package com.example.aihub.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** صدای عصبی رایگان مایکروسافت (همان که مرورگر Edge برای «خواندن با صدای بلند» می‌گیرد)؛ فارسی با صدای خوب دارد. */
object EdgeTts {
    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val VER = "143.0.3650.75"

    private val voices = mapOf(
        "fa" to ("fa-IR-DilaraNeural" to "fa-IR-FaridNeural"),
        "en" to ("en-US-JennyNeural" to "en-US-GuyNeural"),
        "ar" to ("ar-SA-ZariyahNeural" to "ar-SA-HamedNeural"),
        "tr" to ("tr-TR-EmelNeural" to "tr-TR-AhmetNeural"),
        "de" to ("de-DE-KatjaNeural" to "de-DE-ConradNeural"),
        "fr" to ("fr-FR-DeniseNeural" to "fr-FR-HenriNeural"),
        "es" to ("es-ES-ElviraNeural" to "es-ES-AlvaroNeural"),
        "it" to ("it-IT-ElsaNeural" to "it-IT-DiegoNeural"),
        "ru" to ("ru-RU-SvetlanaNeural" to "ru-RU-DmitryNeural"),
        "zh" to ("zh-CN-XiaoxiaoNeural" to "zh-CN-YunxiNeural"),
        "ja" to ("ja-JP-NanamiNeural" to "ja-JP-KeitaNeural"),
        "ko" to ("ko-KR-SunHiNeural" to "ko-KR-InJoonNeural"),
    )

    private fun gec(): String {
        val secs = System.currentTimeMillis() / 1000 + 11644473600L
        val ticks = (secs - secs % 300) * 10_000_000L
        val d = MessageDigest.getInstance("SHA-256").digest("$ticks$TOKEN".toByteArray(Charsets.US_ASCII))
        return d.joinToString("") { "%02X".format(it) }
    }

    private fun stamp(): String {
        val f = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** mp3 می‌سازد؛ در صورت خطا AiException می‌اندازد. [male] برای انتخاب صدای مردانه. */
    suspend fun speak(text: String, lang: String, male: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val pair = voices[lang] ?: voices.getValue("en")
        val voice = if (male) pair.second else pair.first
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$TOKEN&ConnectionId=${UUID.randomUUID().toString().replace("-", "")}" +
            "&Sec-MS-GEC=${gec()}&Sec-MS-GEC-Version=1-$VER"
        val req = Request.Builder().url(url)
            .header("Pragma", "no-cache").header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Sec-WebSocket-Version", "13")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=" + UUID.randomUUID().toString().replace("-", "").uppercase() + ";")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${VER.substringBefore('.')}.0.0.0 Safari/537.36 Edg/${VER.substringBefore('.')}.0.0.0")
            .build()
        val done = CompletableDeferred<ByteArray>()
        val audio = ByteArrayOutputStream()
        val ws = Net.http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val ts = stamp()
                webSocket.send(
                    "X-Timestamp:$ts\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\"," +
                        "\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                )
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                    "<voice name='$voice'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>${esc(text)}</prosody></voice></speak>"
                webSocket.send(
                    "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\nContent-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${ts}Z\r\nPath:ssml\r\n\r\n$ssml"
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    val b = audio.toByteArray()
                    if (b.size > 100) done.complete(b) else done.completeExceptionally(AiException("پاسخ صوتی خالی بود"))
                    webSocket.close(1000, null)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size < 2) return
                val hl = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                if (bytes.size < 2 + hl) return
                val head = bytes.substring(2, 2 + hl).utf8()
                if (head.contains("Path:audio")) {
                    val body = bytes.substring(2 + hl)
                    audio.write(body.toByteArray())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                done.completeExceptionally(AiException("Edge: ${response?.code ?: ""} ${t.message ?: ""}".trim()))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) {
                    val b = audio.toByteArray()
                    if (b.size > 100) done.complete(b) else done.completeExceptionally(AiException("Edge بسته شد"))
                }
            }
        })
        try {
            withTimeout(25_000) { done.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AiException("Edge: زمان تمام شد")
        } finally {
            ws.cancel()
        }
    }
}
