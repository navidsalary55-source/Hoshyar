package ir.hooshyar.app.screens

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateFloat
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.hooshyar.app.core.*
import ir.hooshyar.app.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class Persona(val name: String, val system: String)

val Personas = listOf(
    Persona("دستیار عمومی", "You are a helpful, friendly and smart assistant."),
    Persona("معلم صبور", "You are a patient teacher. Explain step by step with simple examples and check understanding."),
    Persona("برنامه‌نویس", "You are a senior software engineer. Give correct, clean code with brief explanations."),
    Persona("نویسنده خلاق", "You are a creative writer with a vivid and elegant style."),
    Persona("مشاور کسب‌وکار", "You are a pragmatic business and marketing consultant. Give actionable advice."),
    Persona("مربی زبان", "You are a language coach. Gently correct mistakes and give useful examples."),
)

private const val BASE_SYSTEM =
    "Always answer in the same language the user writes in (default to Persian). " +
        "Keep answers clear. Use simple markdown (bold, lists, code blocks) when helpful. "

class ChatVM(app: Application) : AndroidViewModel(app) {
    val messages = mutableStateListOf<ChatMsg>()
    var isStreaming by mutableStateOf(false)
        private set
    var persona by mutableStateOf(Personas.first())
    var temperature by mutableStateOf(0.8f)
    val attachedList = mutableStateListOf<ByteArray>()
    private var job: Job? = null
    private var nextId = 0L
    var tool = "CHAT"
    private var histId = -1L

    private fun saveHistory() {
        val ok = messages.filter { !it.isError && it.text.isNotBlank() }
        if (ok.isEmpty()) return
        val body = ok.joinToString("\n\n") { (if (it.isUser) "🧑 " else "🤖 ") + it.text }
        val title = ok.first { it.isUser }.text
        histId = History.update(histId, tool, title, body)
    }

    fun newChat() {
        job?.cancel(); isStreaming = false; messages.clear(); attachedList.clear(); histId = -1L
    }

    fun stop() { job?.cancel() }

    private fun update(id: Long, f: (ChatMsg) -> ChatMsg) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = f(messages[i])
    }

    fun send(raw: String) {
        val text = raw.trim()
        val imgs = attachedList.toList()
        if ((text.isEmpty() && imgs.isEmpty()) || isStreaming) return
        messages.add(ChatMsg(nextId++, true, text.ifEmpty { if (imgs.size > 1) "این تصویرها را توضیح بده." else "این تصویر را توضیح بده." }, images = imgs))
        attachedList.clear()
        val valid = messages.filter { !it.isError && (it.text.isNotBlank() || it.allImages.isNotEmpty()) }
        val history = valid.mapIndexed { i, m -> if (i == valid.lastIndex) m else m.copy(image = null, images = emptyList()) }
        val replyId = nextId++
        messages.add(ChatMsg(replyId, false, ""))
        isStreaming = true
        job = viewModelScope.launch {
            var acc = ""
            try {
                Ai.stream(history, BASE_SYSTEM + persona.system, temperature.toDouble(), if (tool == "CHAT") "chat" else "").collect { chunk ->
                    acc += chunk
                    update(replyId) { it.copy(text = acc) }
                }
                if (acc.isEmpty()) update(replyId) { it.copy(text = "پاسخی دریافت نشد. دوباره امتحان کن.", isError = true) }
                else saveHistory()
            } catch (e: CancellationException) {
                if (acc.isEmpty()) messages.removeAll { it.id == replyId }
                throw e
            } catch (e: Exception) {
                update(replyId) { it.copy(text = e.message ?: "خطای ناشناخته", isError = true) }
            } finally {
                isStreaming = false
            }
        }
    }
}

const val MAX_CHAT_IMAGES = 20

@Composable
fun ChatScreen(advanced: Boolean, onBack: () -> Unit) {
    val vm: ChatVM = viewModel(key = if (advanced) "adv" else "simple")
    vm.tool = if (advanced) "ADV_CHAT" else "CHAT"
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showTune by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val msgs = vm.messages
    val lastText = msgs.lastOrNull()?.text

    LaunchedEffect(msgs.size, lastText) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_CHAT_IMAGES)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val room = MAX_CHAT_IMAGES - vm.attachedList.size
            if (uris.size > room) toast(ctx, "حداکثر $MAX_CHAT_IMAGES عکس؛ ${uris.size - room.coerceAtLeast(0)} عکس اضافه نادیده گرفته شد")
            val dim = when { vm.attachedList.size + uris.size > 10 -> 800; vm.attachedList.size + uris.size > 4 -> 1024; else -> 1280 }
            for (u in uris.take(room.coerceAtLeast(0))) {
                val bytes = withContext(Dispatchers.IO) { decodeBitmap(ctx, u, dim)?.toJpegBytes(if (dim <= 1024) 80 else 85) }
                if (bytes != null) vm.attachedList.add(bytes) else toast(ctx, "یک تصویر خوانده نشد")
            }
        }
    }

    ToolScaffold(
        title = if (advanced) "چت پیشرفته" else "چت ساده",
        onBack = onBack,
        actions = {
            if (advanced) IconButton(onClick = { showTune = !showTune }) { Icon(Icons.Rounded.Tune, "تنظیمات چت") }
            IconButton(onClick = { vm.newChat() }, enabled = msgs.isNotEmpty()) { Icon(Icons.Rounded.Add, "گفتگوی جدید") }
        },
    ) {
        if (advanced) {
            ChipRow(Personas, vm.persona, { it.name }) { vm.persona = it }
            if (showTune) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("خلاقیت پاسخ‌ها: ${"%.1f".format(vm.temperature)}", fontSize = 13.sp)
                    Slider(value = vm.temperature, onValueChange = { vm.temperature = it }, valueRange = 0f..1.5f)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.weight(1f)) {
            if (msgs.isEmpty()) {
                ChatWelcome(advanced) { vm.send(it) }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(msgs, key = { it.id }) { m ->
                        MessageBubble(m, typing = vm.isStreaming && m.id == msgs.last().id)
                    }
                }
            }
        }
        if (advanced) MediaBar(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxImages = MAX_CHAT_IMAGES, onImage = { b ->
            if (vm.attachedList.size >= MAX_CHAT_IMAGES) toast(ctx, "حداکثر $MAX_CHAT_IMAGES عکس")
            else scope.launch { vm.attachedList.add(withContext(Dispatchers.Default) { b.toJpegBytes(82) }) }
        })
        ChatInput(
            value = input, onChange = { input = it }, streaming = vm.isStreaming,
            onSend = { vm.send(input); input = "" }, onStop = { vm.stop() },
            attached = vm.attachedList,
            onAttach = if (advanced) ({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) else null,
            onRemoveAttach = { i -> if (i in vm.attachedList.indices) vm.attachedList.removeAt(i) },
        )
    }
}

