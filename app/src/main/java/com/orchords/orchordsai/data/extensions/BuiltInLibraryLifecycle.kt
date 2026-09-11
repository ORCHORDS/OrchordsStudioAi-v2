package com.orchords.orchordsai.data.extensions

internal fun <T> filterRemovedBuiltIns(
    candidates: List<T>,
    removedIds: Set<String>,
    id: (T) -> String,
): List<T> = candidates.filterNot { candidate -> id(candidate) in removedIds }

internal fun recordRemovedBuiltIns(
    currentIds: Set<String>,
    updatedIds: Set<String>,
    builtInIds: Set<String>,
    existingRemovedIds: Set<String>,
): Set<String> = existingRemovedIds + ((currentIds intersect builtInIds) - updatedIds)
