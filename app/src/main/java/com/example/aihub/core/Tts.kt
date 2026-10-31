package com.example.aihub.core

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/**
 * خواندن متن با بهترین صدای در دسترس، برای همه‌ی بخش‌های برنامه:
 * اول صدای آنلاین (ElevenLabs در صورت وجود کلید، بعد سرویس‌های رایگان)، در صورت خطا صدای خود گوشی.
 */
object Tts {
    suspend fun say(
        ctx: Context, speaker: Speaker, text: String, lang: Lang,
        rate: Float = 1f, pitch: Float = 1f, onNote: (String) -> Unit = {},
    ) {
        val clean = text.replace(Regex("\\[.*?]"), " ").trim()
        if (clean.isBlank()) return
        val parts = splitSpeech(clean, 220)
        val tag = lang.tag.substringBefore('-')
        var idx = 0
        if (Store.voiceOnline) {
            try {
                supervisorScope {
                    var pending = async { Poll.speech(parts[0], Store.botVoice, tag) }
                    while (idx < parts.size) {
                        val b = pending.await()
                        if (idx + 1 < parts.size) {
                            val n = idx + 1
                            pending = async { Poll.speech(parts[n], Store.botVoice, tag) }
                        }
                        playAudioBytes(ctx, b)
                        idx++
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ادامه با صدای گوشی
            }
        }
        if (!speaker.hasVoice(lang.locale)) {
            val why = Poll.lastError.ifBlank { "صدای آنلاین خاموش است" }
            onNote("صدا پخش نشد ($why). صدای ${lang.label} روی گوشی هم نصب نیست؛ eSpeak NG را فعال کن.")
            return
        }
        if (Store.voiceOnline && idx == 0) onNote("صدای آنلاین جواب نداد؛ با صدای گوشی خوانده می‌شود.")
        for (p in parts.drop(idx)) speaker.speak(p, rate, pitch, lang.locale)
    }
}
