@file:OptIn(ExperimentalForeignApi::class)

package com.aivn.meow.ms

import com.aivn.meow.platform.sha256
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import kotlin.io.encoding.Base64

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

actual fun randomUrlSafeString(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, byteCount.convert(), it.addressOf(0)) }
    return base64Url.encode(bytes)
}

actual fun createPkce(): Pkce {
    val verifier = randomUrlSafeString(32)
    return Pkce(verifier = verifier, challenge = base64Url.encode(sha256(verifier.encodeToByteArray())))
}

/** #165 iOS 는 루프백 서버 대신 앱 리디렉션(ASWebAuthenticationSession)을 쓸 예정 — 2단계. */
actual suspend fun runLoopbackAuthorization(buildAuthorizeUrl: (redirectUri: String) -> String): LoopbackResult =
    throw UnsupportedOperationException("아이폰의 Microsoft 로그인은 2단계에서 지원할 예정이에요")

actual fun defaultRefreshTokenStore(): RefreshTokenStore = NoopRefreshTokenStore

/** #165 2단계(iOS 키체인 저장) 전까지 쓰는 빈 보관소. 저장된 토큰이 없으니 항상 '연결 안 됨' 으로 시작한다. */
private object NoopRefreshTokenStore : RefreshTokenStore {
    override fun load(): StoredRefreshToken? = null
    override fun save(account: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

actual val msLoginSupported: Boolean = false
