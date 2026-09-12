package com.orchords.ai.core

import java.math.BigDecimal
import java.math.RoundingMode

data class OaiPricingProfile(
    val revision: String,
    val currency: String,
    val inputPerMillion: BigDecimal? = null,
    val cachedInputPerMillion: BigDecimal? = null,
    val outputPerMillion: BigDecimal? = null,
    val effectiveDate: String? = null,
    val sourceNote: String? = null,
) {
    init {
        require(revision.isNotBlank())
        require(currency.isNotBlank())
        require(inputPerMillion == null || inputPerMillion >= BigDecimal.ZERO)
        require(cachedInputPerMillion == null || cachedInputPerMillion >= BigDecimal.ZERO)
        require(outputPerMillion == null || outputPerMillion >= BigDecimal.ZERO)
    }
}

data class UsageCost(
    val profileRevision: String,
    val currency: String,
    val input: BigDecimal?,
    val cachedInput: BigDecimal?,
    val output: BigDecimal?,
) {
    val total: BigDecimal? = listOf(input, cachedInput, output)
        .takeUnless { it.any { value -> value == null } }
        ?.fold(BigDecimal.ZERO, BigDecimal::add)
}

fun TokenUsage.cost(profile: OaiPricingProfile): UsageCost? {
    val inputTokens = promptTokens - cachedTokens
    if (inputTokens < 0 || completionTokens < 0 || cachedTokens < 0) return null
    fun component(tokens: Int, rate: BigDecimal?): BigDecimal? =
        rate?.multiply(tokens.toBigDecimal())?.divide(BigDecimal(1_000_000), 12, RoundingMode.HALF_UP)
    return UsageCost(
        profileRevision = profile.revision,
        currency = profile.currency,
        input = component(inputTokens, profile.inputPerMillion),
        cachedInput = component(cachedTokens, profile.cachedInputPerMillion),
        output = component(completionTokens, profile.outputPerMillion),
    )
}
