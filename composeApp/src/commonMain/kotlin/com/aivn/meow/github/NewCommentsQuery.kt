package com.aivn.meow.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * #24 · #84 새 댓글 섹션의 이슈 · PR 한 건 + 최근 댓글 · Comment 리뷰.
 * [reviews] 는 내 PR 이면 Comment 리뷰, 참여한 PR 이면 내 마지막 참여 시각 계산용 전체 리뷰다 (이슈는 빈 목록).
 */
@Serializable
data class CommentThreadNode(
    @SerialName("__typename") override val typename: String,
    override val number: Int,
    override val title: String,
    override val bodyText: String = "",
    override val url: String,
    override val updatedAt: String,
    override val author: Author? = null,
    override val repository: RepositoryNode,
    override val labels: LabelConnection,
    val comments: ThreadCommentConnection = ThreadCommentConnection(),
    val reviews: ThreadCommentConnection = ThreadCommentConnection(),
) : SearchItemFields

@Serializable
data class ThreadCommentConnection(val nodes: List<ThreadCommentNode> = emptyList())

/** 이슈 댓글 또는 PR 리뷰 한 건. [body] 는 멘션 판별용 markdown, [bodyText] 는 표시용. */
@Serializable
data class ThreadCommentNode(
    val author: CommentAuthor? = null,
    val body: String = "",
    val bodyText: String = "",
    val createdAt: String,
    /** 리뷰 전용 제출 시각. 댓글은 null. */
    val submittedAt: String? = null,
    val url: String,
)

@Serializable
data class CommentAuthor(val login: String)

@Serializable
data class ViewerLogin(val login: String)

/** [searchCommentThreads] 응답. [own] = 내가 작성한 이슈 · PR, [joined] = 내가 댓글을 단 남의 이슈 · PR. */
@Serializable
data class CommentThreadsData(
    val viewer: ViewerLogin,
    val own: SearchConnection<CommentThreadNode>,
    val joined: SearchConnection<CommentThreadNode>,
)

private const val THREAD_SEARCH_SIZE = 30
private const val THREAD_COMMENTS_SIZE = 20

private const val COMMENTS_FIELD = """
    comments(last: $THREAD_COMMENTS_SIZE) { nodes { author { login } body bodyText createdAt url } }
"""

private const val REVIEWS_FIELD = """
    reviews(last: $THREAD_COMMENTS_SIZE, states: [COMMENTED]) { nodes { author { login } body bodyText createdAt submittedAt url } }
"""

/** 참여한 PR 은 리뷰만 남긴 경우도 참여로 보므로(GitHub `commenter:` 검색과 같은 기준) 리뷰 작성자 · 시각만 받는다. */
private const val JOINED_REVIEWS_FIELD = """
    reviews(last: $THREAD_COMMENTS_SIZE) { nodes { author { login } createdAt submittedAt url } }
"""

/**
 * #84 [sinceDate](yyyy-MM-dd) 이후 갱신된 내 이슈 · PR 과 참여한 스레드를 한 번의 GraphQL 요청으로 조회한다.
 * 스레드는 검색마다 최대 [THREAD_SEARCH_SIZE] 개, 댓글 · 리뷰는 스레드마다 최근 [THREAD_COMMENTS_SIZE] 개.
 */
suspend fun GithubClient.searchCommentThreads(org: String, sinceDate: String): CommentThreadsData {
    val query = """
        query(${'$'}own: String!, ${'$'}joined: String!) {
          viewer { login }
          own: search(query: ${'$'}own, type: ISSUE, first: $THREAD_SEARCH_SIZE) {
            issueCount
            nodes {
              ... on Issue { $SEARCH_ITEM_FIELDS $COMMENTS_FIELD }
              ... on PullRequest { $SEARCH_ITEM_FIELDS $COMMENTS_FIELD $REVIEWS_FIELD }
            }
          }
          joined: search(query: ${'$'}joined, type: ISSUE, first: $THREAD_SEARCH_SIZE) {
            issueCount
            nodes {
              ... on Issue { $SEARCH_ITEM_FIELDS $COMMENTS_FIELD }
              ... on PullRequest { $SEARCH_ITEM_FIELDS $COMMENTS_FIELD $JOINED_REVIEWS_FIELD }
            }
          }
        }
    """.trimIndent()
    return query(
        query,
        CommentThreadsData.serializer(),
        mapOf(
            "own" to "org:$org author:@me updated:>=$sinceDate sort:updated-desc",
            "joined" to "org:$org commenter:@me -author:@me updated:>=$sinceDate sort:updated-desc",
        ),
    )
}
