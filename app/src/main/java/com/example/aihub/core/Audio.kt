package com.example.aihub.core

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class Lang(val label: String, val name: String, val tag: String) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

val Langs = listOf(
    Lang("فارسی", "Persian", "fa-IR"),
    Lang("English", "English", "en-US"),
    Lang("العربية", "Arabic", "ar-SA"),
    Lang("Türkçe", "Turkish", "tr-TR"),
    Lang("Deutsch", "German", "de-DE"),
    Lang("Français", "French", "fr-FR"),
    Lang("Español", "Spanish", "es-ES"),
    Lang("Italiano", "Italian", "it-IT"),
    Lang("Русский", "Russian", "ru-RU"),
    Lang("中文", "Chinese", "zh-CN"),
    Lang("日本語", "Japanese", "ja-JP"),
    Lang("한국어", "Korean", "ko-KR"),
)

/** متن به صدا با موتور TTS خود گوشی */
class Speaker(ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { id?.let { waiters.remove(it)?.complete(Unit) } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { id?.let { waiters.remove(it)?.complete(Unit) } }
                })
            }
        }
    }

    fun isReady() = ready

    fun hasVoice(locale: Locale): Boolean {
        val r = tts?.isLanguageAvailable(locale) ?: return false
        return r >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun speak(text: String, rate: Float = 1f, pitch: Float = 1f, locale: Locale? = null) {
        val clean = text.take(3900)
        if (clean.isBlank()) return
        var waited = 0
        while (!ready && waited < 3000) { delay(200); waited += 200 }
        val t = tts
        if (t == null || !ready) { delay(clean.length * 60L); return }
        if (locale != null) t.language = locale
        t.setSpeechRate((rate * Store.ttsRate).coerceIn(0.3f, 2.5f))
        t.setPitch((pitch * Store.ttsPitch).coerceIn(0.3f, 2.0f))
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<Unit>()
        waiters[id] = d
        val r = t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        if (r != TextToSpeech.SUCCESS) {
            waiters.remove(id)
            delay(clean.length * 70L)
            return
        }
        try {
            withTimeoutOrNull(max(6000L, clean.length * 200L)) { d.await() }
        } catch (e: CancellationException) {
            t.stop()
            throw e
        } finally {
            waiters.remove(id)
        }
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }
}

/** تشخیص گفتار (میکروفون → متن) با سرویس خود اندروید */
class SpeechInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableStateOf(0f)
        private set

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(tag: String, onText: (String) -> Unit, onFail: (String) -> Unit) {
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { level = ((v + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(code: Int) { listening = false; level = 0f; onFail(errText(code)) }
            override fun onResults(b: Bundle?) {
                listening = false; level = 0f
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isBlank()) onFail(errText(SpeechRecognizer.ERROR_NO_MATCH)) else onText(t)
            }
            override fun onPartialResults(b: Bundle?) {
                partial = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        partial = ""
        listening = true
        r.startListening(i)
    }

    fun finish() { rec?.stopListening() }

    fun release() {
        rec?.destroy()
        rec = null
        listening = false
        level = 0f
    }

    private fun errText(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "چیزی نشنیدم، دوباره امتحان کن."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازه‌ی میکروفون داده نشده."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "برای تشخیص گفتار اینترنت لازم است."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سرویس مشغول است، دوباره تلاش کن."
        else -> "تشخیص گفتار خطا داد (کد $code). برنامه‌ی Google باید روی گوشی نصب و فعال باشد."
    }
}

// ---------------------------------------------------------------- ضبط و تغییر صدا

class PcmRecorder {
    val sampleRate = 16000
    @Volatile private var recording = false

    fun stop() { recording = false }

    /**
     * ضبط از میکروفون. اگر autoStop روشن باشد، بعد از پایان حرف زدن (سکوت حدود ۱.۴ ثانیه) خودکار تمام می‌شود.
     * onLevel شدت صدا (۰ تا ۱) را برای انیمیشن می‌دهد.
     */
    suspend fun record(maxMs: Int = 15000, autoStop: Boolean = false, onLevel: ((Float) -> Unit)? = null): ShortArray =
        withContext(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 4096) * 2,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                throw AiException("میکروفون در دسترس نیست. اجازه‌ی میکروفون را بررسی کن.")
            }
            val data = ShortArray(sampleRate * maxMs / 1000)
            var n = 0
            val buf = ShortArray(1024)
            var spoke = false
            var quietSamples = 0
            try {
                rec.startRecording()
                recording = true
                while (recording && n < data.size && isActive) {
                    val r = rec.read(buf, 0, buf.size)
                    if (r > 0) {
                        val c = min(r, data.size - n)
                        System.arraycopy(buf, 0, data, n, c)
                        n += c
                        var sum = 0.0
                        for (i in 0 until r) sum += buf[i].toDouble() * buf[i]
                        val rms = kotlin.math.sqrt(sum / r)
                        onLevel?.invoke((rms / 5000.0).toFloat().coerceIn(0f, 1f))
                        if (autoStop) {
                            if (rms > 1100) { spoke = true; quietSamples = 0 } else quietSamples += r
                            if (spoke && quietSamples > sampleRate * 14 / 10) break
                            if (!spoke && n > sampleRate * 9) break
                        }
                    }
                }
            } finally {
                recording = false
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
            }
            onLevel?.invoke(0f)
            data.copyOf(n)
        }
}

