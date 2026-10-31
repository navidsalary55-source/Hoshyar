package com.example.aihub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** رندر ساده‌ی مارک‌داون: **bold**، `code`، لیست‌ها، تیترها و بلوک کد. */
@Composable
fun RichText(text: String, color: Color) {
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1) {
                val code = part.substringAfter('\n', part).trimEnd()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF12131F))
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            code,
                            color = Color(0xFFE6E6F5),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            } else {
                part.trim('\n').split('\n').forEach { raw ->
                    if (raw.isNotBlank()) {
                        val t = raw.trimStart()
                        val display = when {
                            t.startsWith("#") -> "**" + t.trimStart('#').trim() + "**"
                            t.startsWith("* ") || t.startsWith("- ") -> "• " + t.drop(2)
                            else -> raw
                        }
                        Text(
                            text = inline(display, color.copy(alpha = 0.14f)),
                            color = color,
                            fontSize = 15.5.sp,
                            lineHeight = 24.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun inline(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val e = s.indexOf("**", i + 2)
            if (e != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }
                i = e + 2
                continue
            }
        } else if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e != -1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                    append(s.substring(i + 1, e))
                }
                i = e + 1
                continue
            }
        }
        append(s[i])
        i++
    }
}
