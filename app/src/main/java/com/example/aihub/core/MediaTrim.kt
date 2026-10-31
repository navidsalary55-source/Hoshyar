package com.example.aihub.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** برش و تبدیل فایل‌های صوتی و تصویری */
object MediaTrim {
    class Info(val name: String, val durMs: Long, val hasVideo: Boolean, val hasAudio: Boolean, val w: Int, val h: Int, val rot: Int, val aac: Boolean)
    class Pcm(val data: ShortArray, val sr: Int, val ch: Int)

    const val MAX_AUDIO_SEC = 480
    const val MAX_RESIZE_SEC = 60

    fun info(ctx: Context, uri: Uri): Info? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hv = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val ha = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (dur <= 0 || (!hv && !ha)) return null
            var name = "فایل انتخاب‌شده"
            try {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
            } catch (_: Exception) {}
            var aac = false
            val ex = MediaExtractor()
            try {
                ex.setDataSource(ctx, uri, null)
                for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC) aac = true
            } catch (_: Exception) {} finally { try { ex.release() } catch (_: Exception) {} }
            return Info(name, dur, hv, ha, w, h, rot, aac)
        } catch (e: Exception) {
            return null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun trackOf(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) if ((ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith(prefix)) return i
        return -1
    }

    /** برش سریع بدون افت کیفیت (کپی مستقیم). شروع روی نزدیک‌ترین کلیدفریم قبلی می‌افتد. فقط برای ویدیو و صدای AAC */
    fun trimCopy(ctx: Context, uri: Uri, startUs: Long, endUs: Long, video: Boolean, audio: Boolean, rot: Int, out: File, onProgress: (Int) -> Unit): Boolean {
        val vEx = MediaExtractor(); val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            var vt = -1; var at = -1
            if (video) { vEx.setDataSource(ctx, uri, null); vt = trackOf(vEx, "video/") }
            if (audio) {
                aEx.setDataSource(ctx, uri, null); at = trackOf(aEx, "audio/")
                if (at >= 0 && aEx.getTrackFormat(at).getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) return false
            }
            if (vt < 0 && at < 0) return false
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var vOut = -1; var aOut = -1
            var base = startUs
            if (vt >= 0) {
                vEx.selectTrack(vt)
                vEx.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                base = vEx.sampleTime.coerceAtLeast(0)
                vOut = muxer.addTrack(vEx.getTrackFormat(vt))
                if (rot != 0) muxer.setOrientationHint(rot)
            }
            if (at >= 0) {
                aEx.selectTrack(at)
                aEx.seekTo(base, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                aOut = muxer.addTrack(aEx.getTrackFormat(at))
            }
            muxer.start(); started = true
            val buf = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val span = (endUs - base).coerceAtLeast(1)
            fun copy(ex: MediaExtractor, track: Int, share: Int, offset: Int) {
                while (true) {
                    val sz = ex.readSampleData(buf, 0)
                    if (sz < 0) break
                    val t = ex.sampleTime
                    if (t > endUs) break
                    val pts = t - base
                    if (pts >= 0) {
                        info.set(0, sz, pts, if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        muxer!!.writeSampleData(track, buf, info)
                    }
                    onProgress(offset + (pts.coerceAtLeast(0) * share / span).toInt().coerceIn(0, share))
                    ex.advance()
                }
            }
            val half = if (vOut >= 0 && aOut >= 0) 50 else 100
            if (vOut >= 0) copy(vEx, vOut, half, 0)
            if (aOut >= 0) copy(aEx, aOut, half, if (vOut >= 0) 50 else 0)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { vEx.release() } catch (_: Exception) {}
            try { aEx.release() } catch (_: Exception) {}
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /** رمزگشایی بازه‌ی [startUs, endUs] از هر فایل صوتی یا ویدیویی به PCM (حداکثر MAX_AUDIO_SEC ثانیه) */
    fun decodeRange(ctx: Context, uri: Uri, startUs: Long, endUs: Long, onProgress: (Int) -> Unit): Pcm? {
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            val ti = trackOf(ex, "audio/")
            if (ti < 0) return null
            ex.selectTrack(ti)
            val f = ex.getTrackFormat(ti)
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            dec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            dec.configure(f, null, null, 0)
            dec.start()
            val info = MediaCodec.BufferInfo()
            val durUs = min(endUs - startUs, MAX_AUDIO_SEC * 1_000_000L)
            var sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            var data: ShortArray? = null
            var wrote = 0
            var cap = 0
            var inDone = false; var outDone = false; var guard = 0
            while (!outDone && guard++ < 4_000_000) {
                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(0)
                    if (ii >= 0) {
                        val b = dec.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(b, 0)
                        val tm = ex.sampleTime
                        if (sz < 0 || tm > endUs) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(ii, 0, sz, tm, 0); ex.advance() }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 5000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                } else if (oi >= 0) {
                    val b = dec.getOutputBuffer(oi)
                    if (b != null && info.size > 0) {
                        if (data == null) {
                            val of = dec.outputFormat
                            sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                            cap = (durUs * sr / 1_000_000L).toInt() * ch + ch
                            data = ShortArray(cap)
                        }
                        b.position(info.offset); b.limit(info.offset + info.size)
                        val sb = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val chIn = max(1, dec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        val frames = sb.remaining() / chIn
                        // چند فریم اول، قبل از نقطه‌ی شروع‌اند
                        val skip = if (info.presentationTimeUs < startUs) (((startUs - info.presentationTimeUs) * sr) / 1_000_000L).toInt() else 0
                        for (k in skip until frames) {
                            if (wrote + ch > cap) break
                            if (ch == 1) {
                                var s = 0
                                for (c in 0 until chIn) s += sb.get(k * chIn + c).toInt()
                                data!![wrote++] = (s / chIn).toShort()
                            } else {
                                data!![wrote++] = sb.get(k * chIn)
                                data[wrote++] = sb.get(k * chIn + (if (chIn > 1) 1 else 0))
                            }
                        }
                        onProgress((wrote.toLong() * 50 / max(cap, 1)).toInt().coerceIn(0, 50))
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || wrote >= cap && cap > 0) outDone = true
                }
            }
            val d = data ?: return null
            return Pcm(if (wrote == d.size) d else d.copyOf(wrote), sr, ch)
        } catch (e: Exception) {
            return null
        } finally {
            try { ex.release() } catch (_: Exception) {}
            try { dec?.stop() } catch (_: Exception) {}
            try { dec?.release() } catch (_: Exception) {}
        }
    }

    /** اعمال بلندی صدا و محو شدن ورود/خروج */
    fun applyFx(p: Pcm, gain: Float, fadeInSec: Float, fadeOutSec: Float) {
        val frames = p.data.size / p.ch
        val fin = (fadeInSec * p.sr).toInt(); val fout = (fadeOutSec * p.sr).toInt()
        for (f in 0 until frames) {
            var m = gain
            if (fin > 0 && f < fin) m *= f.toFloat() / fin
            if (fout > 0 && frames - f < fout) m *= (frames - f).toFloat() / fout
            if (m == 1f) continue
            for (c in 0 until p.ch) {
                val i = f * p.ch + c
                p.data[i] = (p.data[i] * m).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }

    fun writeWav(p: Pcm, out: File) {
        val dataLen = p.data.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + dataLen); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(p.ch.toShort())
        bb.putInt(p.sr); bb.putInt(p.sr * p.ch * 2); bb.putShort((p.ch * 2).toShort()); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(dataLen)
        bb.asShortBuffer().put(p.data)
        out.outputStream().use { it.write(bb.array()) }
    }

    /** PCM را به AAC داخل m4a کد می‌کند */
    fun encodeAac(p: Pcm, out: File, onProgress: (Int) -> Unit): Boolean {
        var enc: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            val ef = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, p.sr, p.ch).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
            }
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var pos = 0
            var inDone = false; var done = false; var guard = 0
            while (!done && guard++ < 5_000_000) {
                if (!inDone) {
                    val xi = enc.dequeueInputBuffer(5000)
                    if (xi >= 0) {
                        val eb = enc.getInputBuffer(xi)!!
                        eb.clear()
                        var n = min(eb.capacity() / 2, p.data.size - pos)
                        n -= n % p.ch
                        val pts = pos.toLong() / p.ch * 1_000_000L / p.sr
                        if (n <= 0) { enc.queueInputBuffer(xi, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else {
                            eb.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(p.data, pos, n)
                            enc.queueInputBuffer(xi, 0, n * 2, pts, 0)
                            pos += n
                            onProgress(50 + (pos.toLong() * 50 / max(p.data.size, 1)).toInt())
                        }
                    }
                }
                while (true) {
                    val oi = enc.dequeueOutputBuffer(info, 0)
                    if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(enc.outputFormat); muxer.start(); started = true }
                    else if (oi >= 0) {
                        val b = enc.getOutputBuffer(oi)
                        val cfg = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (info.size > 0 && started && b != null && !cfg) { b.position(info.offset); b.limit(info.offset + info.size); muxer.writeSampleData(track, b, info) }
                        enc.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { done = true; break }
                    }
                }
            }
            return started && done
        } catch (e: Exception) {
            return false
        } finally {
            try { enc?.stop() } catch (_: Exception) {}
            try { enc?.release() } catch (_: Exception) {}
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /** ویدیوی بازه را با اندازه‌ی کوچک‌تر دوباره می‌سازد (بدون صدا). shortSide = ضلع کوتاه خروجی */
    fun reencodeVideo(ctx: Context, uri: Uri, startUs: Long, endUs: Long, shortSide: Int, inf: Info, out: File, onProgress: (Int) -> Unit): Boolean {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val swap = inf.rot == 90 || inf.rot == 270
            val dw = if (swap) inf.h else inf.w
            val dh = if (swap) inf.w else inf.h
            if (dw <= 0 || dh <= 0) return false
            val sc = min(1f, shortSide.toFloat() / min(dw, dh))
            val w = ((dw * sc).toInt() / 2 * 2).coerceAtLeast(2)
            val h = ((dh * sc).toInt() / 2 * 2).coerceAtLeast(2)
            val fps = 24
            val frames = ((endUs - startUs) * fps / 1_000_000L).toInt().coerceAtLeast(2)
            StoryVideo.bitrate = (w.toLong() * h * 4).toInt().coerceIn(700_000, 6_000_000)
            try {
                var last: Bitmap? = null
                return StoryVideo.encodeVideo(out, w, h, fps, frames, onProgress) { i ->
                    val t = startUs + i * 1_000_000L / fps
                    val fr = r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
                    val b = fr ?: last?.copy(Bitmap.Config.ARGB_8888, false) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.BLACK) }
                    last = b.copy(Bitmap.Config.ARGB_8888, false)
                    b
                }
            } finally { StoryVideo.bitrate = 0 }
        } catch (e: Exception) {
            return false
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
