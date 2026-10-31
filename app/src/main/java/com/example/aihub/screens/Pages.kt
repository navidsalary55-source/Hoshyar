package com.example.aihub.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aihub.core.*
import com.example.aihub.ui.*

// ---------------------------------------------------------------- اجزای مشترک

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(body, fontSize = 13.5.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ContactItem(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = c.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GradCircleIcon(icon, size = 42)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    value, fontSize = 13.sp, color = c.onSurfaceVariant.copy(alpha = 0.75f),
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                )
            }
            IconButton(onClick = { clip.setText(AnnotatedString(value)); toast(ctx, "کپی شد") }) {
                Icon(Icons.Rounded.ContentCopy, "کپی", tint = c.primary)
            }
        }
    }
}

@Composable
private fun ContactList() {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ContactItem(Icons.Rounded.Send, "تلگرام", Config.ID_TELEGRAM) { openUrl(ctx, Config.URL_TELEGRAM) }
        ContactItem(Icons.Rounded.Public, "روبیکا", Config.ID_RUBIKA) { openUrl(ctx, Config.URL_RUBIKA) }
        ContactItem(Icons.Rounded.Chat, "واتساپ", Config.ID_WHATSAPP) {
            clip.setText(AnnotatedString(Config.ID_WHATSAPP)); toast(ctx, "آیدی واتساپ کپی شد")
        }
        ContactItem(Icons.Rounded.Email, "ایمیل", Config.CONTACT_EMAIL) {
            sendEmail(ctx, Config.CONTACT_EMAIL, "پیام از اپ هوش مصنوعی هوشیار")
        }
    }
}

private val pageModifier = Modifier.fillMaxWidth()

