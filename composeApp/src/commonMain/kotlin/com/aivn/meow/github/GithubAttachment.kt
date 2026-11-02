package com.aivn.meow.github

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom

/**
 * #172 GitHub 이슈 · PR 에 끌어다 놓은 첨부 이미지 주소인지 판단한다.
 *
 * - `https://github.com/user-attachments/assets/<uuid>` (현재 형식)
 * - `https://github.com/<owner>/<repo>/assets/<id>/<uuid>` (예전 형식)
 *
 * 이 주소는 토큰 없이 받으면 404 이고, `Authorization: Bearer <token>` 을 붙이면 302 로
 * 짧게 서명된 S3 주소를 돌려준다. 토큰이 다른 곳으로 새지 않도록 https + github.com 호스트일 때만 true.
 */
fun isGithubAttachmentUrl(url: String): Boolean {
    val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return false
    if (parsed.protocol != URLProtocol.HTTPS) return false
    if (!parsed.host.equals(GITHUB_HOST, ignoreCase = true)) return false
    val segments = parsed.segments
    return when {
        segments.size >= 2 && segments[0] == "user-attachments" -> true
        segments.size >= 4 && segments[2] == "assets" -> true
        else -> false
    }
}

/**
 * #172 리다이렉트 응답의 `Location` 을 [base] 기준 절대 주소로 바꾼다. 실패하면 null.
 * 서명된 S3 주소는 토큰 없이 받아야 한다 (Authorization 을 같이 보내면 S3 가 400 을 돌려준다).
 */
fun resolveRedirectLocation(base: String, location: String?): String? {
    if (location.isNullOrBlank()) return null
    return runCatching { URLBuilder(base).takeFrom(location.trim()).buildString() }.getOrNull()
}

private const val GITHUB_HOST = "github.com"