/** تبدیل نمونه‌های PCM مونو ۱۶ بیتی به فایل WAV */
fun pcmToWav(pcm: ShortArray, rate: Int): ByteArray {
    val dataLen = pcm.size * 2
    val bb = java.nio.ByteBuffer.allocate(44 + dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    bb.put("RIFF".toByteArray()); bb.putInt(36 + dataLen); bb.put("WAVE".toByteArray())
    bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
    bb.putInt(rate); bb.putInt(rate * 2); bb.putShort(2); bb.putShort(16)
    bb.put("data".toByteArray()); bb.putInt(dataLen)
    for (v in pcm) bb.putShort(v)
    return bb.array()
}

enum class VoiceFx(val label: String) {
    NORMAL("طبیعی"), CHIPMUNK("سنجاب"), DEEP("غول"), ROBOT("ربات"),
    ECHO("اکو"), ALIEN("بیگانه"), MONSTER("هیولا"),
}

/** خروجی: نمونه‌ها و نرخ نمونه‌برداری پخش */
fun applyFx(src: ShortArray, fx: VoiceFx, sr: Int): Pair<ShortArray, Int> {
    fun ring(data: ShortArray, hz: Double, mix: Double = 1.0): ShortArray {
        val out = ShortArray(data.size)
        for (i in data.indices) {
            val m = sin(2 * PI * hz * i / sr)
            val v = data[i] * (1 - mix + mix * m)
            out[i] = v.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
    return when (fx) {
        VoiceFx.NORMAL -> src to sr
        VoiceFx.CHIPMUNK -> src to (sr * 1.5).toInt()
        VoiceFx.DEEP -> src to (sr * 0.7).toInt()
        VoiceFx.ROBOT -> ring(src, 70.0) to sr
        VoiceFx.ALIEN -> ring(src, 28.0, 0.8) to (sr * 1.25).toInt()
        VoiceFx.MONSTER -> ring(src, 22.0, 0.5) to (sr * 0.62).toInt()
        VoiceFx.ECHO -> {
            val d1 = sr * 28 / 100
            val d2 = sr * 56 / 100
            val out = ShortArray(src.size + d2 + sr / 4)
            for (i in out.indices) {
                var v = 0.0
                if (i < src.size) v += src[i]
                if (i - d1 in src.indices) v += src[i - d1] * 0.5
                if (i - d2 in src.indices) v += src[i - d2] * 0.28
                out[i] = v.toInt().coerceIn(-32768, 32767).toShort()
            }
            out to sr
        }
    }
}

suspend fun playPcm(data: ShortArray, rate: Int) {
    if (data.isEmpty()) return
    withContext(Dispatchers.IO) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(data, 0, data.size)
            track.play()
            delay(data.size * 1000L / rate + 200)
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}

/** پخش فایل صوتی (mp3/wav) از بایت‌ها */
suspend fun playAudioBytes(ctx: Context, bytes: ByteArray) {
    val f = File(ctx.cacheDir, "snd_${System.nanoTime()}.bin")
    withContext(Dispatchers.IO) { f.writeBytes(bytes) }
    try {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val mp = MediaPlayer()
                cont.invokeOnCancellation {
                    try { mp.stop() } catch (_: Exception) {}
                    mp.release()
                }
                try {
                    mp.setDataSource(f.absolutePath)
                    mp.setOnCompletionListener { mp.release(); if (cont.isActive) cont.resume(Unit) }
                    mp.setOnErrorListener { _, _, _ -> mp.release(); if (cont.isActive) cont.resume(Unit); true }
                    mp.prepare()
                    mp.start()
                } catch (e: Exception) {
                    mp.release()
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    } finally {
        f.delete()
    }
}

// ---------------------------------------------------------------- ساخت ملودی

fun synthMelody(notes: List<Pair<Int, Double>>, tempo: Int, sr: Int = 22050): ShortArray {
    val beat = 60.0 / tempo.coerceIn(50, 200)
    val total = notes.sumOf { (it.second * beat * sr).toInt() } + sr / 2
    val out = ShortArray(total)
    var pos = 0
    for ((midi, beats) in notes) {
        val len = (beats * beat * sr).toInt()
        if (midi > 0) {
            val f = 440.0 * 2.0.pow((midi - 69) / 12.0)
            val att = (0.012 * sr).toInt().coerceAtLeast(1)
            val rel = (0.09 * sr).toInt().coerceAtLeast(1)
            for (i in 0 until len) {
                if (pos + i >= out.size) break
                val t = i.toDouble() / sr
                val w = sin(2 * PI * f * t) + 0.35 * sin(4 * PI * f * t) + 0.12 * sin(6 * PI * f * t)
                val env = when {
                    i < att -> i.toDouble() / att
                    i > len - rel -> max(0.0, (len - i).toDouble() / rel)
                    else -> 0.85 + 0.15 * (1 - i.toDouble() / len)
                }
                out[pos + i] = (w * env * 7000).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        pos += len
    }
    return out
}

// ---------------------------------------------------------------- صداهای آماده‌ی هوش مصنوعی

data class BotVoice(val id: String, val label: String)

val BotVoices = listOf(
    BotVoice("nova", "نوا · زن شاد"),
    BotVoice("shimmer", "شیمر · زن آرام"),
    BotVoice("alloy", "آلوی · خنثی"),
    BotVoice("echo", "اکو · مرد"),
    BotVoice("fable", "فیبل · قصه‌گو"),
    BotVoice("onyx", "اونیکس · مرد بم"),
)

/** شکستن متن بلند به تکه‌های کوتاه (روی پایان جمله) برای تبدیل به صدا */
fun splitSpeech(text: String, max: Int = 420): List<String> {
    val out = ArrayList<String>()
    var cur = StringBuilder()
    val parts = text.split(Regex("(?<=[.!?؟۔،\\n])\\s*"))
    for (p in parts) {
        if (p.isBlank()) continue
        if (cur.length + p.length > max && cur.isNotEmpty()) {
            out.add(cur.toString().trim()); cur = StringBuilder()
        }
        var piece = p
        while (piece.length > max) {
            out.add(piece.take(max)); piece = piece.drop(max)
        }
        cur.append(piece).append(' ')
    }
    if (cur.isNotBlank()) out.add(cur.toString().trim())
    return out
}
