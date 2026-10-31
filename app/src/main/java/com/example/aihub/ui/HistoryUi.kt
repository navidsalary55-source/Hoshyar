package com.example.aihub.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aihub.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun screenOf(tool: String): Screen? = try { Screen.valueOf(tool) } catch (_: Exception) { null }

private fun fmtDate(t: Long): String = try {
    val f = android.icu.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", android.icu.util.ULocale("fa_IR@calendar=persian"))
    f.format(java.util.Date(t))
} catch (_: Exception) {
    java.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", java.util.Locale.US).format(java.util.Date(t))
}

/** فهرست تاریخچه؛ اگر only داده شود فقط همان بخش را نشان می‌دهد */
@Composable
fun HistoryList(only: String?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    var filter by remember(only) { mutableStateOf(only) }
    var open by remember { mutableStateOf<HistoryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val all = History.items
    val shown = all.filter { filter == null || it.tool == filter }
    val used = all.map { it.tool }.distinct()

    Column(modifier) {
        if (only == null && used.isNotEmpty()) {
            ChipRow(listOf<String?>(null) + used, filter, { t -> if (t == null) "همه" else (screenOf(t)?.title ?: t) }) { filter = it }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.History, null, tint = c.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(10.dp))
                Text("هنوز چیزی ذخیره نشده", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "هر کاری که در برنامه انجام بدهی (چت، ترجمه، نقاشی، آهنگ، سایت…) خودکار اینجا ذخیره می‌شود.",
                    fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { h ->
                    val s = screenOf(h.tool)
                    Surface(
                        onClick = { open = h }, shape = RoundedCornerShape(20.dp),
                        color = c.surface.copy(alpha = 0.93f), modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).background(
                                    Brush.linearGradient(listOf(s?.c1 ?: Brand[0], s?.c2 ?: Brand[1])), RoundedCornerShape(15.dp),
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Icon(s?.icon ?: Icons.Rounded.History, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.onSurface)
                                Text(
                                    h.body.replace("\n", " "), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = c.onSurface.copy(alpha = 0.65f),
                                )
                                Text(
                                    "${s?.title ?: h.tool} · ${fmtDate(h.time)}", fontSize = 11.sp, color = c.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            IconButton(onClick = { History.remove(h.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, "حذف", tint = c.onSurface.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            TextButton(onClick = { confirmClear = true }, Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(6.dp))
                Text(if (filter == null) "پاک کردن کل تاریخچه" else "پاک کردن تاریخچه‌ی این بخش")
            }
        }
    }
    open?.let { h -> HistoryDetail(h) { open = null } }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک کردن تاریخچه") },
            text = { Text("همه‌ی موارد ${if (filter == null) "" else "این بخش "}برای همیشه پاک می‌شوند. مطمئنی؟") },
            confirmButton = { TextButton(onClick = { History.clear(filter); confirmClear = false }) { Text("پاک کن") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } },
        )
    }
}

/** جزئیات یک مورد: متن، تصویر، صدا یا صفحه‌ی وب + کپی، اشتراک، ذخیره، حذف */
@Composable
private fun HistoryDetail(h: HistoryItem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val c = MaterialTheme.colorScheme
    val s = screenOf(h.tool)
    var bytes by remember(h.id) { mutableStateOf<ByteArray?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var showHtml by remember { mutableStateOf(false) }
    LaunchedEffect(h.id) { bytes = withContext(Dispatchers.IO) { History.readFile(h) } }
    DisposableEffect(Unit) { onDispose { playJob?.cancel() } }
    val bmp = remember(bytes) {
        if (h.ext == "jpg" || h.ext == "png") bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } else null
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f), shape = RoundedCornerShape(28.dp), color = c.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(s?.c1 ?: Brand[0], s?.c2 ?: Brand[1])))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${s?.title ?: h.tool} · ${fmtDate(h.time)}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "بستن", tint = Color.White) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    bmp?.let { BitmapImage(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) }
                    if (h.ext == "mp3" && bytes != null) {
                        GradButton(
                            if (playing) "توقف" else "پخش صدا",
                            icon = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (playing) { playJob?.cancel(); playing = false } else {
                                    playing = true
                                    playJob = scope.launch { try { playAudioBytes(ctx, bytes!!) } finally { playing = false } }
                                }
                            },
                        )
                    }
                    if (h.ext == "html" && bytes != null) {
                        GradButton(
                            if (showHtml) "بستن پیش‌نمایش" else "نمایش صفحه", icon = Icons.Rounded.Language,
                            modifier = Modifier.fillMaxWidth(), onClick = { showHtml = !showHtml },
                        )
                        if (showHtml) {
                            Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp))) {
                                HtmlPreview(String(bytes!!), Modifier.fillMaxSize())
                            }
                        }
                    }
                    if (h.ext != "html" && h.body.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(18.dp), color = c.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer { Box(Modifier.padding(14.dp)) { RichText(h.body, c.onSurfaceVariant) } }
                        }
                    } else if (h.ext == "html" && h.body.isNotBlank()) {
                        Text("درخواست: ${h.body}", fontSize = 13.sp, color = c.onBackground.copy(alpha = 0.8f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val t = if (h.ext == "html") String(bytes ?: h.body.toByteArray()) else h.body
                            if (canExport()) { clip.setText(AnnotatedString(t)); toast(ctx, "کپی شد") }
                        },
                        Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                    ) { Text("کپی", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { if (canExport()) shareText(ctx, if (h.ext == "html") String(bytes ?: h.body.toByteArray()) else h.body) },
                        Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                    ) { Text("اشتراک", fontSize = 13.sp) }
                    if (bytes != null && h.ext != null) {
                        OutlinedButton(
                            onClick = {
                                if (canExport()) {
                                    val b = bytes!!
                                    val name = "AIHub_${h.time}.${h.ext}"
                                    val ok = if (bmp != null) saveBitmapToGallery(ctx, bmp)
                                    else saveBytesToDownloads(ctx, name, if (h.ext == "mp3") "audio/mpeg" else "text/html", b)
                                    toast(ctx, if (ok) "ذخیره شد" else "ذخیره نشد")
                                }
                            },
                            Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                        ) { Text("ذخیره", fontSize = 13.sp) }
                    }
                    OutlinedButton(
                        onClick = { History.remove(h.id); onClose() },
                        Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                    ) { Text("حذف", fontSize = 13.sp, color = c.error) }
                }
            }
        }
    }
}

/** صفحه‌ی کامل تاریخچه (از منو) */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    ToolScaffold("تاریخچه", onBack) { HistoryList(null, Modifier.weight(1f).fillMaxWidth()) }
}

/** پنجره‌ی تاریخچه‌ی یک بخش (از آیکون ساعت بالای هر صفحه) */
@Composable
fun HistoryDialog(tool: String, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val s = screenOf(tool)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f), shape = RoundedCornerShape(28.dp), color = c.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(s?.c1 ?: Brand[0], s?.c2 ?: Brand[1])))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.History, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("تاریخچه‌ی ${s?.title ?: ""}", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = Color.White) }
                }
                Spacer(Modifier.height(10.dp))
                HistoryList(tool, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
