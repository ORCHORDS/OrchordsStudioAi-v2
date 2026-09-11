package com.orchords.orchordsai.ui.pages.extensions.workspace

internal data class WorkspaceDnsSnapshot(
    val nameservers: List<String>,
    val privateDnsActive: Boolean,
)

internal fun resolveWorkspaceDnsPolicy(snapshot: WorkspaceDnsSnapshot): List<String> {
    check(!snapshot.privateDnsActive) {
        "Workspace networking cannot preserve Android Private DNS yet"
    }
    return snapshot.nameservers
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
}
