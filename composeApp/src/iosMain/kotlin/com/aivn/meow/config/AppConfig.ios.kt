package com.aivn.meow.config

import com.aivn.meow.platform.Keychain
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.stringWithContentsOfFile

private const val GITHUB_ACCOUNT = "token"
private const val SUPABASE_URL_ACCOUNT = "url"
private const val SUPABASE_ANON_ACCOUNT = "anon"

/**
 * #165 iOS 설정. 환경변수 · 홈 폴더가 없으니 설정 화면에서 넣은 키체인 값을 먼저 보고,
 * 없으면 빌드할 때 맥의 ~/.config/meow 에서 앱 번들 meow-config/ 로 복사해 둔 값,
 * 그것도 없으면 (개발용으로 넣어 둔) 앱 Documents/meow 의 token · supabase_url · supabase_anon 파일을 본다.
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

    /** #165 설정 화면에서 연결을 해제했으면 번들 값으로 되살리지 않도록 남겨 두는 표시. 새 값을 저장하면 지운다. */
    private const val GITHUB_DISABLED_KEY = "meow.github.disabled"
    private const val SUPABASE_DISABLED_KEY = "meow.supabase.disabled"

    private val defaults get() = NSUserDefaults.standardUserDefaults

    internal fun githubToken(): String? =
        Keychain.read(Keychain.SERVICE_GITHUB, GITHUB_ACCOUNT)?.secret?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackConfig("token", GITHUB_DISABLED_KEY)

    override val hasGithubToken: Boolean get() = githubToken() != null

    override val supabase: SupabaseConfig?
        get() {
            val url = Keychain.read(Keychain.SERVICE_SUPABASE, SUPABASE_URL_ACCOUNT)?.secret?.takeIf { it.isNotBlank() }
                ?: fallbackConfig("supabase_url", SUPABASE_DISABLED_KEY)
            val anon = Keychain.read(Keychain.SERVICE_SUPABASE, SUPABASE_ANON_ACCOUNT)?.secret?.takeIf { it.isNotBlank() }
                ?: fallbackConfig("supabase_anon", SUPABASE_DISABLED_KEY)
            return if (url != null && anon != null) SupabaseConfig(url = url.trim(), anonKey = anon.trim()) else null
        }

    override fun saveGithubToken(token: String) {
        Keychain.write(Keychain.SERVICE_GITHUB, GITHUB_ACCOUNT, token.trim())
        defaults.removeObjectForKey(GITHUB_DISABLED_KEY)
        onChanged()
    }

    override fun clearGithubToken() {
        Keychain.delete(Keychain.SERVICE_GITHUB)
        deleteConfigFile("token")
        defaults.setBool(true, GITHUB_DISABLED_KEY)
        onChanged()
    }

    override fun saveSupabase(config: SupabaseConfig) {
        Keychain.write(Keychain.SERVICE_SUPABASE, SUPABASE_URL_ACCOUNT, config.url)
        Keychain.write(Keychain.SERVICE_SUPABASE, SUPABASE_ANON_ACCOUNT, config.anonKey)
        defaults.removeObjectForKey(SUPABASE_DISABLED_KEY)
        onChanged()
    }

    override fun clearSupabase() {
        Keychain.delete(Keychain.SERVICE_SUPABASE)
        deleteConfigFile("supabase_url")
        deleteConfigFile("supabase_anon")
        defaults.setBool(true, SUPABASE_DISABLED_KEY)
        onChanged()
    }

    /** #165 키체인에 값이 없을 때: 번들 meow-config/[name] → Documents/meow/[name]. 연결을 해제했으면([disabledKey]) 둘 다 보지 않는다. */
    private fun fallbackConfig(name: String, disabledKey: String): String? {
        if (defaults.boolForKey(disabledKey)) return null
        return readBundledConfig(name) ?: readConfigFile(name)
    }

    /** #165 빌드 단계(Bundle Meow Config)가 앱 번들 meow-config/ 에 넣어 둔 값. 없으면 null. */
    @OptIn(ExperimentalForeignApi::class)
    private fun readBundledConfig(name: String): String? {
        val path = NSBundle.mainBundle.pathForResource(name, ofType = null, inDirectory = "meow-config") ?: return null
        return runCatching { NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
