package com.aivn.meow.config

import java.nio.file.Files
import java.nio.file.Path

private fun configPath(name: String): Path? =
    System.getProperty("user.home")?.let { Path.of(it, ".config", "meow", name) }

actual fun readConfigFile(name: String): String? {
    val path = configPath(name)?.takeIf { Files.exists(it) } ?: return null
    return runCatching { Files.readString(path) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

actual fun writeConfigFile(name: String, value: String) {
    val path = configPath(name) ?: return
    runCatching {
        Files.createDirectories(path.parent)
        Files.writeString(path, value)
    }
}

actual fun deleteConfigFile(name: String) {
    val path = configPath(name) ?: return
    runCatching { Files.deleteIfExists(path) }
}
