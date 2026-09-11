package com.orchords.orchordsai.data.ai.transformers

import com.orchords.orchordsai.data.model.PromptInjection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Request-scoped, allowlist-only variables for transient prompt-injection rendering. */
internal data class PromptInjectionRuntimeVariables private constructor(
    val instant: Instant,
    val zoneId: ZoneId,
) {
    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ssXXX")
        private val DATETIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun capture(clock: Clock = Clock.systemDefaultZone()): PromptInjectionRuntimeVariables =
            PromptInjectionRuntimeVariables(
                instant = clock.instant().truncatedTo(ChronoUnit.SECONDS),
                zoneId = clock.zone,
            )
    }

    private val zonedDateTime get() = instant.atZone(zoneId)

    /** Unknown placeholders remain literal; this renderer never evaluates arbitrary expressions. */
    fun render(content: String): String {
        if ("{{cur_" !in content) return content
        val now = zonedDateTime
        return content
            .replace("{{cur_datetime}}", DATETIME_FORMATTER.format(now))
            .replace("{{cur_date}}", DateTimeFormatter.ISO_LOCAL_DATE.format(now))
            .replace("{{cur_time}}", TIME_FORMATTER.format(now))
    }
}

/** Return an ephemeral copy so configured injection source text is never rewritten. */
internal fun PromptInjection.renderRuntimeVariables(
    variables: PromptInjectionRuntimeVariables,
): PromptInjection {
    val renderedContent = variables.render(content)
    if (renderedContent == content) return this
    return when (this) {
        is PromptInjection.ModeInjection -> copy(content = renderedContent)
        is PromptInjection.RegexInjection -> copy(content = renderedContent)
    }
}
