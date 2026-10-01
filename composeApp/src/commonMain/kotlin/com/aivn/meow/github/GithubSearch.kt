package com.aivn.meow.github

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GraphQL `search(type: ISSUE)` 결과에서 이슈 · PR 이 공통으로 갖는 필드.
 * 섹션별로 필드가 더 필요하면 이 인터페이스를 구현하는 자체 노드 DTO 를 만들고
 * [search] 의 `issueFields` / `pullRequestFields` 에 [SEARCH_ITEM_FIELDS] + 추가 필드를 넘긴다.
 */
interface SearchItemFields {
    val typename: String
    val number: Int
    val title: String
    val bodyText: String
    val url: String
    val updatedAt: String
    val author: Author?
    val repository: RepositoryNode
    val labels: LabelConnection
}

/** 공통 필드만 받는 기본 검색 노드. */
@Serializable
data class SearchItemNode(
    @SerialName("__typename") override val typename: String,
    override val number: Int,
    override val title: String,
    override val bodyText: String = "",
    override val url: String,
    override val updatedAt: String,
    override val author: Author? = null,
    override val repository: RepositoryNode,
    override val labels: LabelConnection,
) : SearchItemFields

@Serializable
data class SearchResultData<T>(val search: SearchConnection<T>)

@Serializable
data class SearchConnection<T>(
    val issueCount: Int,
    val nodes: List<T>,
)

/** [SearchItemFields] 에 대응하는 GraphQL 선택 필드. */
const val SEARCH_ITEM_FIELDS = """
    __typename
    number
    title
    bodyText
    url
    updatedAt
    author { login avatarUrl }
    repository { nameWithOwner }
    labels(first: 10) { nodes { name color } }
"""

/**
 * GitHub 검색 문법([searchQuery], 예: `org:Team-AIVN assignee:@me is:issue`)으로 이슈 · PR 을 조회한다.
 * 노드 타입별 선택 필드는 [issueFields] / [pullRequestFields] 로 확장할 수 있다.
 */
suspend fun <T> GithubClient.search(
    searchQuery: String,
    nodeSerializer: KSerializer<T>,
    issueFields: String = SEARCH_ITEM_FIELDS,
    pullRequestFields: String = SEARCH_ITEM_FIELDS,
    first: Int = DEFAULT_SEARCH_SIZE,
): SearchConnection<T> {
    val query = """
        query(${'$'}q: String!) {
          search(query: ${'$'}q, type: ISSUE, first: $first) {
            issueCount
            nodes {
              ... on Issue { $issueFields }
              ... on PullRequest { $pullRequestFields }
            }
          }
        }
    """.trimIndent()
    return query(query, SearchResultData.serializer(nodeSerializer), mapOf("q" to searchQuery)).search
}

/** 공통 필드만 필요한 섹션용 단축 함수. */
suspend fun GithubClient.searchItems(
    searchQuery: String,
    first: Int = DEFAULT_SEARCH_SIZE,
): SearchConnection<SearchItemNode> = search(searchQuery, SearchItemNode.serializer(), first = first)

private const val DEFAULT_SEARCH_SIZE = 30
