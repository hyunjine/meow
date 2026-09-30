package com.aivn.meow.config

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
)

data class AppConfig(
    val org: String = "Team-AIVN",
    val token: String,
    val supabase: SupabaseConfig? = null,
)

expect fun loadAppConfig(): AppConfig?
