package com.example.aihub.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.example.aihub.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aihub.core.Store
import com.example.aihub.core.canExport
import com.example.aihub.core.saveBytesToDownloads
import com.example.aihub.core.shareText
import com.example.aihub.core.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** پس‌زمینه‌ی مشترک؛ در صفحه‌های داخلی عکس تمام‌صفحه با لایه‌ی محوکننده */
@Composable
fun AppBackground(showImage: Boolean = false, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(c.background)) {
        if (showImage) {
            ResImage("page_bg", Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(c.background.copy(alpha = 0.58f), c.background.copy(alpha = 0.84f), c.background.copy(alpha = 0.94f))
                    )
                )
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(c.background, c.background, c.primary.copy(alpha = 0.10f).compositeOver(c.background))
                    )
                )
            )
        }
        content()
    }
}

/** عکس‌ها به‌صورت متن (base64) در assets ذخیره شده‌اند؛ بارگذاری امن و یک‌بار برای همیشه */
object Pics {
    private val cache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    fun load(ctx: android.content.Context, name: String, maxDim: Int = 1100): androidx.compose.ui.graphics.ImageBitmap? =
        synchronized(cache) {
            cache.getOrPut(name) {
                try {
                    val text = ctx.assets.open("$name.b64").bufferedReader().use { it.readText() }
                    val bytes = android.util.Base64.decode(text, android.util.Base64.DEFAULT)
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var sample = 1
                    while (maxOf(o.outWidth, o.outHeight) / sample > maxDim) sample *= 2
                    val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)?.asImageBitmap()
                } catch (e: Throwable) {
                    null
                }
            }
        }
}

@Composable
fun rememberAsset(name: String): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    return remember(name) { Pics.load(ctx, name) }
}

@Composable
fun ResImage(name: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val bmp = rememberAsset(name)
    if (bmp != null) {
        Image(bmp, null, modifier, contentScale = scale)
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF0B1020), Color(0xFF3B2F8F), Color(0xFF1F6FB5)))))
    }
}

/** بنر تصویری با لایه‌ی تیره؛ ارتفاع = ارتفاع داده‌شده + نوار وضعیت */
@Composable
fun BannerBox(height: Dp, content: @Composable BoxScope.(top: Dp) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier.fillMaxWidth().height(height + top)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        ResImage("hero_bg", Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xD90B1020), Color(0x990B1020), Color(0xE60B1020)))
            )
        )
        this.content(top)
    }
}

@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val guide = remember(title) { Guides.forTitle(title) }
    var showGuide by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title) {
        if (guide != null && !Store.guideSeen(guide.id)) { Store.markGuide(guide.id); showGuide = true }
    }
    if (showGuide && guide != null) GuideDialog(guide) { showGuide = false }
    val histTool = remember(title) {
        Screen.values().firstOrNull { it.title == title && it in Screen.tools }?.name
    }
    var showHist by remember(title) { mutableStateOf(false) }
    if (showHist && histTool != null) HistoryDialog(histTool) { showHist = false }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color(0xF00B1020), Color(0xB30B1020), Color(0x000B1020)))
            )
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    Modifier.fillMaxWidth().padding(top = top, start = 8.dp, end = 8.dp, bottom = 16.dp).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "بازگشت")
                    }
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                    actions()
                    if (histTool != null) {
                        IconButton(onClick = { showHist = true }) {
                            Icon(Icons.Rounded.History, contentDescription = "تاریخچه")
                        }
                    }
                    if (guide != null) {
                        IconButton(onClick = { showGuide = true }) {
                            Icon(Icons.Rounded.Info, contentDescription = "راهنما")
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(top = 4.dp), content = content)
        }
    }
}

/** وضعیت اجرای یک کار طولانی (loading / error) */
class TaskState {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var job: Job? = null

    fun launch(scope: CoroutineScope, block: suspend () -> Unit) {
        if (loading) return
        error = null
        loading = true
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "خطای ناشناخته"
            } finally {
                loading = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
        loading = false
    }
}

