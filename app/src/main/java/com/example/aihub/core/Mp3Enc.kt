package com.example.aihub.core

import net.sourceforge.lame.mp3.Lame
import net.sourceforge.lame.mp3.MPEGMode
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** رمزگذار MP3 (پورت جاوای LAME؛ پروژه‌ی java-lame / jump3r با مجوز LGPL) */
object Mp3Enc {
    fun encode(p: MediaTrim.Pcm, out: File, kbps: Int, onProgress: (Int) -> Unit): Boolean {
        var lame: Lame? = null
        try {
            val l = Lame()
            lame = l
            val f = l.getFlags()
            f.setInNumChannels(p.ch)
            f.setInSampleRate(p.sr)
            f.setMode(if (p.ch == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO)
            f.setBitRate(if (p.sr < 32000) min(kbps, 160) else kbps)
            f.setQuality(Lame.QUALITY_MIDDLE)
            l.getId3().init(f)
            f.setWriteId3tagAutomatic(false)
            f.setFindReplayGain(true)
            if (l.initParams() < 0) return false
            val chunk = 8192
            val total = p.data.size / p.ch
            val bl = FloatArray(chunk)
            val br = FloatArray(chunk)
            val mp3 = ByteArray(chunk * 5 / 4 + 7200)
            out.outputStream().buffered().use { os ->
                var pos = 0
                while (pos < total) {
                    val n = min(chunk, total - pos)
                    for (i in 0 until n) {
                        val left = (p.data[(pos + i) * p.ch].toInt() shl 16).toFloat()
                        bl[i] = left
                        br[i] = if (p.ch == 2) (p.data[(pos + i) * 2 + 1].toInt() shl 16).toFloat() else left
                    }
                    val r = l.encodeBuffer(bl, br, n, mp3)
                    if (r < 0) return false
                    if (r > 0) os.write(mp3, 0, r)
                    pos += n
                    onProgress(50 + (pos.toLong() * 50 / max(total, 1)).toInt())
                }
                val r = l.encodeFlush(mp3)
                if (r > 0) os.write(mp3, 0, r)
            }
            return true
        } catch (e: Throwable) {
            return false
        } finally {
            try { lame?.close() } catch (_: Throwable) {}
        }
    }
}
