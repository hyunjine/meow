package com.aivn.meow.config

/**
 * #165 iOS 설정. 환경변수 · 홈 폴더가 없으니 앱 Documents/meow 의 파일만 본다.
 * 2단계(로그인)에서 앱 안 설정 화면 · 키체인으로 토큰을 넣기 전까지는 대개 토큰이 없어 null 이다.
 */
actual fun loadAppConfig(): AppConfig? {
    val token = readConfigFile("token") ?: return null
    val supabase = readConfigFile("supabase_url")?.let { url ->
        readConfigFile("supabase_anon")?.let { SupabaseConfig(url = url, anonKey = it) }
    }
    return AppConfig(token = token, supabase = supabase)
}
