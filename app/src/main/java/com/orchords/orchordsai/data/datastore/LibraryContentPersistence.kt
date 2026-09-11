package com.orchords.orchordsai.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orchords.orchordsai.data.extensions.BuiltInLibrary
import com.orchords.orchordsai.data.extensions.appendMissingById
import com.orchords.orchordsai.data.extensions.filterRemovedBuiltIns
import com.orchords.orchordsai.data.extensions.recordRemovedBuiltIns
import com.orchords.orchordsai.data.model.Lorebook
import com.orchords.orchordsai.data.model.PromptInjection
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.serialization.Serializable

private val BUILT_IN_LIBRARY_LIFECYCLE = stringPreferencesKey("built_in_library_lifecycle")

@Serializable
private data class BuiltInLibraryLifecycleState(
    val installedCatalogVersion: Int = 0,
    val removedModeIds: Set<String> = emptySet(),
    val removedLorebookIds: Set<String> = emptySet(),
)

data class LibraryContentReceipt(val addedModes: Int, val addedLorebooks: Int)

private fun decodeLifecycle(preferences: Preferences): BuiltInLibraryLifecycleState =
    preferences[BUILT_IN_LIBRARY_LIFECYCLE]?.let { encoded ->
        JsonInstant.decodeFromString<BuiltInLibraryLifecycleState>(encoded)
    } ?: BuiltInLibraryLifecycleState()

private fun Preferences.writeLifecycle(state: BuiltInLibraryLifecycleState) {
    this[BUILT_IN_LIBRARY_LIFECYCLE] = JsonInstant.encodeToString(state)
}

/** Read/merge/write only library content and its bounded lifecycle metadata in one transaction. */
internal suspend fun appendLibraryContent(
    store: DataStore<Preferences>,
    modes: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
): LibraryContentReceipt {
    require(modes.map { it.id }.distinct().size == modes.size)
    require(lorebooks.map { it.id }.distinct().size == lorebooks.size)
    var receipt = LibraryContentReceipt(0, 0)
    store.edit { preferences ->
        // Corrupt existing content/lifecycle metadata fails the transaction; neither is treated as empty.
        val currentModes = JsonInstant.decodeFromString<List<PromptInjection.ModeInjection>>(
            preferences[SettingsStore.MODE_INJECTIONS] ?: "[]"
        )
        val currentBooks = JsonInstant.decodeFromString<List<Lorebook>>(
            preferences[SettingsStore.LOREBOOKS] ?: "[]"
        )
        val lifecycle = decodeLifecycle(preferences)
        val installableModes = filterRemovedBuiltIns(modes, lifecycle.removedModeIds) { it.id.toString() }
        val installableBooks = filterRemovedBuiltIns(lorebooks, lifecycle.removedLorebookIds) { it.id.toString() }
        val mergedModes = appendMissingById(currentModes, installableModes) { it.id.toString() }
        val mergedBooks = appendMissingById(currentBooks, installableBooks) { it.id.toString() }
        validateModeInjectionsForPersistence(mergedModes)
        validateLorebooksForPersistence(mergedBooks)
        preferences[SettingsStore.MODE_INJECTIONS] = JsonInstant.encodeToString(mergedModes)
        preferences[SettingsStore.LOREBOOKS] = JsonInstant.encodeToString(mergedBooks)
        preferences.writeLifecycle(
            lifecycle.copy(installedCatalogVersion = maxOf(lifecycle.installedCatalogVersion, BuiltInLibrary.catalog.version))
        )
        receipt = LibraryContentReceipt(mergedModes.size - currentModes.size, mergedBooks.size - currentBooks.size)
    }
    // Returning before edit commits, or optimistically changing SettingsFlow, would be false success.
    return receipt
}

/**
 * Mutate only the persisted mode list inside DataStore's serialized read-modify-write transaction.
 * Removing a currently installed built-in records user deletion intent so Install Missing will not
 * resurrect it. Custom records and edited built-ins that retain their stable ID are unaffected.
 */
internal suspend fun updateModeInjections(
    store: DataStore<Preferences>,
    transform: (List<PromptInjection.ModeInjection>) -> List<PromptInjection.ModeInjection>,
) {
    store.edit { preferences ->
        val current = JsonInstant.decodeFromString<List<PromptInjection.ModeInjection>>(
            preferences[SettingsStore.MODE_INJECTIONS] ?: "[]"
        )
        val updated = transform(current)
        require(updated.map { it.id }.distinct().size == updated.size) {
            "Mode injection IDs must be unique"
        }
        validateModeInjectionsForPersistence(updated)
        val lifecycle = decodeLifecycle(preferences)
        val removed = recordRemovedBuiltIns(
            currentIds = current.mapTo(mutableSetOf()) { it.id.toString() },
            updatedIds = updated.mapTo(mutableSetOf()) { it.id.toString() },
            builtInIds = BuiltInLibrary.catalog.modes.mapTo(mutableSetOf()) { it.id },
            existingRemovedIds = lifecycle.removedModeIds,
        )
        preferences[SettingsStore.MODE_INJECTIONS] = JsonInstant.encodeToString(updated)
        if (removed != lifecycle.removedModeIds) {
            preferences.writeLifecycle(lifecycle.copy(removedModeIds = removed))
        }
    }
}

/**
 * Mutate only the persisted lorebook list inside DataStore's serialized read-modify-write transaction.
 * Removing a currently installed built-in records user deletion intent so Install Missing will not
 * resurrect it. Custom records and edited built-ins that retain their stable ID are unaffected.
 */
internal suspend fun updateLorebooks(
    store: DataStore<Preferences>,
    transform: (List<Lorebook>) -> List<Lorebook>,
) {
    store.edit { preferences ->
        val current = JsonInstant.decodeFromString<List<Lorebook>>(
            preferences[SettingsStore.LOREBOOKS] ?: "[]"
        )
        val updated = transform(current)
        require(updated.map { it.id }.distinct().size == updated.size) {
            "Lorebook IDs must be unique"
        }
        validateLorebooksForPersistence(updated)
        val lifecycle = decodeLifecycle(preferences)
        val removed = recordRemovedBuiltIns(
            currentIds = current.mapTo(mutableSetOf()) { it.id.toString() },
            updatedIds = updated.mapTo(mutableSetOf()) { it.id.toString() },
            builtInIds = BuiltInLibrary.catalog.lorebooks.mapTo(mutableSetOf()) { it.id },
            existingRemovedIds = lifecycle.removedLorebookIds,
        )
        preferences[SettingsStore.LOREBOOKS] = JsonInstant.encodeToString(updated)
        if (removed != lifecycle.removedLorebookIds) {
            preferences.writeLifecycle(lifecycle.copy(removedLorebookIds = removed))
        }
    }
}
