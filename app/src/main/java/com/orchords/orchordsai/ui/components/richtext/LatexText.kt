package com.orchords.orchordsai.ui.components.richtext

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import ru.noties.jlatexmath.JLatexMathDrawable

fun assumeLatexSize(latex: String, fontSize: Float): Rect {
    return runCatching {
        JLatexMathDrawable.builder(latex)
            .textSize(fontSize)
            .padding(0)
            .build()
            .bounds
    }.getOrElse { Rect(0, 0, 0, 0) }
}

@Composable
fun LatexText(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val style = style.merge(
        fontSize = fontSize,
        color = color
    )
    val density = LocalDensity.current

    val drawable = remember(latex, fontSize, style) {
        runCatching {
            with(density) {
                getLatexDrawable(
                    latex = processLatex(latex),
                    fontSize = fontSize.toPx(),
                    color = style.color.toArgb(),
                    background = style.background.toArgb()
                )
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    if (drawable != null) {
        with(density) {
            Canvas(
                modifier = modifier
                    .size(
                        width = drawable.bounds.width().toDp(),
                        height = drawable.bounds.height().toDp()
                    )
            ) {
                drawable.draw(drawContext.canvas.nativeCanvas)
            }
        }
    } else {
        Text(
            text = latex,
            style = style,
            modifier = modifier
        )
    }
}

fun getLatexDrawable(
    latex: String,
    fontSize: Float,
    color: Int,
    background: Int
): JLatexMathDrawable? {
    return runCatching {
        JLatexMathDrawable.builder(processLatex(latex))
            .textSize(fontSize)
            .color(color)
            .background(background)
            .padding(0)
            .align(JLatexMathDrawable.ALIGN_LEFT)
            .build()
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()
}

/**
 * Wrap inline LaTeX only at syntax-safe top-level boundaries. Protected groups are rendered as
 * one drawable even when they exceed [maxWidthPx]; visual overflow is safer than corrupting math.
 */
fun splitLatex(
    latex: String,
    maxWidthPx: Float,
    fontSize: Float,
    color: Int
): List<JLatexMathDrawable> {
    return runCatching {
        val processed = processLatex(latex)
        val atomicSegments = segmentLatexForWrapping(processed)
        if (atomicSegments.size == 1) {
            return@runCatching listOf(buildInlineLatexDrawable(atomicSegments.single(), fontSize, color))
        }

        val lines = mutableListOf<JLatexMathDrawable>()
        var current = ""
        atomicSegments.forEach { segment ->
            val candidate = current + segment
            val candidateDrawable = buildInlineLatexDrawable(candidate, fontSize, color)
            if (current.isNotEmpty() && candidateDrawable.bounds.width() > maxWidthPx) {
                lines += buildInlineLatexDrawable(current, fontSize, color)
                current = segment
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) {
            lines += buildInlineLatexDrawable(current, fontSize, color)
        }
        lines
    }.onFailure {
        it.printStackTrace()
    }.getOrElse { emptyList() }
}

private fun buildInlineLatexDrawable(
    latex: String,
    fontSize: Float,
    color: Int,
): JLatexMathDrawable = JLatexMathDrawable.builder(latex)
    .textSize(fontSize)
    .color(color)
    .padding(0)
    .align(JLatexMathDrawable.ALIGN_LEFT)
    .build()

@Composable
fun LatexDrawable(
    drawable: JLatexMathDrawable,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    with(density) {
        Canvas(
            modifier = modifier.size(
                width = drawable.bounds.width().toDp(),
                height = drawable.bounds.height().toDp()
            )
        ) {
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
    }
}

private val inlineDollarRegex = Regex("""^\$(.*?)\$""", RegexOption.DOT_MATCHES_ALL)
private val displayDollarRegex = Regex("""^\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL)
private val inlineParenRegex = Regex("""^\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL)
private val displayBracketRegex = Regex("""^\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL)

private fun processLatex(latex: String): String {
    val trimmed = latex.trim()
    return when {
        displayDollarRegex.matches(trimmed) ->
            displayDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        inlineDollarRegex.matches(trimmed) ->
            inlineDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        displayBracketRegex.matches(trimmed) ->
            displayBracketRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        inlineParenRegex.matches(trimmed) ->
            inlineParenRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        else -> trimmed
    }
}
