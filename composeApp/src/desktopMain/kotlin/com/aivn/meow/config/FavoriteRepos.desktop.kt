package com.aivn.meow.config

import java.nio.file.Files
import java.nio.file.Path

private fun repoListPath(name: String): Path? =
    System.getProperty("user.home")?.let { Path.of(it, ".config", "meow", name) }

actual fun loadRepoList(name: String): List<String>? = repoListPath(name)?.let(::readRepoListFile)

actual fun saveRepoList(name: String, repos: List<String>) {
    repoListPath(name)?.let { writeRepoListFile(it, repos) }
}

/** [path] 의 비어 있지 않은 줄 (앞뒤 공백 제거). 파일이 없거나 읽지 못하면 null. */
internal fun readRepoListFile(path: Path): List<String>? {
    if (!Files.exists(path)) return null
    val text = runCatching { Files.readString(path) }.getOrNull() ?: return null
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

internal fun writeRepoListFile(path: Path, repos: List<String>) {
    runCatching {
        Files.createDirectories(path.parent)
        Files.writeString(path, repos.joinToString("") { "$it\n" })
    }
}
