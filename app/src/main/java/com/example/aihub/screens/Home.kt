package com.example.aihub.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History as HistoryIcon
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.core.Config
import com.example.aihub.core.History
import com.example.aihub.core.Store
import com.example.aihub.ui.BannerBox
import com.example.aihub.ui.ResImage
import com.example.aihub.ui.Screen
import com.example.aihub.ui.rememberAsset
import kotlinx.coroutines.launch

private val origins = listOf(
    TransformOrigin(0.15f, 0.2f), TransformOrigin(0.5f, 0.5f), TransformOrigin(0.85f, 0.8f),
    TransformOrigin(0.85f, 0.2f), TransformOrigin(0.15f, 0.8f), TransformOrigin(0.5f, 0.15f),
)

private val dailyTips = listOf(
    "به‌جای «بنویس»، بگو برای چه کسی، با چه لحنی و چقدر طولانی؛ جواب‌ها خیلی بهتر می‌شوند.",
    "در عکس‌خوان یک عکس از متن کتاب یا تابلو بگیر؛ هم می‌خواند، هم ترجمه می‌کند.",
    "آزمون‌ساز را قبل از امتحان امتحان کن: موضوع را بده و خودت را بسنج.",
    "در چت صوتی «گفتگوی پیوسته» را روشن کن تا مثل تماس تلفنی با ربات حرف بزنی.",
    "مقاله‌ی بلند را لینکش را در خلاصه‌ساز بگذار؛ نکات کلیدی‌اش را در ده ثانیه می‌گیری.",
    "هر روز سهمیه‌ی رایگان داری؛ با اشتراک (تنظیمات ← حساب و اشتراک) سهمیه‌ات بیشتر می‌شود.",
    "برنامه‌ریز هدفت را به چک‌لیست روزانه تبدیل می‌کند؛ هر روز یک تیک بزن.",
    "برای ساخت نقاشی بهتر، سبک را انتخاب کن و جزئیات رنگ و نور را بنویس.",
    "با آشپز هوشمند مواد داخل یخچال را بنویس تا غذای شام را پیشنهاد بدهد.",
    "یادداشت صوتی را ضبط کن و بگو «لیست کارها» تا حرف‌هایت چک‌لیست شود.",
    "همه‌ی کارهایت در «تاریخچه» ذخیره می‌شود؛ هر وقت خواستی برگرد و ادامه بده.",
    "ویجت هوش مصنوعی هوشیار را روی صفحه‌ی اصلی گوشی بگذار تا با یک لمس به بخش‌های پرکاربرد برسی.",
    "با مربی زبان، قبل از سفر مکالمه‌ی رستوران یا فرودگاه را تمرین کن.",
    "در برنامه‌ریز یادآور روزانه را روشن کن تا هر روز کار بعدی را در اعلان ببینی.",
    "در ایده‌ساز کسب‌وکار نوع کار را عوض کن تا از اسم و شعار تا تقویم محتوا بگیری.",
)

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Screen.categories[0]) }
    val shown = Screen.tools.filter { s ->
        (cat == Screen.categories[0] || s.category == cat) &&
            (query.isBlank() || s.title.contains(query.trim()) || s.sub.contains(query.trim()))
    }
    val tip = remember { dailyTips[((System.currentTimeMillis() / 86_400_000L) % dailyTips.size).toInt()] }
    val robot = rememberAsset("page_bg")
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    ResImage("hero_bg", Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0x990B1020), Color(0xE60B1020)))
                        )
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ResImage("logo", Modifier.size(56.dp).clip(CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Config.APP_NAME, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text("سازنده: ${Config.OWNER}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Screen.drawerItems.forEach { s ->
                    NavigationDrawerItem(
                        label = { Text(s.title, fontWeight = FontWeight.Medium) },
                        icon = { Icon(s.icon, null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; onOpen(s) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            BannerBox(height = 168.dp) { top ->
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.align(Alignment.TopStart).padding(top = top, start = 8.dp),
                ) { Icon(Icons.Rounded.Menu, "منو", tint = Color.White) }
                IconButton(
                    onClick = { onOpen(Screen.SETTINGS) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = top, end = 8.dp),
                ) { Icon(Icons.Rounded.Settings, "تنظیمات", tint = Color.White) }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ResImage("logo", Modifier.size(64.dp).clip(CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Config.APP_NAME, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp)
                        Text(
                            "همه‌ی ابزارهای هوش مصنوعی در یک اپ",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp,
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (!Store.paid) {
                    item(span = { GridItemSpan(2) }) {
                        Surface(
                            onClick = { onOpen(Screen.SETTINGS) },
                            shape = RoundedCornerShape(18.dp),
                            color = c.primary.copy(alpha = 0.14f),
                        ) {
                            Text(
                                "همه‌ی بخش‌ها را می‌توانی رایگان امتحان کنی؛ برای ذخیره، دانلود و کپی نتیجه اشتراک لازم است. ←",
                                Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.5.sp, lineHeight = 22.sp,
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(2) }) {
                    Surface(shape = RoundedCornerShape(20.dp), color = c.surface.copy(alpha = 0.9f)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💡", fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("نکته‌ی امروز", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
                                Text(tip, fontSize = 12.5.sp, lineHeight = 20.sp, color = c.onSurface)
                            }
                        }
                    }
                }
                History.items.firstOrNull()?.let { h ->
                    item(span = { GridItemSpan(2) }) {
                        Surface(
                            onClick = { onOpen(Screen.HISTORY) }, shape = RoundedCornerShape(20.dp),
                            color = c.primary.copy(alpha = 0.14f),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.HistoryIcon, null, tint = c.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("ادامه از آخرین کار", fontSize = 11.5.sp, color = c.primary, fontWeight = FontWeight.Bold)
                                    Text(h.title, fontSize = 13.5.sp, maxLines = 1, color = c.onSurface)
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(2) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("جستجوی ابزار…") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "پاک") } },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = c.surface.copy(alpha = 0.92f), unfocusedContainerColor = c.surface.copy(alpha = 0.92f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Screen.categories.forEach { name ->
                                val sel = name == cat
                                Surface(onClick = { cat = name }, shape = RoundedCornerShape(50), color = if (sel) c.primary else c.surface.copy(alpha = 0.9f)) {
                                    Text(
                                        name, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp,
                                        color = if (sel) Color.White else c.onSurface, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text("ابزاری پیدا نشد", Modifier.fillMaxWidth().padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                itemsIndexed(shown) { i, s ->
                    val o = origins[i % origins.size]
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onOpen(s) }
                    ) {
                        if (robot != null) {
                            Image(
                                robot, null,
                                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.7f; scaleY = 1.7f; transformOrigin = o },
                                contentScale = ContentScale.Crop,
                            )
                        }
                        // لایه‌ی رنگی شفاف‌تر تا عکس ربات واضح‌تر دیده شود
                        val a1 = if (robot != null) 0.28f else 1f
                        val a2 = if (robot != null) 0.18f else 1f
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(s.c1.copy(alpha = a1), s.c2.copy(alpha = a2)))
                            )
                        )
                        // سایه‌ی ملایم پایین کارت برای خوانایی متن
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color(0x14000000), Color(0x99000000)))
                            )
                        )
                        Column(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.26f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                            Column {
                                Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, style = LocalTextStyle.current.copy(shadow = Shadow(Color(0xCC000000), blurRadius = 8f)))
                                Text(
                                    s.sub, color = Color.White.copy(alpha = 0.92f), fontSize = 11.5.sp,
                                    lineHeight = 15.sp, maxLines = 2, style = LocalTextStyle.current.copy(shadow = Shadow(Color(0xCC000000), blurRadius = 8f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
