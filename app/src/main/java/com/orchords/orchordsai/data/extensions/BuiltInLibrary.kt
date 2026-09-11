package com.orchords.orchordsai.data.extensions

const val BUILT_IN_SKILL_ORIGIN = "built-in/orchords"
const val BUILT_IN_SKILL_VERSION = "2"

/** Versioned first-party definitions, deliberately independent of Android and network clients. */
data class LibraryMode(val id: String, val name: String, val body: String)
data class LibraryEntry(val id: String, val name: String, val keywords: List<String>, val body: String)
data class LibraryLorebook(val id: String, val name: String, val description: String, val entries: List<LibraryEntry>)
data class LibrarySkill(
    val name: String,
    val description: String,
    val body: String,
    val origin: String = BUILT_IN_SKILL_ORIGIN,
    val version: String = BUILT_IN_SKILL_VERSION,
) {
    fun skillFile(): String {
        require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(name) && name.length <= 64)
        require(description.isNotBlank() && description.length <= 1024)
        require(origin.isNotBlank() && version.isNotBlank())
        val quotedDescription = description.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
        return "---\nname: $name\ndescription: \"$quotedDescription\"\nmetadata:\n  origin: \"$origin\"\n  version: \"$version\"\n---\n\n$body\n"
    }
}
data class LibraryCatalog(
    val version: Int,
    val modes: List<LibraryMode>,
    val lorebooks: List<LibraryLorebook>,
    val skills: List<LibrarySkill>,
)
object BuiltInLibrary {
    val catalog: LibraryCatalog = builtInLibraryCatalog()
}

/** Append missing identities only; existing ordering, duplicates and user edits are untouched. */
fun <T> appendMissingById(current: List<T>, builtIns: List<T>, id: (T) -> String): List<T> {
    val seen = current.mapTo(HashSet()) { id(it) }
    return current + builtIns.filter { seen.add(id(it)) }
}
