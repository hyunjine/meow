package com.aivn.meow.config

import com.aivn.meow.platform.IosFiles

/** #165 iOS 는 `~/.config/meow/<name>` 대신 앱 Documents/meow/<name> 을 쓴다. */
internal fun iosConfigPath(name: String): String = "${IosFiles.configDir}/$name"

actual fun readConfigFile(name: String): String? =
    runCatching { IosFiles.readText(iosConfigPath(name)) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

actual fun writeConfigFile(name: String, value: String) {
    runCatching { IosFiles.writeText(iosConfigPath(name), value) }
}

actual fun deleteConfigFile(name: String) {
    runCatching { IosFiles.delete(iosConfigPath(name)) }
}
