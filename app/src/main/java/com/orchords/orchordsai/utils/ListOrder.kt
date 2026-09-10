package com.orchords.orchordsai.utils

/** Return a reordered copy while preserving every list element exactly once. */
internal fun <T> moveListItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    require(fromIndex in items.indices) { "fromIndex out of bounds: $fromIndex" }
    require(toIndex in items.indices) { "toIndex out of bounds: $toIndex" }
    if (fromIndex == toIndex) return items
    return items.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
