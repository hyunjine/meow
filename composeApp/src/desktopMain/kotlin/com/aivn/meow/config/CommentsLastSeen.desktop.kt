package com.aivn.meow.config

import java.nio.file.Files
import java.nio.file.Path

private fun commentsLastSeenPath(): Path? =
    System.getProperty("user.home")?.let { Path.of(it, ".config", "meow", "comments_last_seen") }

actual fun loadCommentsLastSeen(): String? {
    val path = commentsLastSeenPath()?.takeIf { Files.exists(it) } ?: return null
    return runCatching { Files.readString(path) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
}

actual fun saveCommentsLastSeen(iso: String) {
    val path = commentsLastSeenPath() ?: return
    runCatching {
        Files.createDirectories(path.parent)
        Files.writeString(path, iso)
    }
}
