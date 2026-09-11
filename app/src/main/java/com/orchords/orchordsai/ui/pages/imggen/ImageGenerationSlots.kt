package com.orchords.orchordsai.ui.pages.imggen

/**
 * Tracks one in-flight/final value per requested image output.
 *
 * Indexed provider events always retain their provider slot. For providers that omit an index,
 * partials are conservatively assigned to the next empty slot so unrelated anonymous outputs are
 * never merged. Anonymous finals consume the first unresolved partial slot, then the first empty
 * slot. Once a slot is final, later partial/final duplicates are ignored.
 */
internal class ImageGenerationSlots<T>(
    private val outputCount: Int,
) {
    init {
        require(outputCount > 0)
    }

    internal data class Mutation<T>(
        val accepted: Boolean,
        val replacedPreview: T? = null,
    )

    private data class Slot<T>(
        val value: T,
        val isFinal: Boolean,
    )

    private val slots = mutableMapOf<Int, Slot<T>>()

    fun resolvePartialIndex(explicitIndex: Int?): Int? {
        if (explicitIndex != null) return explicitIndex.takeIf(::isValidIndex)
        return (0 until outputCount).firstOrNull { index -> index !in slots }
    }

    fun resolveFinalIndex(explicitIndex: Int?): Int? {
        if (explicitIndex != null) return explicitIndex.takeIf(::isValidIndex)
        return (0 until outputCount).firstOrNull { index -> slots[index]?.isFinal == false }
            ?: (0 until outputCount).firstOrNull { index -> index !in slots }
    }

    fun isFinal(index: Int): Boolean = slots[index]?.isFinal == true

    fun putPartial(index: Int, value: T): Mutation<T> {
        if (!isValidIndex(index)) return Mutation(accepted = false)
        val current = slots[index]
        if (current?.isFinal == true) return Mutation(accepted = false)
        slots[index] = Slot(value = value, isFinal = false)
        return Mutation(
            accepted = true,
            replacedPreview = current?.value,
        )
    }

    fun putFinal(index: Int, value: T): Mutation<T> {
        if (!isValidIndex(index)) return Mutation(accepted = false)
        val current = slots[index]
        if (current?.isFinal == true) return Mutation(accepted = false)
        slots[index] = Slot(value = value, isFinal = true)
        return Mutation(
            accepted = true,
            replacedPreview = current?.takeUnless { it.isFinal }?.value,
        )
    }

    fun snapshot(): List<T> = slots.toSortedMap().values.map { it.value }

    fun drainPartials(): List<T> {
        val partialIndexes = slots
            .filterValues { slot -> !slot.isFinal }
            .keys
            .sorted()
        return partialIndexes.mapNotNull { index -> slots.remove(index)?.value }
    }

    private fun isValidIndex(index: Int): Boolean = index in 0 until outputCount
}
