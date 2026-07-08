package com.alertnotes.core.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text with every occurrence of [query] emphasized in the primary color —
 * used by list rows so search matches are visible at a glance.
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, query, highlightColor) {
        highlightMatches(text, query, highlightColor)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun highlightMatches(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val span = SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)
        var searchFrom = 0
        while (true) {
            val index = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
            if (index < 0) break
            addStyle(span, index, index + query.length)
            searchFrom = index + query.length
        }
    }
}
