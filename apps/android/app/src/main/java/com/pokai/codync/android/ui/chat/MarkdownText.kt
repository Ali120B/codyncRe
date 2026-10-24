package com.pokai.codync.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

/**
 * Lightweight markdown for chat bubbles: **bold**, *italic*, `code`, ```fences```, [links](url).
 * Good enough for M2 agent replies without pulling a full markdown engine.
 */
@Composable
fun MarkdownText(
    source: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = CodyncTheme.colors
    val blocks = remember(source) { splitMarkdownBlocks(source) }
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> {
                    SelectionContainer {
                        Text(
                            block.body.trimEnd(),
                            color = colors.secondary,
                            style = CodyncType.mono,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.codeBackground)
                                .padding(10.dp),
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = remember(block.body, color) { inlineMarkdown(block.body, color) },
                        style = CodyncType.body,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class Paragraph(val body: String) : MdBlock()
    data class Code(val body: String) : MdBlock()
}

private fun splitMarkdownBlocks(source: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val fence = Regex("```[\\w+-]*\\n([\\s\\S]*?)```")
    var last = 0
    fence.findAll(source).forEach { m ->
        if (m.range.first > last) {
            val para = source.substring(last, m.range.first).trim()
            if (para.isNotEmpty()) out += MdBlock.Paragraph(para)
        }
        out += MdBlock.Code(m.groupValues[1])
        last = m.range.last + 1
    }
    if (last < source.length) {
        val para = source.substring(last).trim()
        if (para.isNotEmpty()) out += MdBlock.Paragraph(para)
    }
    if (out.isEmpty()) out += MdBlock.Paragraph(source)
    return out
}

private fun inlineMarkdown(text: String, color: Color) = buildAnnotatedString {
    // Order: code → bold → italic → links
    val pattern = Regex(
        "`([^`]+)`|\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*|\\[([^\\]]+)]\\(([^)]+)\\)|([^*`\\[]+)|(.)",
    )
    pattern.findAll(text).forEach { m ->
        when {
            m.groups[1] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = color.copy(alpha = 0.08f)),
            ) {
                append(m.groupValues[1])
            }
            m.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) {
                append(m.groupValues[2])
            }
            m.groups[3] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                append(m.groupValues[3])
            }
            m.groups[4] != null -> withStyle(
                SpanStyle(color = color, textDecoration = TextDecoration.Underline),
            ) {
                append(m.groupValues[4])
            }
            m.groups[6] != null -> withStyle(SpanStyle(color = color)) { append(m.groupValues[6]) }
            m.groups[7] != null -> withStyle(SpanStyle(color = color)) { append(m.groupValues[7]) }
            else -> withStyle(SpanStyle(color = color)) { append(m.value) }
        }
    }
}
