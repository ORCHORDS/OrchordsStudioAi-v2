#!/usr/bin/env python3
"""Execute the actual share-URI gateway against real filesystem fixtures.

Context, FileProvider and managed-record lookup are host test doubles. These
checks prove pre-provider authorization, not Android URI grant delivery. A JDK,
kotlinc and a host with symbolic-link support are required. No network or Android
SDK is needed. Unsupported symlink creation fails explicitly rather than passing.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

FIXTURES = {
    "Context.kt": '''package android.content
import java.io.File
class Context(val filesDir: File, val cacheDir: File) {
    val packageName = "com.orchords.orchordsai.test"
}
''',
    "Uri.kt": '''package android.net
class Uri(val value: String)
''',
    "FileProvider.kt": '''package androidx.core.content
import android.content.Context
import android.net.Uri
import java.io.File
object FileProvider {
    var calls = 0
    fun getUriForFile(context: Context, authority: String, file: File): Uri {
        calls++
        return Uri("content://$authority/${file.name}")
    }
}
''',
    "Records.kt": '''package com.orchords.orchordsai.data.files
import java.io.File
object FileFolders { const val UPLOAD = "upload" }
data class Record(val folder: String, val file: File?)
class FilesManager {
    val records = mutableMapOf<String, Record>()
    suspend fun getByRelativePath(path: String): Record? = records[path]
    fun getFileOrNull(record: Record): File? = record.file
}
''',
    "ShareUriHarness.kt": r'''package com.orchords.orchordsai.data.files
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.*

private fun runSuspend(block: suspend () -> Unit) {
    var result: Result<Unit>? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<Unit>) { result = value }
    })
    checkNotNull(result) { "Host lookup fixture unexpectedly suspended" }.getOrThrow()
}

private fun rejects(block: () -> Unit) {
    val calls = FileProvider.calls
    var rejected = false
    try { block() } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected) { "unauthorized share accepted" }
    check(FileProvider.calls == calls) { "provider invoked before rejection" }
}

private class Fixture(val root: File) {
    val context = Context(root.resolve("files").apply { mkdirs() }, root.resolve("cache").apply { mkdirs() })
    val manager = FilesManager()
    fun file(parent: File, name: String = "fixture.txt") = parent.resolve(name).apply {
        parentFile.mkdirs(); writeText("private sentinel")
    }
    fun stage(kind: StagedShareRoot) = context.cacheDir.resolve(kind.directoryName)
    fun owned(candidate: File, folder: String = FileFolders.UPLOAD, recordFile: File? = candidate) {
        manager.records[candidate.canonicalFile.relativeTo(context.filesDir.canonicalFile).invariantSeparatorsPath] = Record(folder, recordFile)
    }
    fun share(candidate: File) = runSuspend { manager.createManagedUploadShareUri(context, candidate) }
}

fun main() {
    var passed = 0
    var failed = 0
    fun test(name: String, run: Fixture.() -> Unit) {
        val root = Files.createTempDirectory("share-uri-policy-")
        try {
            FileProvider.calls = 0
            Fixture(root.toFile()).run()
            passed++
            println("PASS: $name")
        } catch (error: Throwable) {
            failed++
            println("FAIL: $name: ${error.javaClass.simpleName}: ${error.message}")
        } finally {
            // Files.walk does not follow symlinks; never clean their targets.
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
    StagedShareRoot.values().forEach { kind ->
        test("$kind normal staged file allowed") {
            createStagedShareUri(context, file(stage(kind)), kind)
            check(FileProvider.calls == 1)
        }
        test("$kind missing file rejected") {
            rejects { createStagedShareUri(context, stage(kind).resolve("missing"), kind) }
        }
        test("$kind sibling prefix rejected") {
            val outside = file(context.cacheDir.resolve(kind.directoryName + "-private"))
            rejects { createStagedShareUri(context, outside, kind) }
        }
        test("$kind child symlink escape rejected") {
            val outside = file(context.filesDir.resolve("private"))
            val link = stage(kind).apply { mkdirs() }.resolve("linked.txt")
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            rejects { createStagedShareUri(context, link, kind) }
        }
        test("$kind redirected root rejected") {
            val privateFile = file(context.filesDir.resolve("private"))
            Files.createSymbolicLink(stage(kind).toPath(), privateFile.parentFile.toPath())
            rejects { createStagedShareUri(context, stage(kind).resolve(privateFile.name), kind) }
        }
    }
    test("managed registered upload allowed") {
        val candidate = file(context.filesDir.resolve("upload")); owned(candidate); share(candidate)
        check(FileProvider.calls == 1)
    }
    test("managed unregistered upload rejected") {
        val candidate = file(context.filesDir.resolve("upload"))
        rejects { share(candidate) }
    }
    test("managed wrong ownership class rejected") {
        val candidate = file(context.filesDir.resolve("upload")); owned(candidate, "private")
        rejects { share(candidate) }
    }
    test("managed stale missing file rejected") {
        val candidate = file(context.filesDir.resolve("upload")); owned(candidate); candidate.delete()
        rejects { share(candidate) }
    }
    test("managed mismatched record rejected") {
        val candidate = file(context.filesDir.resolve("upload")); owned(candidate, recordFile = file(context.filesDir, "other.txt"))
        rejects { share(candidate) }
    }
    test("managed unresolved record rejected") {
        val candidate = file(context.filesDir.resolve("upload")); owned(candidate, recordFile = null)
        rejects { share(candidate) }
    }
    test("managed child symlink escape rejected") {
        val privateFile = file(context.filesDir.resolve("private")); owned(privateFile)
        val link = context.filesDir.resolve("upload").apply { mkdirs() }.resolve("linked.txt")
        Files.createSymbolicLink(link.toPath(), privateFile.toPath())
        rejects { share(link) }
    }
    test("managed redirected root rejected even with a matching record") {
        val privateFile = file(context.filesDir.resolve("private")); owned(privateFile)
        val upload = context.filesDir.resolve("upload")
        Files.createSymbolicLink(upload.toPath(), privateFile.parentFile.toPath())
        rejects { share(upload.resolve(privateFile.name)) }
    }
    test("canonical Android parent alias remains usable") {
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias.toPath(), root.toPath())
        val aliased = Context(alias.resolve("files"), alias.resolve("cache"))
        val candidate = file(stage(StagedShareRoot.EXPORT))
        createStagedShareUri(aliased, candidate, StagedShareRoot.EXPORT)
        val upload = file(context.filesDir.resolve("upload")); owned(upload)
        runSuspend { manager.createManagedUploadShareUri(aliased, upload) }
        check(FileProvider.calls == 2)
    }
    println("RESULT: $passed passed, $failed failed")
    check(failed == 0) { "share URI policy regressions failed" }
}
''',
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    source = args.repo / "app/src/main/java/com/orchords/orchordsai/data/files/AuthorizedShareUris.kt"
    compiler, java = shutil.which("kotlinc"), shutil.which("java")
    if not source.is_file() or not compiler or not java:
        parser.error("Requires repository source, kotlinc and java")
    with tempfile.TemporaryDirectory(prefix="orchords-share-host-") as temp:
        directory = Path(temp)
        fixtures = []
        for name, text in FIXTURES.items():
            path = directory / name
            path.write_text(text, encoding="utf-8")
            fixtures.append(str(path))
        output = directory / "tests.jar"
        # compiler is resolved from the CI-controlled PATH and argv is executed with shell=False.
        subprocess.run(  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-tainted-env-args.dangerous-subprocess-use-tainted-env-args
            [compiler, str(source), *fixtures, "-nowarn", "-include-runtime", "-d", str(output)],
            check=True,
            timeout=120,
        )
        subprocess.run([java, "-jar", str(output)], check=True, timeout=60)


if __name__ == "__main__":
    main()
