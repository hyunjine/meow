package com.aivn.meow.data

/**
 * #104 Claude GitHub App(`claude[bot]`) 이 작성한 댓글 · 리뷰인지.
 * GraphQL 은 login 이 `claude`, `__typename` 이 `Bot` 으로, REST · webhook 은 login 이 `claude[bot]` 으로 온다.
 * - login 이 `claude` 또는 `claude[bot]` 이면 봇 (대소문자 무시).
 * - [typename] 이 `Bot` 이고 login 이 `claude` 로 시작하면 봇.
 */
fun isClaudeBot(login: String?, typename: String? = null): Boolean {
    val name = login?.trim()?.lowercase() ?: return false
    if (name == "claude" || name == "claude[bot]") return true
    return typename == "Bot" && name.startsWith("claude")
}
