package com.aivn.meow.config

import java.nio.file.Files
import java.nio.file.Path

private fun favoriteReposPath(): Path? =
    System.getProperty("user.home")?.let { Path.of(it, ".config", "meow", "favorite_repos") }

actual fun loadFavoriteRepos(): Set<String>? {
    val path = favoriteReposPath()?.takeIf { Files.exists(it) } ?: return null
    val text = runCatching { Files.readString(path) }.getOrNull() ?: return null
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

actual fun saveFavoriteRepos(repos: Set<String>) {
    val path = favoriteReposPath() ?: return
    runCatching {
        Files.createDirectories(path.parent)
        Files.writeString(path, repos.sorted().joinToString("") { "$it\n" })
    }
}
