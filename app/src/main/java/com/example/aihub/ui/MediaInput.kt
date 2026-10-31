package com.example.aihub.ui

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.aihub.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * نوار ورودی یکپارچه: دوربین، گالری، فایل متنی و میکروفون.
 * هر قابلیتی که callback اش null باشد نمایش داده نمی‌شود.
 *  - onImage: تصویر (دوربین/گالری) را مستقیم می‌دهد.
 *  - onOcrText: تصویر را با هوش مصنوعی می‌خواند (ocrPrompt) و متن را می‌دهد.
 *  - onText: محتوای یک فایل متنی (txt, md, csv, json, srt …).
 *  - onSpeech: متن گفتار میکروفون.
 */
@Composable
fun MediaBar(
    modifier: Modifier = Modifier,
    langTag: String = "fa-IR",
    ocrPrompt: String = "Extract ALL the text visible in this image exactly as written, keeping line breaks. Output only the text.",
    onImage: ((Bitmap) -> Unit)? = null,
    onOcrText: ((String) -> Unit)? = null,
    onText: ((String) -> Unit)? = null,
    onSpeech: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stt = remember { SpeechInput(ctx) }
    DisposableEffect(Unit) { onDispose { stt.release() } }
    var busy by remember { mutableStateOf(false) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val wantsImage = onImage != null || onOcrText != null

    fun handleBitmap(b: Bitmap?) {
        if (b == null) { toast(ctx, "تصویر خوانده نشد"); return }
        if (onImage != null) { onImage(b); return }
        if (onOcrText != null) {
            busy = true
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.Default) { b.toJpegBytes(85) }
                    val t = Ai.generate(ocrPrompt, image = bytes, temperature = 0.1, maxTokens = 2500).trim()
                    if (t.isBlank()) toast(ctx, "چیزی در تصویر پیدا نشد") else onOcrText(t)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(ctx, e.message ?: "خواندن تصویر ناموفق بود")
                } finally {
                    busy = false
                }
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, uri, 1600) }
            handleBitmap(b)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch {
            val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, u, 1600) }
            handleBitmap(b)
        }
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val t = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (e: Exception) { null }
            }
            if (t.isNullOrBlank()) toast(ctx, "فایل خوانده نشد (فقط فایل متنی)") else onText?.invoke(t.take(60000))
        }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) stt.start(langTag, { onSpeech?.invoke(it) }, { toast(ctx, it) }) else toast(ctx, "اجازه‌ی میکروفون لازم است")
    }

    fun openCamera() {
        try {
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            camUri = u
            camera.launch(u)
        } catch (e: Exception) {
            toast(ctx, "باز کردن دوربین ممکن نشد")
        }
    }

    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wantsImage) {
            MediaChip(Icons.Rounded.PhotoCamera, "دوربین", !busy) { openCamera() }
            MediaChip(Icons.Rounded.PhotoLibrary, "گالری", !busy) {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        if (onText != null) {
            MediaChip(Icons.Rounded.AttachFile, "فایل", !busy) {
                filePick.launch(arrayOf("text/*", "application/json", "application/xml", "application/x-subrip"))
            }
        }
        if (onSpeech != null) {
            MediaChip(if (stt.listening) Icons.Rounded.Stop else Icons.Rounded.Mic, if (stt.listening) "پایان" else "میکروفون", true) {
                if (stt.listening) stt.finish()
                else if (hasMic(ctx)) stt.start(langTag, { onSpeech(it) }, { toast(ctx, it) })
                else micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("در حال خواندن تصویر…", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MediaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}
