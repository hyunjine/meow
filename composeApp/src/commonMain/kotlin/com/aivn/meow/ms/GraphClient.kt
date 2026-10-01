package com.aivn.meow.ms

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Microsoft Graph v1.0 클라이언트. 요청마다 [tokens] 의 access token 을 Bearer 로 붙이고,
 * 401 이면 토큰을 강제 갱신해 한 번만 다시 시도한다.
 */
class GraphClient(
    private val tokens: MsTokenProvider,
    engineFactory: HttpClientEngineFactory<*>,
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = HttpClient(engineFactory) {
        // 리디렉션은 따라가지 않는다 — 다운로드는 사전 인증 URL 로 직접 받는다(Authorization 헤더 유출 방지).
        followRedirects = false
        expectSuccess = false
    }

    /** [pathOrUrl] 은 `/me/...` 같은 v1.0 상대 경로 또는 `@odata.nextLink` 같은 절대 URL. */
    suspend fun <T> get(pathOrUrl: String, serializer: KSerializer<T>): T {
        val response = authorized(HttpMethod.Get, pathOrUrl)
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw graphFailure(response.status.value, text)
        return json.decodeFromString(serializer, text)
    }

    /** `value` 배열을 `@odata.nextLink` 를 따라가며 [maxPages] 쪽까지 모은다. */
    suspend fun <T> getAll(path: String, serializer: KSerializer<T>, maxPages: Int = 10): List<T> {
        val out = mutableListOf<T>()
        var next: String? = path
        var pages = 0
        while (next != null && pages < maxPages) {
            val page = get(next, GraphPage.serializer(serializer))
            out += page.value
            next = page.nextLink
            pages++
        }
        return out
    }

    /** 파일 메타데이터(eTag · 사전 인증 다운로드 URL) 를 읽고 내용을 받는다. */
    suspend fun downloadItem(driveId: String, itemId: String): DriveFile {
        // \$select 를 쓰면 @microsoft.graph.downloadUrl 이 빠져서 전체 메타데이터를 받는다.
        val meta = get("/drives/$driveId/items/$itemId", DriveItemDownload.serializer())
        val url = meta.downloadUrl ?: throw GraphApiException(0, "noDownloadUrl", "다운로드 URL 이 없어요: ${meta.name}")
        // downloadUrl 은 짧게 유효한 사전 인증 URL — Authorization 헤더를 붙이지 않는다.
        val response = http.get(url)
        if (!response.status.isSuccess()) throw graphFailure(response.status.value, response.bodyAsText())
        return DriveFile(bytes = response.readRawBytes(), eTag = meta.eTag ?: "", name = meta.name.orEmpty())
    }

    /**
     * 내용을 [eTag] 조건부로 덮어쓴다(`If-Match`). 그 사이 다른 사람이 문서를 고쳤으면(412)
     * [DocumentChangedException], 편집 잠금 중(423)이면 [DocumentLockedException].
     */
    suspend fun uploadIfMatch(driveId: String, itemId: String, bytes: ByteArray, eTag: String): DriveItemRef {
        val response = authorized(HttpMethod.Put, "/drives/$driveId/items/$itemId/content") {
            header(HttpHeaders.IfMatch, eTag)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        val text = response.bodyAsText()
        return when (response.status.value) {
            in 200..299 -> json.decodeFromString(DriveItemRef.serializer(), text)
            412 -> throw DocumentChangedException()
            423 -> throw DocumentLockedException()
            else -> throw graphFailure(response.status.value, text)
        }
    }

    fun close() = http.close()

    private suspend fun authorized(
        method: HttpMethod,
        pathOrUrl: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val url = if (pathOrUrl.startsWith("https://")) pathOrUrl else "$BASE$pathOrUrl"
        suspend fun send(token: String) = http.request(url) {
            this.method = method
            header(HttpHeaders.Authorization, "Bearer $token")
            block()
        }
        val first = send(tokens.accessToken(forceRefresh = false))
        if (first.status.value != 401) return first
        first.bodyAsText() // 응답 소비
        return send(tokens.accessToken(forceRefresh = true))
    }

    private fun graphFailure(status: Int, body: String): GraphApiException {
        val err = runCatching { json.decodeFromString(GraphErrorEnvelope.serializer(), body).error }.getOrNull()
        return GraphApiException(status, err?.code ?: "http_$status", err?.message ?: "Graph HTTP $status")
    }

    companion object {
        const val BASE = "https://graph.microsoft.com/v1.0"

        /** 공유 링크 → `/shares/{id}` 의 id (`u!` + base64url, 패딩 없음). */
        @OptIn(ExperimentalEncodingApi::class)
        fun shareIdFor(url: String): String =
            "u!" + Base64.UrlSafe.encode(url.encodeToByteArray()).trimEnd('=')
    }
}

class GraphApiException(val status: Int, val code: String, message: String) : Exception(message)

/** 조건부 업로드에서 문서가 그 사이 바뀜(412). 다시 읽고 병합한 뒤 저장해야 한다. */
class DocumentChangedException : Exception("문서가 그 사이 다른 사람에 의해 수정됐어요. 다시 불러온 뒤 저장해 주세요")

/** 문서가 잠겨 있어 덮어쓸 수 없음(423). */
class DocumentLockedException : Exception("문서가 잠겨 있어 저장하지 못했어요. 잠시 후 다시 시도해 주세요")

data class DriveFile(val bytes: ByteArray, val eTag: String, val name: String)

@Serializable
data class GraphPage<T>(
    val value: List<T> = emptyList(),
    @SerialName("@odata.nextLink") val nextLink: String? = null,
)

@Serializable
data class DriveItemRef(
    val id: String,
    val name: String? = null,
    val eTag: String? = null,
)

@Serializable
private data class DriveItemDownload(
    val id: String,
    val name: String? = null,
    val eTag: String? = null,
    @SerialName("@microsoft.graph.downloadUrl") val downloadUrl: String? = null,
)

@Serializable
private data class GraphErrorEnvelope(val error: GraphError)

@Serializable
private data class GraphError(val code: String? = null, val message: String? = null)
