package com.aivn.meow.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.json.Json

class GithubClient(
    private val token: String,
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

    suspend fun fetchReviewRequests(org: String): ReviewSearchData {
        val query = buildQuery(org)
        val response: HttpResponse = http.post(GRAPHQL_ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query = query))
        }
        if (!response.status.isSuccess()) {
            error("GitHub API HTTP ${response.status.value}: ${response.bodyAsText()}")
        }
        val body: GraphQlResponse<ReviewSearchData> = response.body()
        val errors = body.errors
        if (!errors.isNullOrEmpty()) {
            error("GitHub GraphQL error: ${errors.joinToString { it.message }}")
        }
        return body.data ?: error("GitHub GraphQL returned no data")
    }

    fun close() = http.close()

    private fun buildQuery(org: String) = """
        query {
          viewer { login name avatarUrl }
          search(query: "org:$org is:pr is:open review-requested:@me archived:false", type: ISSUE, first: 50) {
            issueCount
            nodes {
              ... on PullRequest {
                number
                title
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
    }
}
