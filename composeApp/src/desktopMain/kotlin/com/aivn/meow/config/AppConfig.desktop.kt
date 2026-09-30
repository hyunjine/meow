package com.aivn.meow.config

import java.nio.file.Files
import java.nio.file.Path

actual fun loadAppConfig(): AppConfig? {
    val token = readToken() ?: return null
    val org = System.getenv("MEOW_GITHUB_ORG")?.takeIf { it.isNotBlank() } ?: "Team-AIVN"
    return AppConfig(org = org, token = token, supabase = readSupabase())
}

private fun readSupabase(): SupabaseConfig? {
    val url = envOrFile("MEOW_SUPABASE_URL", "supabase_url") ?: return null
    val anon = envOrFile("MEOW_SUPABASE_ANON_KEY", "supabase_anon") ?: return null
    return SupabaseConfig(url = url, anonKey = anon)
}

private fun envOrFile(envName: String, fileName: String): String? {
    System.getenv(envName)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    val home = System.getProperty("user.home") ?: return null
    val candidates = listOf(
        Path.of(home, ".config", "meow", fileName),
        Path.of(home, ".meow", fileName),
    )
    for (path in candidates) {
        if (Files.exists(path)) {
            runCatching { Files.readString(path) }.getOrNull()
                ?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return null
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
