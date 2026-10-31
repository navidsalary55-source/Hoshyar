package com.example.aihub.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.aihub.core.*
import com.example.aihub.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

private enum class OutKind(val label: String, val ext: String, val mime: String) {
    VIDEO("ویدیو (MP4)", "mp4", "video/mp4"), M4A("فقط صدا (M4A)", "m4a", "audio/mp4"), MP3("فقط صدا (MP3)", "mp3", "audio/mpeg"), WAV("فقط صدا (WAV)", "wav", "audio/wav")
}
private enum class VSize(val label: String, val side: Int) {
    ORIG("اصلی (سریع و بدون افت)", 0), P720("۷۲۰", 720), P480("۴۸۰", 480), P360("۳۶۰", 360)
}

private fun fmtT(ms: Long): String {
    val t = max(0L, ms)
    val m = t / 60000; val s = (t / 1000) % 60; val d = (t / 100) % 10
    return "%d:%02d.%d".format(m, s, d)
}

private fun fmtSize(b: Long): String = if (b >= 1_048_576) "%.1f مگابایت".format(b / 1_048_576.0) else "%d کیلوبایت".format(max(1L, b / 1024))

internal fun saveFileToDownloads(ctx: Context, f: File, name: String, mime: String): Boolean = try {
    val v = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AIHub")
    }
    val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
    ctx.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
    true
} catch (e: Exception) { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var uri by remember { mutableStateOf<Uri?>(null) }
    var inf by remember { mutableStateOf<MediaTrim.Info?>(null) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var kind by remember { mutableStateOf(OutKind.VIDEO) }
    var vsize by remember { mutableStateOf(VSize.ORIG) }
    var mute by remember { mutableStateOf(false) }
    var gain by remember { mutableStateOf(1f) }
    var fadeIn by remember { mutableStateOf(0f) }
    var fadeOut by remember { mutableStateOf(0f) }
    var kbps by remember { mutableStateOf(192) }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<File?>(null) }
    var resultKind by remember { mutableStateOf(OutKind.VIDEO) }
    var playing by remember { mutableStateOf(false) }
    var vv by remember { mutableStateOf<VideoView?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            scope.launch {
                val i = withContext(Dispatchers.IO) { MediaTrim.info(ctx, u) }
                if (i == null) { toast(ctx, "این فایل خوانده نشد؛ فایل صوتی یا ویدیویی دیگری انتخاب کن"); return@launch }
                playJob?.cancel(); playing = false; vv?.pause()
                uri = u; inf = i; startMs = 0; endMs = i.durMs; result = null
                kind = if (i.hasVideo) OutKind.VIDEO else OutKind.M4A
                vsize = VSize.ORIG; mute = false; gain = 1f; fadeIn = 0f; fadeOut = 0f
            }
        }
    }

    fun setRange(s: Long, e: Long) {
        val d = inf?.durMs ?: return
        var a = s.coerceIn(0, d); var b = e.coerceIn(0, d)
        if (b - a < 300) { if (a + 300 <= d) b = a + 300 else a = max(0, b - 300) }
        startMs = a; endMs = b; result = null
    }

    fun togglePlay() {
        val v = vv ?: return
        if (playing) { playJob?.cancel(); v.pause(); playing = false; return }
        v.seekTo(startMs.toInt()); v.start(); playing = true
        playJob = scope.launch {
            while (v.isPlaying && v.currentPosition < endMs) delay(50)
            v.pause(); playing = false
        }
    }

    DisposableEffect(Unit) { onDispose { playJob?.cancel(); vv?.stopPlayback() } }

    fun process() {
        val u = uri ?: return; val i = inf ?: return
        if (busy) return
        val sUs = startMs * 1000L; val eUs = endMs * 1000L
        val secs = (endMs - startMs) / 1000
        val fast = kind == OutKind.VIDEO && vsize == VSize.ORIG
        val copyAudio = kind == OutKind.M4A && i.aac && gain == 1f && fadeIn == 0f && fadeOut == 0f
        if (kind == OutKind.VIDEO && vsize != VSize.ORIG && secs > MediaTrim.MAX_RESIZE_SEC) { toast(ctx, "برای تغییر اندازه‌ی ویدیو بازه را حداکثر ${MediaTrim.MAX_RESIZE_SEC} ثانیه کن"); return }
        if (kind != OutKind.VIDEO && !copyAudio && secs > MediaTrim.MAX_AUDIO_SEC) { toast(ctx, "برای این تبدیل بازه را حداکثر ${MediaTrim.MAX_AUDIO_SEC / 60} دقیقه کن"); return }
        playJob?.cancel(); vv?.pause(); playing = false
        busy = true; prog = 0; result = null
        scope.launch {
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith("trim_") }?.forEach { it.delete() }
            val out = File(dir, "trim_${System.currentTimeMillis()}.${kind.ext}")
            val ok = withContext(Dispatchers.Default) {
                val tmp = ArrayList<File>()
                try {
                    when (kind) {
                        OutKind.VIDEO -> {
                            if (fast) MediaTrim.trimCopy(ctx, u, sUs, eUs, true, !mute && i.hasAudio, i.rot, out) { prog = it }
                            else {
                                val v = File(ctx.cacheDir, "trim_v_${System.currentTimeMillis()}.mp4").also { tmp.add(it) }
                                if (!MediaTrim.reencodeVideo(ctx, u, sUs, eUs, vsize.side, i, v) { prog = it * 8 / 10 }) false
                                else {
                                    var aUri: Uri? = null
                                    if (!mute && i.hasAudio) {
                                        val a = File(ctx.cacheDir, "trim_a_${System.currentTimeMillis()}.m4a").also { tmp.add(it) }
                                        prog = 85
                                        val got = if (i.aac) MediaTrim.trimCopy(ctx, u, sUs, eUs, false, true, 0, a) {}
                                        else MediaTrim.decodeRange(ctx, u, sUs, eUs) {}?.let { p -> MediaTrim.encodeAac(p, a) {} } ?: false
                                        if (got) aUri = Uri.fromFile(a)
                                    }
                                    prog = 92
                                    StoryVideo.mux(ctx, v, aUri, eUs - sUs, out)
                                }
                            }
                        }
                        OutKind.M4A -> {
                            if (copyAudio) MediaTrim.trimCopy(ctx, u, sUs, eUs, false, true, 0, out) { prog = it }
                            else {
                                val p = MediaTrim.decodeRange(ctx, u, sUs, eUs) { prog = it }
                                if (p == null) false else { MediaTrim.applyFx(p, gain, fadeIn, fadeOut); MediaTrim.encodeAac(p, out) { prog = it } }
                            }
                        }
                        OutKind.MP3 -> {
                            val p = MediaTrim.decodeRange(ctx, u, sUs, eUs) { prog = it }
                            if (p == null) false else { MediaTrim.applyFx(p, gain, fadeIn, fadeOut); Mp3Enc.encode(p, out, kbps) { prog = it } }
                        }
                        OutKind.WAV -> {
                            val p = MediaTrim.decodeRange(ctx, u, sUs, eUs) { prog = it }
                            if (p == null) false else { MediaTrim.applyFx(p, gain, fadeIn, fadeOut); MediaTrim.writeWav(p, out); true }
                        }
                    }
                } catch (e: Throwable) { false } finally { tmp.forEach { it.delete() } }
            }
            busy = false
            if (ok && out.exists() && out.length() > 0) { result = out; resultKind = kind }
            else { out.delete(); toast(ctx, "ساخت فایل ناموفق بود. فرمت یا بازه‌ی دیگری امتحان کن") }
        }
    }

    ToolScaffold("برش و تبدیل", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // انتخاب فایل
            GradButton(
                if (inf == null) "انتخاب موزیک یا ویدیو" else "انتخاب فایل دیگر", icon = Icons.Rounded.FolderOpen,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                onClick = { pick.launch(arrayOf("video/*", "audio/*")) },
            )
            val i = inf
            if (i == null) {
                Text(
                    "یک فایل صوتی یا ویدیویی از گوشی انتخاب کن. بعد بخشی را که می‌خواهی ببر، خروجی را انتخاب کن و ذخیره کن.",
                    Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f),
                )
            } else {
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface.copy(alpha = 0.6f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(i.name, fontSize = 14.sp, maxLines = 1)
                    Text(
                        (if (i.hasVideo) "ویدیو ${if (i.rot == 90 || i.rot == 270) i.h else i.w}×${if (i.rot == 90 || i.rot == 270) i.w else i.h}" else "فایل صوتی") + "  •  مدت ${fmtT(i.durMs)}",
                        fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.7f),
                    )
                }

                // پیش‌نمایش
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(if (i.hasVideo) 220.dp else 90.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { VideoView(it).also { v -> vv = v } },
                        update = { v -> if (v.tag != uri) { v.tag = uri; v.setVideoURI(uri); v.seekTo(1) } },
                        modifier = Modifier.fillMaxSize().then(if (i.hasVideo) Modifier else Modifier.size(1.dp)),
                    )
                    if (!i.hasVideo) Text("🎧  پیش‌نمایش صدا", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
                }
                OutlinedButton({ togglePlay() }, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (playing) "توقف" else "پخش بخش انتخاب‌شده")
                }

                // بازه
                SectionTitle("بازه‌ی برش")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    RangeSlider(
                        value = startMs.toFloat()..endMs.toFloat(),
                        onValueChange = { r -> setRange(r.start.toLong(), r.endInclusive.toLong()) },
                        valueRange = 0f..i.durMs.toFloat(),
                    )
                    Text("از ${fmtT(startMs)} تا ${fmtT(endMs)}  •  مدت خروجی ${fmtT(endMs - startMs)}", fontSize = 13.sp)
                }
                TimeAdjust("شروع", startMs) { setRange(it, endMs) }
                TimeAdjust("پایان", endMs) { setRange(startMs, it) }
                ChipRow(listOf(15, 30, 60, 0), -1, { if (it == 0) "همه" else "$it ثانیه" }) { s ->
                    if (s == 0) setRange(0, i.durMs) else setRange(startMs, min(i.durMs, startMs + s * 1000L))
                }

                // خروجی
                SectionTitle("خروجی")
                val kinds = if (i.hasVideo) OutKind.values().toList() else listOf(OutKind.M4A, OutKind.MP3, OutKind.WAV)
                ChipRow(kinds, kind, { it.label }) { kind = it; result = null }
                if (kind == OutKind.VIDEO) {
                    Text("اندازه‌ی ویدیو (ضلع کوتاه، پیکسل)", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                    ChipRow(VSize.values().toList(), vsize, { it.label }) { vsize = it; result = null }
                    if (vsize != VSize.ORIG) Text("تغییر اندازه کندتر است و فقط تا ${MediaTrim.MAX_RESIZE_SEC} ثانیه.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f))
                    if (vsize == VSize.ORIG) Text("برش سریع از نزدیک‌ترین کلیدفریم شروع می‌شود؛ ممکن است تا حدود ۱ ثانیه جابه‌جایی داشته باشد.", Modifier.padding(horizontal = 20.dp), fontSize = 11.5.sp, color = c.onBackground.copy(alpha = 0.65f))
                    if (i.hasAudio) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(mute, { mute = it; result = null }); Spacer(Modifier.width(10.dp)); Text("بدون صدا", fontSize = 13.sp)
                    }
                } else {
                    if (kind == OutKind.MP3) {
                        Text("کیفیت MP3 (کیلوبیت بر ثانیه)", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                        ChipRow(listOf(128, 192, 256, 320), kbps, { it.toString() }) { kbps = it; result = null }
                    }
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("بلندی صدا: ${(gain * 100).toInt()}٪", fontSize = 13.sp)
                        Slider(gain, { gain = it; result = null }, valueRange = 0.3f..2f)
                        Text("محو شدن ابتدا: ${"%.1f".format(fadeIn)} ثانیه", fontSize = 13.sp)
                        Slider(fadeIn, { fadeIn = it; result = null }, valueRange = 0f..5f)
                        Text("محو شدن انتها: ${"%.1f".format(fadeOut)} ثانیه", fontSize = 13.sp)
                        Slider(fadeOut, { fadeOut = it; result = null }, valueRange = 0f..5f)
                    }
                }

                GradButton(
                    if (busy) "در حال ساخت… ${prog}٪" else "برش و ساخت فایل", icon = Icons.Rounded.ContentCut,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = !busy, onClick = { process() },
                )
                if (busy) LinearProgressIndicator(progress = { prog / 100f }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())

                val r = result
                if (r != null) {
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.primary.copy(alpha = 0.12f)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("✅ فایل آماده شد  •  ${resultKind.label}  •  ${fmtSize(r.length())}", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradButton(
                                "ذخیره", icon = Icons.Rounded.Download, modifier = Modifier.weight(1f),
                                onClick = {
                                    if (canExport()) scope.launch {
                                        val base = i.name.substringBeforeLast('.').take(30).ifBlank { "AIHub" }
                                        val ok = withContext(Dispatchers.IO) { saveFileToGallery(ctx, r, "${base}_cut_${System.currentTimeMillis() % 100000}.${resultKind.ext}", resultKind.mime) }
                                        toast(ctx, if (ok) (if (resultKind == OutKind.VIDEO) "در گالری ذخیره شد (Movies/AIHub)" else "در پوشه‌ی Music/AIHub ذخیره شد") else "ذخیره نشد")
                                    }
                                },
                            )
                            OutlinedButton(
                                onClick = {
                                    if (canExport()) try {
                                        val fu = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", r)
                                        val it = Intent(Intent.ACTION_SEND).apply { type = resultKind.mime; putExtra(Intent.EXTRA_STREAM, fu); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                        ctx.startActivity(Intent.createChooser(it, "اشتراک‌گذاری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) { toast(ctx, "اشتراک‌گذاری ممکن نشد") }
                                },
                                modifier = Modifier.weight(0.8f).heightIn(min = 52.dp),
                            ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک", maxLines = 1) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TimeAdjust(label: String, ms: Long, onChange: (Long) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.width(44.dp), fontSize = 13.sp)
        listOf(-1000L to "−۱", -100L to "−۰٫۱").forEach { (d, t) ->
            OutlinedButton({ onChange(ms + d) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f)) { Text(t, fontSize = 12.sp, maxLines = 1) }
        }
        Text(fmtT(ms), Modifier.weight(1.3f), fontSize = 13.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        listOf(100L to "+۰٫۱", 1000L to "+۱").forEach { (d, t) ->
            OutlinedButton({ onChange(ms + d) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.weight(1f)) { Text(t, fontSize = 12.sp, maxLines = 1) }
        }
    }
}
