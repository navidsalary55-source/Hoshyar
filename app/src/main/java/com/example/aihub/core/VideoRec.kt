package com.example.aihub.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** ضبط یک ناحیه از صفحه‌ی خود برنامه به‌صورت MP4 (بدون نیاز به مجوز ضبط صفحه) */
object VideoRec {
    private fun activityOf(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }

    private suspend fun grab(act: Activity, r: Rect, bmp: Bitmap): Boolean = suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(act.window, r, bmp, { res -> if (cont.isActive) cont.resume(res == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }

    /** یک عکس از ناحیه‌ی داده‌شده */
    suspend fun snapshot(ctx: Context, r: Rect): Bitmap? {
        val act = activityOf(ctx) ?: return null
        if (r.width() < 4 || r.height() < 4) return null
        val b = Bitmap.createBitmap(r.width(), r.height(), Bitmap.Config.ARGB_8888)
        return if (grab(act, r, b)) b else null
    }

    /** ضبط [ms] میلی‌ثانیه؛ rectNow هر بار موقعیت فعلی ناحیه را می‌دهد. خروجی: فایل MP4 موقت یا null */
    suspend fun record(ctx: Context, rectNow: () -> Rect, ms: Long, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.Default) {
        val act = activityOf(ctx) ?: return@withContext null
        val first = rectNow()
        val srcW = first.width(); val srcH = first.height()
        if (srcW < 16 || srcH < 16) return@withContext null
        val w = (minOf(srcW, 720) / 2) * 2
        val h = ((srcH.toLong() * w / srcW).toInt() / 2) * 2
        val out = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        var track = -1
        try {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
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
                        i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(c.outputFormat); muxer!!.start(); started = true
                        }
                        i >= 0 -> {
                            val buf = c.getOutputBuffer(i)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                            if (info.size > 0 && started && buf != null) {
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, buf, info)
                            }
                            c.releaseOutputBuffer(i, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                        }
                    }
                }
            }

            val bmp = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val dst = Rect(0, 0, w, h)
            val t0 = System.currentTimeMillis()
            var frames = 0
            while (true) {
                val el = System.currentTimeMillis() - t0
                if (el >= ms) break
                val r0 = rectNow()
                val r = Rect(r0.left, r0.top, r0.left + srcW, r0.top + srcH)
                if (grab(act, r, bmp)) {
                    val cv = surface.lockHardwareCanvas()
                    try { cv.drawBitmap(bmp, null, dst, paint) } finally { surface.unlockCanvasAndPost(cv) }
                    frames++
                    drain(false)
                }
                onProgress((el * 100 / ms).toInt().coerceIn(0, 99))
                delay(8)
            }
            codec.signalEndOfInputStream()
            drain(true)
            if (frames == 0 || !started) { out.delete(); return@withContext null }
            out
        } catch (e: Exception) {
            out.delete(); null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
