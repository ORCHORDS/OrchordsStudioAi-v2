package com.orchords.orchordsai.data.extensions

import com.orchords.orchordsai.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable

private const val BUILT_IN_SKILL_LIFECYCLE_FILE = ".orchords-built-in-skills.json"
private const val MAX_LIFECYCLE_BYTES = 64 * 1024
private val builtInSkillLifecycleLock = Any()

@Serializable
internal data class BuiltInSkillLifecycleState(
    val catalogVersion: Int = 0,
    val knownSkillNames: Set<String> = emptySet(),
    val removedSkillNames: Set<String> = emptySet(),
)

internal fun bootstrapBuiltInSkillLifecycleState(
    catalogNames: Set<String>,
    existingBundledNames: Set<String>,
    priorState: BuiltInSkillLifecycleState?,
): BuiltInSkillLifecycleState {
    if (priorState != null) return priorState
    if (existingBundledNames.isEmpty()) return BuiltInSkillLifecycleState()
    val knownExisting = existingBundledNames.intersect(catalogNames)
    return BuiltInSkillLifecycleState(
        knownSkillNames = catalogNames,
        removedSkillNames = catalogNames - knownExisting,
    )
}

internal fun installableBuiltInSkillNames(
    catalogNames: Set<String>,
    state: BuiltInSkillLifecycleState,
    restoreRemoved: Boolean,
): Set<String> = if (restoreRemoved) catalogNames else catalogNames - state.removedSkillNames

internal fun recordBuiltInSkillRemoval(
    state: BuiltInSkillLifecycleState,
    name: String,
    catalogNames: Set<String>,
): BuiltInSkillLifecycleState {
    if (name !in catalogNames) return state
    return state.copy(
        knownSkillNames = state.knownSkillNames + name,
        removedSkillNames = state.removedSkillNames + name,
    )
}

internal fun readBuiltInSkillLifecycle(skillsRoot: File): BuiltInSkillLifecycleState? =
    synchronized(builtInSkillLifecycleLock) {
        val file = skillsRoot.resolve(BUILT_IN_SKILL_LIFECYCLE_FILE)
        if (!file.exists()) return@synchronized null
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Invalid built-in skill lifecycle file" }
        require(file.length() in 1..MAX_LIFECYCLE_BYTES.toLong()) { "Invalid built-in skill lifecycle size" }
        JsonInstant.decodeFromString<BuiltInSkillLifecycleState>(file.readText(Charsets.UTF_8))
    }

internal fun writeBuiltInSkillLifecycle(skillsRoot: File, state: BuiltInSkillLifecycleState) =
    synchronized(builtInSkillLifecycleLock) {
        require(!Files.isSymbolicLink(skillsRoot.toPath())) { "Skill root must not be a symlink" }
        check(skillsRoot.isDirectory || skillsRoot.mkdirs()) { "Skill storage is unavailable" }
        val root = skillsRoot.canonicalFile
        val target = root.resolve(BUILT_IN_SKILL_LIFECYCLE_FILE)
        val bytes = JsonInstant.encodeToString(state).toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_LIFECYCLE_BYTES)
        val staging = Files.createTempFile(root.toPath(), ".orchords-built-in-skills-", ".tmp").toFile()
        try {
            FileOutputStream(staging).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    staging.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (staging.exists()) staging.delete()
        }
    }

internal fun tombstoneBuiltInSkill(skillsRoot: File, name: String, catalogNames: Set<String>) {
    if (name !in catalogNames) return
    val current = readBuiltInSkillLifecycle(skillsRoot)
        ?: BuiltInSkillLifecycleState(knownSkillNames = catalogNames)
    writeBuiltInSkillLifecycle(
        skillsRoot,
        recordBuiltInSkillRemoval(current, name, catalogNames),
    )
}

internal fun clearBuiltInSkillTombstone(skillsRoot: File, name: String) {
    val current = readBuiltInSkillLifecycle(skillsRoot) ?: return
    if (name !in current.removedSkillNames) return
    writeBuiltInSkillLifecycle(
        skillsRoot,
        current.copy(removedSkillNames = current.removedSkillNames - name),
    )
}
