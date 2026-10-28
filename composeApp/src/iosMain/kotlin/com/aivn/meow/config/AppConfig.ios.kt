package com.aivn.meow.config

import com.aivn.meow.platform.Keychain

private const val GITHUB_ACCOUNT = "token"
private const val SUPABASE_URL_ACCOUNT = "url"
private const val SUPABASE_ANON_ACCOUNT = "anon"

/**
 * #165 iOS 설정. 환경변수 · 홈 폴더가 없으니 설정 화면에서 넣은 키체인 값을 먼저 보고,
 * 없으면 (개발용으로 넣어 둔) 앱 Documents/meow 의 token · supabase_url · supabase_anon 파일을 본다.
 * GitHub 토큰이 없으면 null → 앱은 GitHub 연결 안내를 보여 준다.
 */
actual fun loadAppConfig(): AppConfig? {
    val token = IosAccountSettings.githubToken() ?: return null
    return AppConfig(token = token, supabase = IosAccountSettings.supabase)
}

/**
 * #165 아이폰 설정 화면의 저장소. GitHub 토큰은 키체인 `meow-github`, Supabase URL · anon key 는 `meow-supabase` 에 둔다.
 * 값이 바뀌면 [onChanged] 로 앱을 다시 띄운다(MainViewController 가 연결).
 */
object IosAccountSettings : AccountSettings {
    var onChanged: () -> Unit = {}

    internal fun githubToken(): String? =
        Keychain.read(Keychain.SERVICE_GITHUB, GITHUB_ACCOUNT)?.secret?.trim()?.takeIf { it.isNotEmpty() }
            ?: readConfigFile("token")

    override val hasGithubToken: Boolean get() = githubToken() != null

    override val supabase: SupabaseConfig?
        get() {
            val url = Keychain.read(Keychain.SERVICE_SUPABASE, SUPABASE_URL_ACCOUNT)?.secret?.takeIf { it.isNotBlank() }
                ?: readConfigFile("supabase_url")
            val anon = Keychain.read(Keychain.SERVICE_SUPABASE, SUPABASE_ANON_ACCOUNT)?.secret?.takeIf { it.isNotBlank() }
                ?: readConfigFile("supabase_anon")
            return if (url != null && anon != null) SupabaseConfig(url = url.trim(), anonKey = anon.trim()) else null
        }

    override fun saveGithubToken(token: String) {
        Keychain.write(Keychain.SERVICE_GITHUB, GITHUB_ACCOUNT, token.trim())
        onChanged()
    }

    override fun clearGithubToken() {
        Keychain.delete(Keychain.SERVICE_GITHUB)
        deleteConfigFile("token")
        onChanged()
    }

    override fun saveSupabase(config: SupabaseConfig) {
        Keychain.write(Keychain.SERVICE_SUPABASE, SUPABASE_URL_ACCOUNT, config.url)
        Keychain.write(Keychain.SERVICE_SUPABASE, SUPABASE_ANON_ACCOUNT, config.anonKey)
        onChanged()
    }

    override fun clearSupabase() {
        Keychain.delete(Keychain.SERVICE_SUPABASE)
        deleteConfigFile("supabase_url")
        deleteConfigFile("supabase_anon")
        onChanged()
    }
}
