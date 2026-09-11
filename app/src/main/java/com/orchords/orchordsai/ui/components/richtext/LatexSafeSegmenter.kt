package com.orchords.orchordsai.ui.components.richtext

/**
 * Returns conservative LaTeX chunks that are safe to wrap independently.
 *
 * The scanner is iterative and bounded. Operators inside braces/brackets/parentheses or used as
 * a single-token script operand are never treated as top-level wrap points. Incomplete, deeply
 * nested, oversized, or environment/\left expressions fall back to one unsplit chunk rather than
 * risking a syntactically invalid visual split.
 */
internal fun segmentLatexForWrapping(source: String): List<String> {
    if (source.isEmpty() || source.length > MAX_LATEX_WRAP_SCAN_CHARS) return listOf(source)
    if ("\\begin{" in source || "\\left" in source) return listOf(source)

    val cuts = mutableListOf<Int>()
    var braceDepth = 0
    var bracketDepth = 0
    var parenDepth = 0
    var maxDepth = 0
    var scriptOperandPending = false
    var index = 0

    while (index < source.length) {
        val ch = source[index]
        if (ch == '\\') {
            index++
            if (index >= source.length) return listOf(source)
            if (source[index].isLetter()) {
                while (index + 1 < source.length && source[index + 1].isLetter()) index++
            }
            if (scriptOperandPending) scriptOperandPending = false
            index++
            continue
        }

        if (scriptOperandPending && ch.isWhitespace()) {
            index++
            continue
        }

        when (ch) {
            '{' -> {
                braceDepth++
                maxDepth = maxOf(maxDepth, braceDepth + bracketDepth + parenDepth)
                if (scriptOperandPending) scriptOperandPending = false
            }
            '}' -> {
                braceDepth--
                if (braceDepth < 0) return listOf(source)
            }
            '[' -> {
                bracketDepth++
                maxDepth = maxOf(maxDepth, braceDepth + bracketDepth + parenDepth)
                if (scriptOperandPending) scriptOperandPending = false
            }
            ']' -> {
                bracketDepth--
                if (bracketDepth < 0) return listOf(source)
            }
            '(' -> {
                parenDepth++
                maxDepth = maxOf(maxDepth, braceDepth + bracketDepth + parenDepth)
                if (scriptOperandPending) scriptOperandPending = false
            }
            ')' -> {
                parenDepth--
                if (parenDepth < 0) return listOf(source)
            }
            '^', '_' -> if (braceDepth == 0 && bracketDepth == 0 && parenDepth == 0) {
                scriptOperandPending = true
            }
            else -> {
                if (scriptOperandPending) {
                    scriptOperandPending = false
                } else if (
                    braceDepth == 0 && bracketDepth == 0 && parenDepth == 0 &&
                    ch in TOP_LEVEL_WRAP_OPERATORS
                ) {
                    val previous = source.getOrNull(index - 1)
                    val isContinuation =
                        ch == '=' && previous != null && previous in TOP_LEVEL_WRAP_OPERATORS
                    if (!isContinuation && index > 0) cuts += index
                }
            }
        }

        if (maxDepth > MAX_LATEX_WRAP_NESTING) return listOf(source)
        index++
    }

    if (braceDepth != 0 || bracketDepth != 0 || parenDepth != 0 || scriptOperandPending) {
        return listOf(source)
    }
    if (cuts.isEmpty()) return listOf(source)

    val segments = ArrayList<String>(cuts.size + 1)
    var start = 0
    cuts.distinct().sorted().forEach { cut ->
        if (cut > start) segments += source.substring(start, cut)
        start = cut
    }
    if (start < source.length) segments += source.substring(start)
    return segments.ifEmpty { listOf(source) }
}

private const val MAX_LATEX_WRAP_SCAN_CHARS = 32_768
private const val MAX_LATEX_WRAP_NESTING = 256
private val TOP_LEVEL_WRAP_OPERATORS = setOf('+', '-', '=', '<', '>', '!')
