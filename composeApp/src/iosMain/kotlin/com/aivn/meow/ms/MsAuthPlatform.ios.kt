@file:OptIn(ExperimentalForeignApi::class)

package com.aivn.meow.ms

import com.aivn.meow.platform.Keychain
import com.aivn.meow.platform.sha256
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

/**
 * #165 아이폰 리디렉션 URI (MSAL 형식 `msauth.<bundle id>://auth`). Info.plist 의 CFBundleURLTypes 에 스킴을 등록했고,
 * Entra 앱 등록(Authentication › Mobile and desktop applications)에도 이 주소를 추가해야 로그인이 끝난다 (docs/IOS.md).
 */
const val IOS_REDIRECT_SCHEME = "msauth.com.aivn.meow"
const val IOS_REDIRECT_URI = "$IOS_REDIRECT_SCHEME://auth"

/**
 * #165 iOS: 루프백 서버 대신 ASWebAuthenticationSession(앱 안 Safari 로그인 창)으로 로그인 페이지를 열고,
 * [IOS_REDIRECT_URI] 로 돌아오는 첫 리디렉션의 쿼리를 돌려준다. 코루틴이 취소(시간 초과 등)되면 창을 닫는다.
 */
actual suspend fun runLoopbackAuthorization(buildAuthorizeUrl: (redirectUri: String) -> String): LoopbackResult =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val url = NSURL.URLWithString(buildAuthorizeUrl(IOS_REDIRECT_URI))
                ?: throw MsAuthException("로그인 주소를 만들지 못했어요")
            val session = ASWebAuthenticationSession(
                uRL = url,
                callbackURLScheme = IOS_REDIRECT_SCHEME,
                completionHandler = { callback, error ->
                    activeSession = null
                    when {
                        callback != null ->
                            cont.resume(LoopbackResult(redirectUri = IOS_REDIRECT_URI, params = queryParams(callback)))
                        error != null && error.domain == ASWebAuthenticationSessionErrorDomain &&
                            error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin ->
                            cont.resumeWithException(MsAuthException("로그인을 취소했어요"))
                        else -> cont.resumeWithException(
                            MsAuthException("로그인 창을 열지 못했어요: ${error?.localizedDescription ?: "알 수 없는 오류"}"),
                        )
                    }
                },
            )
            session.presentationContextProvider = presentationProvider
            // 회사 계정 SSO 쿠키를 Safari 와 나눠 쓴다(다음 로그인 때 계정 선택만 하면 되게).
            session.prefersEphemeralWebBrowserSession = false
            activeSession = session
            cont.invokeOnCancellation {
                // 취소는 아무 스레드에서나 올 수 있지만 cancel() 은 스레드 안전하다.
                session.cancel()
                activeSession = null
            }
            if (!session.start()) {
                activeSession = null
                cont.resumeWithException(MsAuthException("로그인 창을 열지 못했어요"))
            }
        }
    }

/** 진행 중인 로그인 창. 세션이 끝나기 전에 해제되지 않도록 붙잡아 둔다. */
private var activeSession: ASWebAuthenticationSession? = null

private fun queryParams(url: NSURL): Map<String, String> {
    val items = NSURLComponents(uRL = url, resolvingAgainstBaseURL = false).queryItems.orEmpty()
    return items.filterIsInstance<NSURLQueryItem>().associate { it.name to (it.value ?: "") }
}

/** 로그인 창을 띄울 창(앱의 key window). presentationContextProvider 는 weak 라 전역으로 둔다. */
private class PresentationProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        val windows = scenes.flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIWindow()
    }
}

private val presentationProvider = PresentationProvider()

actual fun defaultRefreshTokenStore(): RefreshTokenStore = IosKeychainRefreshTokenStore

/** #165 refresh token 을 iOS 키체인(서비스 `meow-ms-graph`, 계정 = UPN)에 둔다 — 데스크톱 macOS 키체인과 같은 이름. */
private object IosKeychainRefreshTokenStore : RefreshTokenStore {
    override fun load(): StoredRefreshToken? =
        Keychain.read(Keychain.SERVICE_MS_GRAPH)?.takeIf { it.secret.isNotEmpty() }
            ?.let { StoredRefreshToken(account = it.account, refreshToken = it.secret) }

    override fun save(account: String, refreshToken: String) {
        // 계정이 바뀌었을 수 있으니 서비스의 항목을 모두 지우고 새로 넣는다.
        Keychain.delete(Keychain.SERVICE_MS_GRAPH)
        Keychain.write(Keychain.SERVICE_MS_GRAPH, account, refreshToken)
    }

    override fun clear() = Keychain.delete(Keychain.SERVICE_MS_GRAPH)
}

actual val msLoginSupported: Boolean = true