// ---------------------------------------------------------------- درباره ما

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ToolScaffold("درباره ما", onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResImage("logo", Modifier.size(104.dp).clip(CircleShape))
            Text(Config.APP_NAME, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("نسخه ${Config.APP_VERSION}", fontSize = 12.5.sp, color = c.primary)
            Text(
                "هوش مصنوعی هوشیار یک جعبه‌ابزار هوش مصنوعی است: چت، چت صوتی، ترجمه، ساخت تصویر و سایت، تغییر صدا، ساخت آهنگ و کاراکتر سه‌بعدی و ابزارهای دیگر در یک اپلیکیشن.",
                textAlign = TextAlign.Center, lineHeight = 26.sp,
            )
            InfoCard("سازنده", "این برنامه توسط ${Config.OWNER} طراحی و ساخته شده است.")
            InfoCard(
                "فناوری‌ها",
                "• هوش مصنوعی، صدا و تصویر از سرور اختصاصی هوشیار\n" +
                    "• تشخیص گفتار و متن‌به‌صدای خود اندروید\n" +
                    "• Jetpack Compose و Material 3",
            )
            Text(
                "پاسخ‌های هوش مصنوعی ممکن است اشتباه باشند؛ برای موضوعات مهم (پزشکی، حقوقی، مالی) حتماً بررسی کن.",
                textAlign = TextAlign.Center, fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- تماس با ما

@Composable
fun ContactScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    ToolScaffold("تماس با ما", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ContactList()
            SectionTitle("ارسال نظر یا پیشنهاد")
            AppField(msg, { msg = it }, "پیامت رو بنویس", minLines = 4)
            GradButton(
                "ارسال با ایمیل", icon = Icons.Rounded.Send, enabled = msg.isNotBlank(),
                modifier = pageModifier,
                onClick = { sendEmail(ctx, Config.CONTACT_EMAIL, "بازخورد اپ هوش مصنوعی هوشیار", msg) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- پشتیبانی

private val faq = listOf(
    "برنامه جواب نمی‌دهد" to
        "اینترنت یا VPN را بررسی کن و در «تنظیمات ← حساب و اشتراک» وضعیت اشتراک و سهمیه‌ات را ببین.",
    "پیام «سهمیه پر شده» (خطای ۴۲۹)" to
        "سهمیه‌ی چت رایگان امروز تمام شده. فردا دوباره تازه می‌شود، یا اشتراک تهیه کن.",
    "چت صوتی یا میکروفون کار نمی‌کند" to
        "اجازه‌ی میکروفون را بده و مطمئن شو برنامه‌ی Google روی گوشی نصب و فعال است. تشخیص گفتار به اینترنت نیاز دارد.",
    "صدای فارسی خوانده نمی‌شود" to
        "از تنظیمات گوشی (Text-to-speech) صدای فارسی را دانلود کن یا در بخش «متن به صدا» حالت آنلاین را انتخاب کن.",
    "ساخت تصویر یا صدا کند است" to
        "این بخش‌ها از سرویس رایگان استفاده می‌کنند و گاهی شلوغ است. چند لحظه بعد دوباره امتحان کن.",
)

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    ToolScaffold("پشتیبانی", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard("به پشتیبانی هوش مصنوعی هوشیار خوش آمدی", "برای گزارش مشکل، پیشنهاد یا راهنمایی از یکی از راه‌های زیر پیام بده.")
            ContactList()
            GradButton(
                "گزارش مشکل با ایمیل", icon = Icons.Rounded.BugReport,
                modifier = pageModifier,
                onClick = {
                    sendEmail(
                        ctx, Config.CONTACT_EMAIL, "گزارش مشکل — هوش مصنوعی هوشیار",
                        "نسخه‌ی برنامه: ${Config.APP_VERSION}\nاندروید: ${Build.VERSION.RELEASE}\nمدل گوشی: ${Build.MODEL}\n\nشرح مشکل:\n",
                    )
                },
            )
            SectionTitle("سؤالات پرتکرار")
            faq.forEach { (q, a) -> InfoCard(q, a) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- قوانین

private val rules = listOf(
    "احترام و ادب" to "با دیگران و تیم پشتیبانی مؤدبانه رفتار کن. توهین، تهدید، آزار و تمسخر ممنوع است.",
    "محتوای مجاز" to "از ابزارها برای تولید محتوای غیرقانونی، خشونت‌آمیز، مستهجن، کلاهبردارانه یا جعل هویت استفاده نکن.",
    "حقوق و حریم دیگران" to "عکس، صدا و متن دیگران را بدون اجازه و برای آسیب‌زدن به آن‌ها پردازش یا منتشر نکن.",
    "مسئولیت خروجی" to "پاسخ‌های هوش مصنوعی ممکن است اشتباه باشند و مسئولیت استفاده از آن‌ها با خود کاربر است. برای موضوعات پزشکی، حقوقی و مالی حتماً به متخصص مراجعه کن.",
    "کد اشتراک" to "کد اشتراک مخصوص یک گوشی است؛ آن را با دیگران به اشتراک نگذار و از سهمیه‌ی رایگان سوءاستفاده نکن.",
    "اطلاعات حساس" to "متن‌ها و عکس‌های تو برای پردازش به سرور برنامه و سرویس‌های هوش مصنوعی همکار ارسال می‌شود. رمز، شماره‌ی کارت و مدارک هویتی نفرست.",
    "حفظ حقوق سازنده" to "کپی، دستکاری و بازنشر برنامه با نام دیگر یا حذف نام سازنده بدون اجازه ممنوع است.",
    "گزارش تخلف" to "اگر تخلفی دیدی، از بخش «پشتیبانی» یا «مدیریت» گزارش بده.",
)

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ToolScaffold("قوانین", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "با استفاده از هوش مصنوعی هوشیار، قوانین زیر را می‌پذیری:",
                fontSize = 14.sp, color = c.onBackground.copy(alpha = 0.8f),
            )
            val digits = "۱۲۳۴۵۶۷۸۹"
            rules.forEachIndexed { i, (t, b) ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.surfaceVariant) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = CircleShape, color = c.primary, modifier = Modifier.size(30.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(digits[i].toString(), color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(b, fontSize = 13.5.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.errorContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ضمانت اجرا", fontWeight = FontWeight.Bold, color = c.onErrorContainer)
                    Text(
                        "هر کس خلاف این قوانین عمل کند، بدون اخطار قبلی از گروه‌ها، کانال‌ها و پشتیبانی ریمو (حذف) می‌شود و در موارد جدی دسترسی او به خدمات ما قطع خواهد شد.",
                        fontSize = 13.5.sp, lineHeight = 22.sp, color = c.onErrorContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- مدیریت

@Composable
fun ManagementScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    ToolScaffold("مدیریت", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = c.surfaceVariant) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ResImage("logo", Modifier.size(72.dp).clip(CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(Config.OWNER, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("مدیر و سازنده‌ی هوش مصنوعی هوشیار", fontSize = 13.sp, color = c.primary)
                    }
                }
            }
            InfoCard(
                "وظایف مدیریت",
                "پاسخ به پیام‌ها و پیشنهادها، بررسی گزارش تخلف‌ها، به‌روزرسانی برنامه و اجرای قوانین.",
            )
            SectionTitle("ارتباط با مدیریت")
            ContactList()
            GradButton(
                "گزارش تخلف", icon = Icons.Rounded.Gavel,
                modifier = pageModifier,
                colors = listOf(androidx.compose.ui.graphics.Color(0xFFFF5C8A), androidx.compose.ui.graphics.Color(0xFFFF9A62)),
                onClick = { sendEmail(ctx, Config.CONTACT_EMAIL, "گزارش تخلف — هوش مصنوعی هوشیار", "شرح تخلف (آیدی فرد، توضیح، مدرک):\n") },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
