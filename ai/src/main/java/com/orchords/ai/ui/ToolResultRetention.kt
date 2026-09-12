package com.orchords.ai.ui

import com.orchords.ai.core.MessageRole

/**
 * Configuration for omitting older completed tool-result rounds from
 * outbound request context while preserving the visible conversation history.
 */
enum class ToolResultRetentionMode {
    /** Keep every tool-result round in outbound context. Default/backward-compatible. */
    ALL,

    /** Keep only the most recent N complete logical tool rounds. */
    LATEST_N,

    /** Omit all completed tool-result rounds older than the most recent one. */
    OMIT_OLDER_COMPLETED,
}

/**
 * A "complete logical tool round" is the contiguous suffix of executed tool calls on an
 * assistant message and the tool messages that respond to them. The first assistant message
 * that follows the latest user turn defines the boundary; a follow-up assistant message that
 * contains no pending tool calls ends the round.
 */
internal data class ToolRound(val startIndex: Int, val endIndex: Int, val isComplete: Boolean)

/**
 * Apply the configured retention mode to the message list. The transformation is read-only on
 * the input list; only structural omission occurs, never rewriting user-visible content. Non-tool
 * messages and incomplete (still-executing) rounds are never dropped.
 *
 * After `migrateToolMessages`, every "complete logical tool round" is represented as a single
 * assistant message that contains one or more `UIMessagePart.Tool` entries with both an input
 * and an output. Tool messages and any pending assistant tool calls without output are treated
 * as incomplete rounds and always preserved.
 */
fun List<UIMessage>.applyToolResultRetention(
    mode: ToolResultRetentionMode,
    latestNRounds: Int = Int.MAX_VALUE,
): List<UIMessage> {
    if (mode == ToolResultRetentionMode.ALL || isEmpty()) return this
    val rounds = mutableListOf<ToolRound>()
    var i = 0
    while (i < this.size) {
        val msg = this[i]
        if (msg.role != MessageRole.ASSISTANT) {
            i++
            continue
        }
        val executedWithOutput = msg.getTools().filter { it.isExecuted && it.output.isNotEmpty() }
        if (executedWithOutput.isEmpty()) {
            i++
            continue
        }
        val start = i
        var j = i + 1
        while (j < this.size && this[j].role == MessageRole.TOOL) j++
        rounds.add(ToolRound(startIndex = start, endIndex = j - 1, isComplete = true))
        i = j
    }
    val keepRounds: Set<Int>
    when (mode) {
        ToolResultRetentionMode.LATEST_N -> {
            val n = latestNRounds.coerceAtLeast(1)
            keepRounds = rounds.takeLast(n).map { it.startIndex }.toSet()
        }
        ToolResultRetentionMode.OMIT_OLDER_COMPLETED -> {
            keepRounds = setOfNotNull(rounds.lastOrNull()?.startIndex)
        }
        ToolResultRetentionMode.ALL -> return this
    }
    val keepIndices = mutableSetOf<Int>()
    val firstRoundStart = rounds.firstOrNull()?.startIndex
    val lastRoundEnd = rounds.lastOrNull()?.endIndex
    val firstKept = rounds.filter { it.startIndex in keepRounds }.minOfOrNull { it.startIndex }
    val lastKept = rounds.filter { it.startIndex in keepRounds }.maxOfOrNull { it.endIndex }
    // Always preserve the prefix before any round and the suffix after the last round.
    for (idx in this.indices) {
        val inPrefixBeforeFirstRound = firstRoundStart != null && idx < firstRoundStart
        val inSuffixAfterLastRound = lastRoundEnd != null && idx > lastRoundEnd
        if (inPrefixBeforeFirstRound || inSuffixAfterLastRound) keepIndices.add(idx)
    }
    for (round in rounds) {
        if (round.startIndex in keepRounds) {
            for (k in round.startIndex..round.endIndex) keepIndices.add(k)
        } else if (firstKept != null && round.endIndex >= firstKept) {
            // Older completed round adjacent to the kept region must be dropped (its tool result
            // is replaced or omitted), but the conversation must stay valid: skip these.
        } else if (lastKept != null && round.startIndex <= lastKept) {
            // Same on the leading side.
        }
    }
    // Any non-round message between kept rounds is preserved as-is.
    val keptRoundStarts = rounds.filter { it.startIndex in keepRounds }.map { it.startIndex }
    val keptRoundEnds = rounds.filter { it.startIndex in keepRounds }.map { it.endIndex }
    if (keptRoundStarts.isNotEmpty() && keptRoundEnds.isNotEmpty()) {
        val innerStart = keptRoundStarts.min()
        val innerEnd = keptRoundEnds.max()
        for (idx in innerStart..innerEnd) keepIndices.add(idx)
    }
    return this.filterIndexed { idx, _ -> idx in keepIndices }
}
