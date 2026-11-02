package com.aivn.meow.config

/**
 * #165 앱 안에서 GitHub 토큰 · Supabase(실시간) 설정을 넣고 지우는 곳 (아이폰 설정 화면).
 * 데스크톱은 지금처럼 `~/.config/meow/token` · gh 토큰 · 환경변수를 쓰므로 이 화면이 없다(App 에 null 을 넘긴다).
 *
 * 저장 · 삭제가 끝나면 구현이 앱을 새 설정으로 다시 띄운다(대시보드 · 실시간 구독을 새 토큰으로 다시 만든다).
 */
interface AccountSettings {
    /** 저장된 GitHub 토큰이 있는지. 토큰 자체는 화면에 돌려주지 않는다. */
    val hasGithubToken: Boolean

    /** 저장된 Supabase 설정. anon key 는 화면에 그대로 보여 주지 않는다. */
    val supabase: SupabaseConfig?

    /** 확인을 마친 GitHub 토큰을 보안 저장소(키체인)에 저장한다. 실패하면 예외. */
    fun saveGithubToken(token: String)

    fun clearGithubToken()

    fun saveSupabase(config: SupabaseConfig)

    fun clearSupabase()
}
