package com.orchords.orchordsai.data.extensions

import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.files.SkillFrontmatterParser
import com.orchords.orchordsai.data.files.SkillManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class BuiltInLibraryInstallResult(
    val addedModes: Int,
    val addedLorebooks: Int,
    val addedSkills: Int,
    val preservedSkills: Int,
    val skippedRemovedSkills: Int,
    val failedSkills: List<String>,
)

/**
 * Idempotently makes bundled first-party content available without selecting it or authorizing an account.
 * Existing files are create-only/preserved. Startup respects user deletion tombstones; explicit repair can
 * restore removed built-ins after the user asks for it.
 */
class BuiltInLibraryInstaller(
    private val settingsStore: SettingsStore,
    private val skillManager: SkillManager,
) {
    suspend fun installMissing(
        restoreRemovedSkills: Boolean = false,
    ): BuiltInLibraryInstallResult = withContext(Dispatchers.IO) {
        val content = settingsStore.installBuiltInLibraryContent()
        val skillsRoot = skillManager.getSkillsDir()
        val catalog = BuiltInLibrary.catalog
        val catalogNames = catalog.skills.mapTo(linkedSetOf()) { it.name }
        val parsedBundledNames = skillManager.listSkills()
            .filter { it.origin == BUILT_IN_SKILL_ORIGIN }
            .mapTo(linkedSetOf()) { it.name }
        var lifecycle = bootstrapBuiltInSkillLifecycleState(
            catalogNames = catalogNames,
            existingBundledNames = parsedBundledNames,
            priorState = readBuiltInSkillLifecycle(skillsRoot),
        )
        if (restoreRemovedSkills && lifecycle.removedSkillNames.isNotEmpty()) {
            lifecycle = lifecycle.copy(removedSkillNames = emptySet())
        }
        val installableNames = installableBuiltInSkillNames(
            catalogNames = catalogNames,
            state = lifecycle,
            restoreRemoved = restoreRemovedSkills,
        )

        var added = 0
        var preserved = 0
        var skippedRemoved = 0
        val failed = mutableListOf<String>()
        for (skill in catalog.skills) {
            currentCoroutineContext().ensureActive()
            if (skill.name !in installableNames) {
                skippedRemoved++
                continue
            }
            try {
                val fileContent = skill.skillFile()
                val frontmatter = SkillFrontmatterParser.parse(fileContent)
                require(frontmatter["name"] == skill.name && frontmatter["description"] == skill.description) {
                    "Bundled skill metadata is invalid"
                }
                require(SkillFrontmatterParser.extractBody(fileContent).isNotBlank())
                when (installNewSkillContent(skillsRoot, skill.name, fileContent)) {
                    SkillInstallDisposition.INSTALLED -> added++
                    SkillInstallDisposition.ALREADY_PRESENT -> preserved++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Only a bounded, first-party identifier reaches the UI, never exception contents.
                failed += skill.name
            }
        }

        lifecycle = lifecycle.copy(
            catalogVersion = maxOf(lifecycle.catalogVersion, catalog.version),
            knownSkillNames = lifecycle.knownSkillNames + catalogNames,
        )
        writeBuiltInSkillLifecycle(skillsRoot, lifecycle)
        BuiltInLibraryInstallResult(
            addedModes = content.addedModes,
            addedLorebooks = content.addedLorebooks,
            addedSkills = added,
            preservedSkills = preserved,
            skippedRemovedSkills = skippedRemoved,
            failedSkills = failed,
        )
    }
}
