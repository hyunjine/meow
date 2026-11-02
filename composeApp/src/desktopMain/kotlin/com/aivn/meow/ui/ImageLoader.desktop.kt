package com.aivn.meow.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aivn.meow.config.readGithubToken
import com.aivn.meow.github.isGithubAttachmentUrl
import com.aivn.meow.github.resolveRedirectLocation
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

private val imageHttp by lazy { HttpClient(CIO) }

/**
 * #172 GitHub 첨부 이미지용 — 리다이렉트를 따라가지 않는다. 토큰을 붙인 요청이 302 로 돌려준
 * 서명된 S3 주소를 [imageHttp] 로(토큰 없이) 다시 받기 위함. 자동으로 따라가면 Authorization 이
 * 다른 호스트로 넘어갈 수 있고, S3 는 서명 주소 + Authorization 을 400 으로 거절한다.
 */
private val githubAttachmentHttp by lazy { HttpClient(CIO) { followRedirects = false } }

actual suspend fun loadImageBitmap(url: String): ImageBitmap? = try {
    val response = if (isGithubAttachmentUrl(url)) fetchGithubAttachment(url) else imageHttp.get(url)
    if (!response.status.isSuccess()) {
        null
    } else {
        val bytes = response.readRawBytes()
        withContext(Dispatchers.Default) { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/**
 * #172 `github.com/user-attachments/assets/...` 는 토큰 없이 받으면 404 라 깨진 이미지로 보였다.
 * github.com 에만 토큰을 붙여 요청하고, 돌려받은 리다이렉트 주소는 토큰 없이 받는다.
 */
private suspend fun fetchGithubAttachment(url: String): HttpResponse {
    val token = withContext(Dispatchers.IO) { readGithubToken() } ?: return imageHttp.get(url)
    val first = githubAttachmentHttp.get(url) { header(HttpHeaders.Authorization, "Bearer $token") }
    if (first.status.value !in 300..399) return first
    val location = resolveRedirectLocation(url, first.headers[HttpHeaders.Location]) ?: return first
    return imageHttp.get(location)
}
