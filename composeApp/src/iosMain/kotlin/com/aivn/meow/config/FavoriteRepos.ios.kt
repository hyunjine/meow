package com.aivn.meow.config

import com.aivn.meow.platform.IosFiles

actual fun loadRepoList(name: String): List<String>? {
    val text = runCatching { IosFiles.readText(iosConfigPath(name)) }.getOrNull() ?: return null
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

actual fun saveRepoList(name: String, repos: List<String>) {
    runCatching { IosFiles.writeText(iosConfigPath(name), repos.joinToString("") { "$it\n" }) }
}