private val suggestions = listOf(
    "یه ایده‌ی خلاقانه برای یه کسب‌وکار کوچیک بده",
    "یه کد پایتون برای مرتب‌سازی لیست بنویس",
    "یه داستان کوتاه و جذاب برام تعریف کن",
    "این هفته چی یاد بگیرم که به دردم بخوره؟",
)

@Composable
private fun ChatWelcome(advanced: Boolean, onPick: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GradCircleIcon(if (advanced) Icons.Rounded.AutoAwesome else Icons.Rounded.Chat, size = 84)
        Spacer(Modifier.height(18.dp))
        Text("سلام! 👋", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (advanced) "شخصیت رو انتخاب کن، عکس بفرست و بپرس." else "هر چی می‌خوای بپرس، من اینجام.",
            color = c.onBackground.copy(alpha = 0.65f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))
        suggestions.forEach { s ->
            Surface(
                onClick = { onPick(s) },
                shape = RoundedCornerShape(18.dp),
                color = c.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) { Text(s, Modifier.padding(16.dp), fontSize = 14.5.sp) }
        }
    }
}

@Composable
fun MessageList(msgs: List<ChatMsg>, typing: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex) }
    LazyColumn(
        state = listState, modifier = modifier,
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(msgs, key = { it.id }) { m -> MessageBubble(m, typing && m.id == msgs.last().id) }
    }
}

@Composable
fun MessageBubble(m: ChatMsg, typing: Boolean) {
    val c = MaterialTheme.colorScheme
    val shape = if (m.isUser) RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp) else RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    val textColor = when {
        m.isUser -> Color.White
        m.isError -> c.onErrorContainer
        else -> c.onSurfaceVariant
    }
    val base = Modifier.widthIn(max = 340.dp).clip(shape)
    val styled = when {
        m.isUser -> base.background(grad(Brand))
        m.isError -> base.background(c.errorContainer)
        else -> base.background(c.surfaceVariant)
    }.padding(horizontal = 14.dp, vertical = 10.dp)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.isUser) Arrangement.Start else Arrangement.End) {
        Column(styled) {
            val imgs = m.allImages
            if (imgs.isNotEmpty()) {
                val size = if (imgs.size == 1) 160.dp else 84.dp
                Row(Modifier.widthIn(max = 312.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    imgs.forEach { bytes ->
                        val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                if (imgs.size > 1) Text("🖼️ ${imgs.size} تصویر", fontSize = 11.sp, color = textColor.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
            }
            if (typing && m.text.isEmpty()) TypingDots() else SelectionContainer { RichText(m.text, textColor) }
        }
    }
}

@Composable
fun TypingDots() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(600, delayMillis = i * 180, easing = androidx.compose.animation.core.LinearEasing),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(Modifier.size(9.dp).alpha(a).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
fun ChatInput(
    value: String,
    onChange: (String) -> Unit,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    attached: List<ByteArray> = emptyList(),
    onAttach: (() -> Unit)? = null,
    onRemoveAttach: (Int) -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val canSend = value.isNotBlank() || streaming || attached.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
        if (attached.isNotEmpty()) {
            Row(Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attached.forEachIndexed { i, bytes ->
                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    Box(Modifier.size(60.dp)) {
                        if (bmp != null) {
                            Image(
                                bmp.asImageBitmap(), null,
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
                            )
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xCC000000)).clickable { onRemoveAttach(i) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "حذف تصویر", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
                Text("${attached.size}/$MAX_CHAT_IMAGES", fontSize = 12.sp, color = c.onSurface.copy(alpha = 0.6f))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (onAttach != null) {
                IconButton(onClick = onAttach) { Icon(Icons.Rounded.AddPhotoAlternate, "پیوست تصویر", tint = c.primary) }
            }
            TextField(
                value = value, onValueChange = onChange, modifier = Modifier.weight(1f),
                placeholder = { Text("پیامت رو بنویس…") },
                shape = RoundedCornerShape(26.dp), maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(54.dp).alpha(if (canSend) 1f else 0.4f).clip(CircleShape)
                    .background(grad(Brand)).clickable(enabled = canSend) { if (streaming) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (streaming) Icons.Rounded.Close else Icons.Rounded.Send,
                    if (streaming) "توقف" else "ارسال", tint = Color.White,
                )
            }
        }
    }
}
