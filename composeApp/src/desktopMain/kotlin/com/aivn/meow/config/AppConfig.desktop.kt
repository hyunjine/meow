package com.aivn.meow.config

import java.nio.file.Files
import java.nio.file.Path

actual fun loadAppConfig(): AppConfig? {
    val token = readToken() ?: return null
    val org = System.getenv("MEOW_GITHUB_ORG")?.takeIf { it.isNotBlank() } ?: "Team-AIVN"
    return AppConfig(org = org, token = token)
}

private fun readToken(): String? {
    System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it.trim() }

    val home = System.getProperty("user.home") ?: return null
    val candidates = listOf(
        Path.of(home, ".config", "meow", "token"),
        Path.of(home, ".meow", "token"),
    )
    for (path in candidates) {
        if (Files.exists(path)) {
            val content = runCatching { Files.readString(path) }.getOrNull()
            content?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return null
}
