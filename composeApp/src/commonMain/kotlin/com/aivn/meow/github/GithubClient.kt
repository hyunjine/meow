package com.aivn.meow.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class GithubClient(
    /** 요청마다 호출돼 최신 토큰을 돌려준다 — 토큰 교체 후 재시작 없이 다시 시도로 반영하기 위함. */
    private val tokenProvider: () -> String,
    engineFactory: HttpClientEngineFactory<*>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = HttpClient(engineFactory) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    suspend fun fetchReviewRequests(org: String): ReviewSearchData =
        query(buildQuery(org), ReviewSearchData.serializer())

    /** GraphQL 쿼리를 실행하고 `data` 를 [serializer] 로 디코딩한다. 오류 분류는 [GithubApiException] 참고. */
    suspend fun <T> query(
        query: String,
        serializer: KSerializer<T>,
        variables: Map<String, String> = emptyMap(),
    ): T {
        val response: HttpResponse = http.post(GRAPHQL_ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider()}")
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query = query, variables = variables))
        }
        if (!response.status.isSuccess()) {
            throw httpFailure(response)
        }
        val body = json.decodeFromString(GraphQlResponse.serializer(serializer), response.bodyAsText())
        val errors = body.errors
        if (!errors.isNullOrEmpty()) {
            val detail = "GitHub GraphQL error: ${errors.joinToString { it.message }}"
            // GraphQL 은 rate limit · 스코프 부족도 HTTP 200 + errors[].type 으로 내려준다.
            when {
                errors.any { it.type == "RATE_LIMITED" } -> throw rateLimited(response, detail)
                errors.any { it.type == "INSUFFICIENT_SCOPES" } -> throw GithubApiException.Unauthorized(200, detail)
                else -> error(detail)
            }
        }
        return body.data ?: error("GitHub GraphQL returned no data")
    }

    fun close() = http.close()

    private suspend fun httpFailure(response: HttpResponse): GithubApiException {
        val status = response.status.value
        val body = response.bodyAsText()
        val detail = "GitHub API HTTP $status: $body"
        val remaining = response.headers[HEADER_REMAINING]?.toIntOrNull()
        // 403 은 권한 부족과 rate limit(1차: remaining=0, 2차: retry-after/본문 문구) 둘 다에 쓰인다.
        val isRateLimited = status == 429 || (
            status == 403 && (
                remaining == 0 ||
                    response.headers[HttpHeaders.RetryAfter] != null ||
                    body.contains("rate limit", ignoreCase = true)
                )
            )
        return when {
            isRateLimited -> rateLimited(response, detail)
            status == 401 || status == 403 -> GithubApiException.Unauthorized(status, detail)
            else -> GithubApiException.Http(status, detail)
        }
    }

    private fun rateLimited(response: HttpResponse, detail: String): GithubApiException.RateLimited {
        val resetAt = response.headers[HEADER_RESET]?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
            ?: response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.let { Clock.System.now() + it.seconds }
        return GithubApiException.RateLimited(
            remaining = response.headers[HEADER_REMAINING]?.toIntOrNull(),
            limit = response.headers[HEADER_LIMIT]?.toIntOrNull(),
            resetAt = resetAt,
            detail = detail,
        )
    }

    private fun buildQuery(org: String) = """
        query {
          viewer { login name avatarUrl }
          search(query: "org:$org is:pr is:open review-requested:@me archived:false", type: ISSUE, first: 50) {
            issueCount
            nodes {
              ... on PullRequest {
                number
                title
                bodyText
                url
                isDraft
                createdAt
                updatedAt
                author { login avatarUrl }
                repository { nameWithOwner }
                labels(first: 10) { nodes { name color } }
                commits(last: 1) {
                  nodes {
                    commit {
                      statusCheckRollup { state }
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    companion object {
        private const val GRAPHQL_ENDPOINT = "https://api.github.com/graphql"
        private const val HEADER_LIMIT = "x-ratelimit-limit"
        private const val HEADER_REMAINING = "x-ratelimit-remaining"
        private const val HEADER_RESET = "x-ratelimit-reset"
    }
}

/** UI 에서 원인별 안내를 하기 위해 구분하는 GitHub API 실패. */
sealed class GithubApiException(message: String) : Exception(message) {
    /** 401 · 403 등 토큰 만료 / 무효 / 권한 부족. 토큰 교체가 필요. */
    class Unauthorized(val status: Int, detail: String) : GithubApiException(detail)

    /** Rate limit 초과. [resetAt] 이후 다시 요청 가능. */
    class RateLimited(
        val remaining: Int?,
        val limit: Int?,
        val resetAt: Instant?,
        detail: String,
    ) : GithubApiException(detail)

    /** 그 밖의 HTTP 오류. */
    class Http(val status: Int, detail: String) : GithubApiException(detail)
}
