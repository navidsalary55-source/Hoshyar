package com.example.aihub.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/** ساخت ویدیوی MP4 از فریم‌های رندرشده‌ی استوری + (اختیاری) موزیک */
object StoryVideo {
    /** اگر بزرگ‌تر از صفر باشد به‌جای مقدار پیش‌فرض برای نرخ بیت ویدیو استفاده می‌شود */
    @Volatile var bitrate = 0

    /** فریم‌ها را کد می‌کند. frame(i) باید بیت‌مپ فریم i را بدهد. خروجی: فایل MP4 بدون صدا */
    fun encodeVideo(out: File, w: Int, h: Int, fps: Int, frames: Int, onProgress: (Int) -> Unit, frame: (Int) -> Bitmap): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        var track = -1
        var outCount = 0L
        try {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, if (bitrate > 0) bitrate else if (w >= 1000) 8_000_000 else 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()

            fun drain(eos: Boolean) {
                val c = codec!!
                while (true) {
                    val i = c.dequeueOutputBuffer(info, if (eos) 10_000 else 0)
                    when {
                        i == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!eos) return }
                        i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer!!.addTrack(c.outputFormat); muxer!!.start(); started = true }
                        i >= 0 -> {
                            val buf = c.getOutputBuffer(i)
                            val cfg = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (info.size > 0 && started && buf != null && !cfg) {
                                info.presentationTimeUs = outCount * 1_000_000L / fps
                                outCount++
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, buf, info)
                            }
                            c.releaseOutputBuffer(i, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                        }
                    }
                }
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val dst = Rect(0, 0, w, h)
            for (i in 0 until frames) {
                val b = frame(i)
                val cv = surface.lockHardwareCanvas()
                try { cv.drawBitmap(b, null, dst, paint) } finally { surface.unlockCanvasAndPost(cv) }
                b.recycle()
                drain(false)
                onProgress(i * 85 / frames)
            }
            codec.signalEndOfInputStream()
            drain(true)
            return started && outCount > 0
        } catch (e: Exception) {
            return false
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun audioTrack(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            if ((ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) return i
        }
        return -1
    }

    /** هر فایل صوتی (mp3, wav, ogg, …) را تا maxUs به AAC داخل m4a تبدیل می‌کند */
    private fun transcodeToAac(ctx: Context, uri: Uri, maxUs: Long, out: File): Boolean {
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        var enc: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var mStarted = false
        var mTrack = -1
        try {
            ex.setDataSource(ctx, uri, null)
            val ti = audioTrack(ex)
            if (ti < 0) return false
            ex.selectTrack(ti)
            val inFmt = ex.getTrackFormat(ti)
            dec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
            dec.configure(inFmt, null, null, 0)
            dec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val di = MediaCodec.BufferInfo()
            val ei = MediaCodec.BufferInfo()
            var sr = 44100; var ch = 2
            var totalBytes = 0L
            var inputDone = false
            var decDone = false
            var encDone = false

            fun ensureEnc() {
                if (enc != null) return
                val f = dec!!.outputFormat
                sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                val ef = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
                }
                enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                enc!!.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc!!.start()
            }

            fun drainEnc() {
                val e = enc ?: return
                while (true) {
                    val i = e.dequeueOutputBuffer(ei, 0)
                    when {
                        i == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                        i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { mTrack = muxer!!.addTrack(e.outputFormat); muxer!!.start(); mStarted = true }
                        i >= 0 -> {
                            val buf = e.getOutputBuffer(i)
                            val cfg = (ei.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (ei.size > 0 && mStarted && buf != null && !cfg) {
                                buf.position(ei.offset); buf.limit(ei.offset + ei.size)
                                muxer!!.writeSampleData(mTrack, buf, ei)
                            }
                            e.releaseOutputBuffer(i, false)
                            if ((ei.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { encDone = true; return }
                        }
                    }
                }
            }

            var guard = 0
            while (!encDone && guard < 2_000_000) {
                guard++
                if (!inputDone) {
                    val ii = dec.dequeueInputBuffer(0)
                    if (ii >= 0) {
                        val b = dec.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(b, 0)
                        val t = ex.sampleTime
                        if (sz < 0 || t > maxUs) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { dec.queueInputBuffer(ii, 0, sz, t, 0); ex.advance() }
                    }
                }
                if (!decDone) {
                    val oi = dec.dequeueOutputBuffer(di, 5000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { /* بعداً از outputFormat خوانده می‌شود */ }
                    else if (oi >= 0) {
                        ensureEnc()
                        val src = dec.getOutputBuffer(oi)
                        var remaining = di.size
                        var pos = di.offset
                        while (remaining > 0 && src != null) {
                            val e = enc!!
                            val xi = e.dequeueInputBuffer(10_000)
                            if (xi < 0) { drainEnc(); continue }
                            val eb = e.getInputBuffer(xi)!!
                            eb.clear()
                            val n = minOf(eb.remaining(), remaining)
                            val dup = src.duplicate(); dup.position(pos); dup.limit(pos + n)
                            eb.put(dup)
                            val pts = totalBytes * 1_000_000L / (sr.toLong() * ch * 2L)
                            e.queueInputBuffer(xi, 0, n, pts, 0)
                            totalBytes += n; pos += n; remaining -= n
                            drainEnc()
                        }
                        val last = (di.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        dec.releaseOutputBuffer(oi, false)
                        if (last) {
                            decDone = true
                            var xi = -1
                            while (xi < 0) { xi = enc!!.dequeueInputBuffer(10_000); if (xi < 0) drainEnc() }
                            enc!!.queueInputBuffer(xi, 0, 0, totalBytes * 1_000_000L / (sr.toLong() * ch * 2L), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }
                drainEnc()
            }
            return mStarted && encDone
        } catch (e: Exception) {
            return false
        } finally {
            try { ex.release() } catch (_: Exception) {}
            try { dec?.stop() } catch (_: Exception) {}
            try { dec?.release() } catch (_: Exception) {}
            try { enc?.stop() } catch (_: Exception) {}
            try { enc?.release() } catch (_: Exception) {}
            try { if (mStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /** هر فایل صوتی را تا maxUs به PCM تک‌کاناله‌ی ۱۶ کیلوهرتز (برای تشخیص گفتار) تبدیل می‌کند */
    fun decodePcm16k(ctx: Context, uri: Uri, maxUs: Long, startUs: Long = 0L): ShortArray? {
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            val ti = audioTrack(ex)
            if (ti < 0) return null
            ex.selectTrack(ti)
            if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val f = ex.getTrackFormat(ti)
            dec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            dec.configure(f, null, null, 0)
            dec.start()
            val info = MediaCodec.BufferInfo()
            var sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var mono = ShortArray(1 shl 20)
            var mn = 0
            var inDone = false; var outDone = false; var guard = 0
            while (!outDone && guard++ < 3_000_000) {
                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(0)
                    if (ii >= 0) {
                        val b = dec.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(b, 0)
                        val tm = ex.sampleTime
                        if (sz < 0 || tm > maxUs) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(ii, 0, sz, tm, 0); ex.advance() }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 5000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (oi >= 0) {
                    val b = dec.getOutputBuffer(oi)
                    if (b != null && info.size > 0) {
                        b.position(info.offset); b.limit(info.offset + info.size)
                        val sb = b.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val n = sb.remaining() / ch
                        for (k in 0 until n) {
                            var s = 0
                            for (c in 0 until ch) s += sb.get(k * ch + c).toInt()
                            if (mn == mono.size) mono = mono.copyOf(mono.size * 2)
                            mono[mn++] = (s / ch).toShort()
                        }
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                }
            }
            if (mn == 0) return null
            val ratio = sr / 16000.0
            val outN = (mn / ratio).toInt()
            val out = ShortArray(outN)
            for (i in 0 until outN) {
                val pos = i * ratio
                val i0 = pos.toInt().coerceAtMost(mn - 1)
                val i1 = (i0 + 1).coerceAtMost(mn - 1)
                val fr = pos - i0
                out[i] = (mono[i0] * (1 - fr) + mono[i1] * fr).toInt().toShort()
            }
            return out
        } catch (e: Exception) {
            return null
        } finally {
            try { ex.release() } catch (_: Exception) {}
            try { dec?.stop() } catch (_: Exception) {}
            try { dec?.release() } catch (_: Exception) {}
        }
    }

    /** ترکیب ویدیو + صدا. audio یا null یا Uri فایل صوتی. اگر صدا خراب باشد، ویدیوی بی‌صدا برمی‌گردد. */
    fun mux(ctx: Context, video: File, audio: Uri?, durUs: Long, out: File): Boolean {
        var aFile: File? = null
        var aUri: Uri? = null
        if (audio != null) {
            // اگر AAC باشد مستقیم کپی می‌شود، وگرنه تبدیل
            val probe = MediaExtractor()
            var aac = false
            try {
                probe.setDataSource(ctx, audio, null)
                val i = audioTrack(probe)
                aac = i >= 0 && probe.getTrackFormat(i).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC
            } catch (_: Exception) {} finally { try { probe.release() } catch (_: Exception) {} }
            if (aac) aUri = audio
            else {
                val f = File(ctx.cacheDir, "story_audio_${System.currentTimeMillis()}.m4a")
                if (transcodeToAac(ctx, audio, durUs, f)) { aFile = f; aUri = Uri.fromFile(f) } else f.delete()
            }
        }
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            vEx.setDataSource(video.absolutePath)
            var vt = -1
            for (i in 0 until vEx.trackCount) if ((vEx.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) vt = i
            if (vt < 0) return false
            vEx.selectTrack(vt)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vOut = muxer.addTrack(vEx.getTrackFormat(vt))
            var aOut = -1
            var at = -1
            if (aUri != null) {
                try {
                    aEx.setDataSource(ctx, aUri, null)
                    at = audioTrack(aEx)
                    if (at >= 0) { aEx.selectTrack(at); aOut = muxer.addTrack(aEx.getTrackFormat(at)) }
                } catch (_: Exception) { at = -1; aOut = -1 }
            }
            muxer.start(); started = true
            val buf = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            fun copy(ex: MediaExtractor, track: Int, limitUs: Long) {
                while (true) {
                    val sz = ex.readSampleData(buf, 0)
                    if (sz < 0) break
                    val t = ex.sampleTime
                    if (limitUs > 0 && t > limitUs) break
                    info.set(0, sz, t, if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer!!.writeSampleData(track, buf, info)
                    ex.advance()
                }
            }
            copy(vEx, vOut, 0)
            if (aOut >= 0) copy(aEx, aOut, durUs)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { vEx.release() } catch (_: Exception) {}
            try { aEx.release() } catch (_: Exception) {}
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            aFile?.delete()
        }
    }
}
