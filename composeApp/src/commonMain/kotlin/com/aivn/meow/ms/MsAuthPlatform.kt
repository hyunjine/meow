package com.aivn.meow.ms

/** PKCE 쌍. [challenge] = BASE64URL(SHA-256([verifier])). */
data class Pkce(val verifier: String, val challenge: String)

expect fun createPkce(): Pkce

/** CSRF 방지용 `state` 등에 쓰는 URL-safe 난수 문자열. */
expect fun randomUrlSafeString(byteCount: Int = 32): String

/** 루프백 리디렉션으로 받은 결과. [params] 는 리디렉션 URL 의 쿼리 파라미터. */
data class LoopbackResult(val redirectUri: String, val params: Map<String, String>)

/**
 * 임의 포트의 `http://localhost:{port}` 루프백 서버를 띄우고, [buildAuthorizeUrl] 로 만든 로그인 페이지를
 * 기본 브라우저로 연 뒤 첫 리디렉션을 기다린다. 코루틴이 취소되면 서버도 닫는다.
 */
expect suspend fun runLoopbackAuthorization(buildAuthorizeUrl: (redirectUri: String) -> String): LoopbackResult

/** 저장된 refresh token 과 그 계정(UPN). */
data class StoredRefreshToken(val account: String, val refreshToken: String)

/** refresh token 보관소. 기본 구현은 macOS 키체인([defaultRefreshTokenStore]). */
interface RefreshTokenStore {
    fun load(): StoredRefreshToken?
    fun save(account: String, refreshToken: String)
    fun clear()
}

expect fun defaultRefreshTokenStore(): RefreshTokenStore