@Composable
fun GradButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    colors: List<Color> = Brand,
) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .background(grad(colors))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val sel = item == selected
            Surface(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(50),
                color = if (sel) c.primary else c.surfaceVariant,
            ) {
                Text(
                    label(item),
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (sel) Color.White else c.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 6,
    ltr: Boolean = false,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        trailingIcon = trailing,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = if (ltr) LocalTextStyle.current.copy(textDirection = TextDirection.Ltr) else LocalTextStyle.current,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
fun ErrorText(msg: String?) {
    if (msg == null) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Text(msg, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.5.sp)
    }
}

@Composable
fun ResultCard(text: String, modifier: Modifier = Modifier, onSpeak: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.surfaceVariant) {
        Column(Modifier.padding(16.dp)) {
            if (Store.paid) SelectionContainer { RichText(text, c.onSurfaceVariant) } else RichText(text, c.onSurfaceVariant)
            Row {
                IconButton(onClick = { if (canExport()) { clip.setText(AnnotatedString(text)); toast(ctx, "کپی شد") } }) {
                    Icon(Icons.Rounded.ContentCopy, "کپی", tint = c.primary)
                }
                IconButton(onClick = {
                    if (canExport()) {
                        val ok = saveBytesToDownloads(ctx, "AIHub_${System.currentTimeMillis()}.txt", "text/plain", text.toByteArray())
                        toast(ctx, if (ok) "در Download/AIHub ذخیره شد" else "ذخیره نشد")
                    }
                }) { Icon(Icons.Rounded.Download, "ذخیره", tint = c.primary) }
                IconButton(onClick = { if (canExport()) shareText(ctx, text) }) { Icon(Icons.Rounded.Share, "اشتراک", tint = c.primary) }
                if (onSpeak != null) {
                    IconButton(onClick = onSpeak) { Icon(Icons.Rounded.VolumeUp, "پخش صدا", tint = c.primary) }
                }
            }
        }
    }
}

@Composable
fun GradCircleIcon(icon: ImageVector, size: Int = 44, colors: List<Color> = Brand) {
    Box(Modifier.size(size.dp).background(grad(colors), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreview(html: String, modifier: Modifier = Modifier, onError: (String) -> Unit = {}, onInfo: (String) -> Unit = {}) {
    val onErr by rememberUpdatedState(onError)
    val onInf by rememberUpdatedState(onInfo)
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.postDelayed({
                            view.evaluateJavascript("(function(){return typeof THREE+'|canvas:'+document.querySelectorAll('canvas').length+'|'+(window.__dbg||'')})()") {
                                onInf(it.orEmpty().trim('"'))
                            }
                        }, 1500)
                    }
                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        if (request?.isForMainFrame == true) onErr("بارگذاری صفحه ناموفق: ${error?.description}")
                    }
                    /** three.js داخل خود اپ است؛ به اینترنت و CDN نیازی نیست */
                    override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                        val u = request?.url?.toString().orEmpty()
                        if (Regex("three(\\.min)?\\.js").containsMatchIn(u.substringBefore('?'))) {
                            return try {
                                android.webkit.WebResourceResponse("application/javascript", "UTF-8", c.assets.open("three.min.js"))
                            } catch (e: Exception) { null }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(m: android.webkit.ConsoleMessage?): Boolean {
                        if (m?.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) onErr(m.message().orEmpty())
                        return true
                    }
                }
            }
        },
        update = { w ->
            if (w.tag != html) {
                w.tag = html
                w.loadDataWithBaseURL("https://preview.local/", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

@Composable
fun BitmapImage(bmp: Bitmap, modifier: Modifier = Modifier, crop: Boolean = false) {
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
        contentScale = if (crop) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit,
    )
}

/** دکمه‌ی «ذخیره»ی متن در پوشه‌ی Download؛ بدون اشتراک پنجره‌ی اشتراک باز می‌شود */
@Composable
fun SaveTextButton(text: String, name: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            if (canExport()) {
                val ok = saveBytesToDownloads(ctx, "AIHub_${name}_${System.currentTimeMillis()}.txt", "text/plain", text.toByteArray())
                toast(ctx, if (ok) "در Download/AIHub ذخیره شد" else "ذخیره نشد")
            }
        },
        modifier,
    ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("ذخیره") }
}
