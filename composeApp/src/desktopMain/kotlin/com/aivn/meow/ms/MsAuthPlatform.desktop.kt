package com.aivn.meow.ms

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import java.awt.Desktop
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

private val secureRandom = SecureRandom()
private val base64Url = Base64.getUrlEncoder().withoutPadding()

actual fun randomUrlSafeString(byteCount: Int): String =
    base64Url.encodeToString(ByteArray(byteCount).also { secureRandom.nextBytes(it) })

actual fun createPkce(): Pkce {
    val verifier = randomUrlSafeString(32) // 43자, RFC 7636 의 43~128자 범위
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Pkce(verifier = verifier, challenge = base64Url.encodeToString(digest))
}

actual suspend fun runLoopbackAuthorization(buildAuthorizeUrl: (redirectUri: String) -> String): LoopbackResult {
    val result = CompletableDeferred<Map<String, String>>()
    val handler = { exchange: HttpExchange ->
        val params = parseQuery(exchange.requestURI.rawQuery)
        // 파비콘 등 code/error 가 없는 요청은 무시하고 진짜 리디렉션만 받는다.
        val isRedirect = params.containsKey("code") || params.containsKey("error")
        val body = if (isRedirect) RESPONSE_HTML else ""
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(if (isRedirect) 200 else 404, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        if (isRedirect) result.complete(params)
    }

    // 브라우저가 localhost 를 ::1 로 먼저 풀 수 있어 IPv4 · IPv6 루프백 둘 다 같은 포트로 연다(외부 인터페이스는 열지 않음).
    val v4 = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    val port = v4.address.port
    val v6 = runCatching {
        HttpServer.create(InetSocketAddress(Inet6Address.getByName("::1"), port), 0)
    }.getOrNull()
    val servers = listOfNotNull(v4, v6)
    servers.forEach { server ->
        server.createContext("/") { handler(it) }
        server.start()
    }
    val redirectUri = "http://localhost:$port"
    try {
        openInBrowser(buildAuthorizeUrl(redirectUri))
        return LoopbackResult(redirectUri = redirectUri, params = result.await())
    } finally {
        servers.forEach { it.stop(0) }
    }
}

private fun parseQuery(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
        val key = pair.substringBefore('=')
        val value = pair.substringAfter('=', "")
        URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

private fun openInBrowser(url: String) {
    val viaDesktop = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (!viaDesktop) ProcessBuilder("open", url).start()
}

private const val RESPONSE_HTML = """<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>Meow</title></head>
<body style="font-family:-apple-system,sans-serif;text-align:center;padding-top:80px">
<h2>Meow 로그인 처리 완료</h2><p>이 탭은 닫아도 돼요.</p></body></html>"""

actual fun defaultRefreshTokenStore(): RefreshTokenStore = KeychainRefreshTokenStore()

/**
 * macOS 키체인(generic password, 서비스 [service]) 에 refresh token 을 둔다.
 * 저장은 `security -i` 의 표준 입력으로 명령을 넘겨 토큰이 프로세스 인자(ps)에 드러나지 않게 한다.
 */
class KeychainRefreshTokenStore(private val service: String = SERVICE) : RefreshTokenStore {
    override fun load(): StoredRefreshToken? {
        val attrs = run("security", "find-generic-password", "-s", service) ?: return null
        val account = ACCOUNT_REGEX.find(attrs)?.groupValues?.get(1) ?: return null
        val secret = run("security", "find-generic-password", "-s", service, "-w")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        return StoredRefreshToken(account = account, refreshToken = secret)
    }

    override fun save(account: String, refreshToken: String) {
        // 계정이 바뀌었을 수 있으니 기존 항목을 먼저 지운다(-U 는 같은 계정일 때만 갱신).
        clear()
        val command = "add-generic-password -U -s ${quote(service)} -a ${quote(account)} -w ${quote(refreshToken)}\n"
        val process = ProcessBuilder("security", "-i").redirectErrorStream(true).start()
        process.outputStream.use { it.write(command.toByteArray(StandardCharsets.UTF_8)) }
        process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
        // `security -i` 는 명령이 실패해도 0 으로 끝날 수 있어 다시 읽어 확인한다.
        check(load()?.refreshToken == refreshToken) { "키체인에 저장하지 못했어요" }
    }

    override fun clear() {
        // 같은 서비스로 여러 항목이 남아 있을 수 있어 없어질 때까지 지운다.
        repeat(5) {
            run("security", "delete-generic-password", "-s", service) ?: return
        }
    }

    private fun run(vararg command: String): String? {
        val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        return output.takeIf { process.exitValue() == 0 }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val SERVICE = "meow-ms-graph"
        private val ACCOUNT_REGEX = Regex("\"acct\"<blob>=\"([^\"]*)\"")
    }
}
