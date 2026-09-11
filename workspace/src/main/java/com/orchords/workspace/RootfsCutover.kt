package com.orchords.workspace

import java.io.File

internal fun finalizeRootfsInstall(
    stagingDir: File,
    linuxDir: File,
    patch: (File) -> Unit,
    move: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
) {
    patch(stagingDir)

    val parent = requireNotNull(linuxDir.parentFile) { "Rootfs target has no parent" }
    val backupDir = File(parent, "${linuxDir.name}.previous")
    require(!backupDir.exists()) { "Previous rootfs backup already exists" }

    if (!linuxDir.exists()) {
        require(move(stagingDir, linuxDir)) { "Failed to move rootfs into workspace" }
        return
    }

    require(move(linuxDir, backupDir)) { "Failed to stage previous rootfs for replacement" }
    var installed = false
    try {
        require(move(stagingDir, linuxDir)) { "Failed to move rootfs into workspace" }
        installed = true
    } finally {
        if (!installed && !linuxDir.exists() && backupDir.exists()) {
            require(move(backupDir, linuxDir)) { "Failed to restore previous rootfs after cutover failure" }
        }
    }

    if (backupDir.exists()) backupDir.deleteRecursively()
}
