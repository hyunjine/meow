package com.aivn.meow.cafeteria

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class KakaoPostsPage(
    val items: List<KakaoPost> = emptyList(),
    @SerialName("has_next") val hasNext: Boolean = false,
)

@Serializable
data class KakaoPost(
    val id: Long,
    val title: String = "",
    @SerialName("published_at") val publishedAt: Long = 0,
    val type: String = "",
    val sort: String? = null,
    val contents: List<KakaoContent> = emptyList(),
    val permalink: String = "",
    val media: List<KakaoMedia> = emptyList(),
) {
    /** 본문의 텍스트 조각을 이어 붙인 것. */
    val text: String
        get() = contents.filter { it.t == "text" }
            .mapNotNull { (it.v as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .joinToString("")
}

@Serializable
data class KakaoContent(val t: String = "", val v: JsonElement? = null)

@Serializable
data class KakaoMedia(
    val type: String = "",
    @SerialName("small_url") val smallUrl: String? = null,
    @SerialName("medium_url") val mediumUrl: String? = null,
    @SerialName("large_url") val largeUrl: String? = null,
    @SerialName("xlarge_url") val xlargeUrl: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    val medium: String? get() = mediumUrl ?: largeUrl ?: xlargeUrl ?: url
    val large: String? get() = largeUrl ?: xlargeUrl ?: mediumUrl ?: url
    val xlarge: String? get() = xlargeUrl ?: url ?: largeUrl ?: mediumUrl
}

/**
 * 카카오톡 채널 공개 게시물 목록(로그인 불필요). 최신순 20건씩,
 * 다음 페이지는 `since=<직전 페이지 마지막 항목의 sort>` 로 받는다.
 */
class KakaoChannelClient(engineFactory: HttpClientEngineFactory<*>, private val profileId: String = CAFETERIA_PROFILE) {
    private val http = HttpClient(engineFactory)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPage(since: String? = null): KakaoPostsPage {
        val response = http.get("https://pf.kakao.com/rocket-web/web/profiles/$profileId/posts") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
            if (since != null) parameter("since", since)
        }
        if (!response.status.isSuccess()) error("카카오 채널 응답 ${response.status.value}")
        return json.decodeFromString(KakaoPostsPage.serializer(), response.bodyAsText())
    }

    companion object {
        /** kt대덕2연구센터 구내식당 채널. */
        const val CAFETERIA_PROFILE = "_xfaxors"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36"
    }
}
