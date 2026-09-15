package com.aivn.meow.config

data class AppConfig(
    val org: String = "Team-AIVN",
    val token: String,
)

expect fun loadAppConfig(): AppConfig?
