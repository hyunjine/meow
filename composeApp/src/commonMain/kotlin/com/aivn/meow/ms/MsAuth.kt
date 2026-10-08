package com.aivn.meow.ms

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Microsoft 계정 연결 상태. */
sealed interface MsAuthState {
    data object NotConnected : MsAuthState

    /** 브라우저 로그인 · 저장된 토큰 복원 진행 중. */
    data object Connecting : MsAuthState

    data class Connected(val upn: String, val displayName: String) : MsAuthState

    data class Error(val message: String) : MsAuthState
}

/** Graph 호출에 쓸 access token 을 주는 쪽. [forceRefresh] 면 캐시를 버리고 새로 받는다. */
fun interface MsTokenProvider {
    suspend fun accessToken(forceRefresh: Boolean): String
}

/** 로그인이 필요(연결 안 됨 · refresh token 만료/폐기). */
class MsNotConnectedException(message: String) : Exception(message)

/**
 * Authorization Code + PKCE 로 Microsoft 계정에 로그인한다.
 * refresh token 은 [store](기본: macOS 키체인) 에, access token 은 메모리에만 둔다.
 */
class MsAuth(
    engineFactory: HttpClientEngineFactory<*>,
    private val config: MsAppConfig = MsAppConfig.load(),
    private val store: RefreshTokenStore = defaultRefreshTokenStore(),
) : MsTokenProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(engineFactory)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<MsAuthState>(MsAuthState.NotConnected)
    val state: StateFlow<MsAuthState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var expiresAt: Instant = Instant.DISTANT_PAST
    private var refreshToken: String? = null
    private var account: String? = null

    /** 보관소에 마지막으로 저장(또는 읽은) 계정 · refresh token — 같은 값을 다시 쓰지 않으려고 둔다. */
    private var persisted: StoredRefreshToken? = null

    /** 앱 시작 시: 키체인의 refresh token 으로 조용히 다시 연결한다. 저장된 게 없으면 NotConnected. */
    suspend fun restore(): MsAuthState {
        val stored = withContext(Dispatchers.Default) { store.load() }
        if (stored == null) {
            _state.value = MsAuthState.NotConnected
            return _state.value
        }
        _state.value = MsAuthState.Connecting
        mutex.withLock {
            refreshToken = stored.refreshToken
            account = stored.account
            persisted = stored
        }
        return connectWithCurrentTokens()
    }

    /** 기본 브라우저로 로그인 페이지를 열고 루프백으로 코드를 받아 토큰을 교환한다. 5분 안에 끝나지 않으면 실패. */
    suspend fun signIn(): MsAuthState {
        _state.value = MsAuthState.Connecting
        return try {
            val pkce = createPkce()
            val expectedState = randomUrlSafeString()
            val result = withTimeout(LOGIN_TIMEOUT) {
                runLoopbackAuthorization { redirectUri -> authorizeUrl(redirectUri, pkce, expectedState) }
            }
            val params = result.params
            params["error"]?.let { error ->
                throw MsAuthException("로그인 실패: $error ${params["error_description"].orEmpty()}".trim())
            }
            if (params["state"] != expectedState) throw MsAuthException("로그인 응답의 state 가 맞지 않아요")
            val code = params["code"] ?: throw MsAuthException("로그인 응답에 code 가 없어요")
            val token = requestToken(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to result.redirectUri,
                "code_verifier" to pkce.verifier,
            )
            mutex.withLock { applyToken(token) }
            connectWithCurrentTokens()
        } catch (e: TimeoutCancellationException) {
            MsAuthState.Error("로그인 시간이 초과됐어요. 다시 시도해 주세요").also { _state.value = it }
        } catch (e: CancellationException) {
            _state.value = MsAuthState.NotConnected
            throw e
        } catch (e: Exception) {
            MsAuthState.Error(e.message ?: e.toString()).also { _state.value = it }
        }
    }

    /** 로그아웃: 키체인 항목과 메모리 토큰을 지운다. */
    suspend fun signOut() {
        mutex.withLock {
            accessToken = null
            refreshToken = null
            account = null
            persisted = null
            expiresAt = Instant.DISTANT_PAST
        }
        withContext(Dispatchers.Default) { store.clear() }
        _state.value = MsAuthState.NotConnected
    }

    /** 유효한 access token. 만료 [REFRESH_MARGIN] 전이면 refresh token 으로 갱신한다. */
    override suspend fun accessToken(forceRefresh: Boolean): String = mutex.withLock {
        val cached = accessToken
        if (!forceRefresh && cached != null && Clock.System.now() < expiresAt - REFRESH_MARGIN) return cached
        refreshLocked()
    }

    fun close() = http.close()

    private suspend fun connectWithCurrentTokens(): MsAuthState = try {
        val token = accessToken(forceRefresh = accessToken == null)
        val me = http.get("$GRAPH/me?\$select=userPrincipalName,displayName") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val text = me.bodyAsText()
        if (!me.status.isSuccess()) throw MsAuthException("내 정보 조회 실패 (HTTP ${me.status.value})")
        val profile = json.decodeFromString(MeDto.serializer(), text)
        val upn = profile.userPrincipalName ?: account.orEmpty()
        mutex.withLock {
            account = upn
            persistLocked()
        }
        MsAuthState.Connected(upn = upn, displayName = profile.displayName ?: upn).also { _state.value = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: MsNotConnectedException) {
        MsAuthState.NotConnected.also { _state.value = it }
    } catch (e: Exception) {
        MsAuthState.Error(e.message ?: e.toString()).also { _state.value = it }
    }

    /** [mutex] 를 잡은 상태에서 호출. */
    private suspend fun refreshLocked(): String {
        val rt = refreshToken ?: throw MsNotConnectedException("Microsoft 계정이 연결되어 있지 않아요")
        val token = try {
            requestToken("grant_type" to "refresh_token", "refresh_token" to rt)
        } catch (e: MsTokenRejectedException) {
            // invalid_grant: 만료 · 폐기 · 비밀번호 변경 등 — 다시 로그인해야 한다.
            accessToken = null
            refreshToken = null
            persisted = null
            withContext(Dispatchers.Default) { store.clear() }
            _state.value = MsAuthState.NotConnected
            throw MsNotConnectedException("Microsoft 로그인이 만료됐어요. 다시 로그인해 주세요 (${e.message})")
        }
        applyToken(token)
        // 회전된 refresh token 저장 실패는 다음 갱신 때 다시 시도한다(이번 access token 은 유효).
        if (account != null) runCatching { persistLocked() }
        return token.accessToken
    }

    /** [mutex] 를 잡은 상태에서 호출. 현재 계정 · refresh token 이 보관소와 다르면 저장한다. */
    private suspend fun persistLocked() {
        val current = StoredRefreshToken(account ?: return, refreshToken ?: return)
        if (current == persisted) return
        withContext(Dispatchers.Default) { store.save(current.account, current.refreshToken) }
        persisted = current
    }

    private fun applyToken(token: TokenResponse) {
        accessToken = token.accessToken
        expiresAt = Clock.System.now() + token.expiresIn.seconds
        token.refreshToken?.let { refreshToken = it }
    }

    private suspend fun requestToken(vararg fields: Pair<String, String>): TokenResponse {
        val response = http.submitForm(
            url = "${config.authority}/token",
            formParameters = parameters {
                append("client_id", config.clientId)
                append("scope", config.scopes.joinToString(" "))
                fields.forEach { (k, v) -> append(k, v) }
            },
        )
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val err = runCatching { json.decodeFromString(TokenError.serializer(), text) }.getOrNull()
            val code = err?.error ?: "http_${response.status.value}"
            // 설명 첫 줄(AADSTS 코드)만 남긴다 — 상관 ID · 타임스탬프는 버림.
            val desc = err?.errorDescription?.lineSequence()?.firstOrNull().orEmpty()
            if (code == "invalid_grant" || code == "interaction_required") throw MsTokenRejectedException("$code $desc".trim())
            throw MsAuthException("토큰 요청 실패: $code $desc".trim())
        }
        return json.decodeFromString(TokenResponse.serializer(), text)
    }

    private fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String): String =
        URLBuilder("${config.authority}/authorize").apply {
            parameters.append("client_id", config.clientId)
            parameters.append("response_type", "code")
            parameters.append("response_mode", "query")
            parameters.append("redirect_uri", redirectUri)
            parameters.append("scope", config.scopes.joinToString(" "))
            parameters.append("state", state)
            parameters.append("code_challenge", pkce.challenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("prompt", "select_account")
        }.buildString()

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    @Serializable
    private data class TokenError(
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    @Serializable
    private data class MeDto(val userPrincipalName: String? = null, val displayName: String? = null)

    companion object {
        private const val GRAPH = "https://graph.microsoft.com/v1.0"
        private val LOGIN_TIMEOUT = 5.minutes
        private val REFRESH_MARGIN = 2.minutes
    }
}

class MsAuthException(message: String) : Exception(message)

private class MsTokenRejectedException(message: String) : Exception(message)
