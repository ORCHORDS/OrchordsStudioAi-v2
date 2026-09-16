package com.orchords.orchordsai.data.ai.planning

import com.orchords.ai.core.MessageRole
import com.orchords.orchordsai.data.model.InjectionPosition
import com.orchords.orchordsai.data.model.PromptInjection
import kotlin.uuid.Uuid

val PLANNING_MODE_ID: Uuid = Uuid.parse("164b9a03-828e-434e-8aa9-82c0e019a7fb")

val PLANNING_MODE_PROMPT: String = """
    Planning Mode is active. Plan before execution.

    Inspect the available conversation context, relevant files/repository state, and available tools before proposing changes. For current, external, or time-sensitive facts, use available search/retrieval tools and prefer current authoritative sources. Separate verified facts from assumptions and unknowns.

    Ask only material clarifying questions when the answer would change architecture, safety, scope, irreversible behavior, or a user-visible decision. Otherwise make the smallest explicit assumption needed and label it.

    Produce an ordered implementation plan that identifies the intended outcome, affected components/files when known, dependencies, risks, tests, verification steps, and any release/integration evidence that cannot be established from source alone.

    Do not autonomously write or edit files, run state-changing shell commands, send messages, deploy, commit, purchase, delete, or otherwise change external or durable state while Planning Mode is active. Read-only inspection and research are allowed. If a non-read-only tool is proposed, leave it for explicit user approval or execution mode.

    Do not claim that a command, test, search, edit, commit, deployment, message, or other tool action happened unless it actually happened. Do not expose hidden chain-of-thought or a private scratchpad; provide only concise reasoning summaries needed to understand the plan.

    Stop after the plan and finish with: Ready to execute.
""".trimIndent()

fun planningModeInjection(): PromptInjection.ModeInjection = PromptInjection.ModeInjection(
    id = PLANNING_MODE_ID,
    name = "Planning",
    enabled = true,
    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
    content = PLANNING_MODE_PROMPT,
    role = MessageRole.USER,
)

fun Set<Uuid>.isPlanningModeEnabled(): Boolean = PLANNING_MODE_ID in this

fun Set<Uuid>.withPlanningMode(enabled: Boolean): Set<Uuid> =
    if (enabled) this + PLANNING_MODE_ID else this - PLANNING_MODE_ID

/** Mirrors PromptInjectionTransformer's assistant/conversation selection semantics. */
fun isPlanningModeActive(
    assistantModeInjectionIds: Set<Uuid>,
    allowConversationPromptInjection: Boolean,
    conversationModeInjectionIds: Set<Uuid>,
): Boolean = if (allowConversationPromptInjection) {
    PLANNING_MODE_ID in conversationModeInjectionIds
} else {
    PLANNING_MODE_ID in assistantModeInjectionIds || PLANNING_MODE_ID in conversationModeInjectionIds
}

/**
 * Planning Mode is a first-party conversation control even when an assistant does not allow
 * arbitrary conversation prompt injections. This policy permits only adding/removing the
 * stable Planning Mode id; every unrelated mode and lorebook selection must remain unchanged.
 */
fun isPlanningOnlyConversationInjectionChange(
    currentModeInjectionIds: Set<Uuid>,
    currentLorebookIds: Set<Uuid>,
    requestedModeInjectionIds: Set<Uuid>,
    requestedLorebookIds: Set<Uuid>,
): Boolean {
    if (requestedLorebookIds != currentLorebookIds) return false
    return (requestedModeInjectionIds - PLANNING_MODE_ID) ==
        (currentModeInjectionIds - PLANNING_MODE_ID)
}
