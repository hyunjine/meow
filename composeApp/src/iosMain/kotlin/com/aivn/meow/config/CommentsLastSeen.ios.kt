package com.aivn.meow.config

private const val COMMENTS_LAST_SEEN_FILE = "comments_last_seen"

actual fun loadCommentsLastSeen(): String? = readConfigFile(COMMENTS_LAST_SEEN_FILE)

actual fun saveCommentsLastSeen(iso: String) = writeConfigFile(COMMENTS_LAST_SEEN_FILE, iso)
