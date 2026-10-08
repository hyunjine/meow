package com.aivn.meow.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** #131 펼친 카드의 댓글 · 리뷰 탭 응답. GraphQL 은 권한 문제 등으로 nodes 안에 null 을 넣을 수 있다. */
@Serializable
data class NodeList<T>(val nodes: List<T?> = emptyList())

@Serializable
data class DiscussionAuthor(
    val login: String,
    val avatarUrl: String? = null,
    /** `User` · `Bot` 등. claude[bot] 판별용. */
    @SerialName("__typename") val typename: String? = null,
)

/** 이슈 · PR 타임라인 댓글. */
@Serializable
data class DiscussionCommentNode(
    val author: DiscussionAuthor? = null,
    val body: String = "",
    val createdAt: String,
    val url: String = "",
)

/** PR 리뷰의 코드(인라인) 댓글. */
@Serializable
data class ReviewCommentNode(
    val author: DiscussionAuthor? = null,
    val body: String = "",
    val createdAt: String,
    val path: String? = null,
    val line: Int? = null,
    val originalLine: Int? = null,
    val diffHunk: String? = null,
    val url: String = "",
)

@Serializable
data class ReviewNode(
    val author: DiscussionAuthor? = null,
    /** APPROVED / CHANGES_REQUESTED / COMMENTED / DISMISSED / PENDING. */
    val state: String,
    val body: String = "",
    val submittedAt: String? = null,
    val url: String = "",
    val comments: NodeList<ReviewCommentNode> = NodeList(),
)

@Serializable
data class PullDiscussionNode(
    val comments: NodeList<DiscussionCommentNode> = NodeList(),
    val reviews: NodeList<ReviewNode> = NodeList(),
)

@Serializable
data class IssueDiscussionNode(
    val comments: NodeList<DiscussionCommentNode> = NodeList(),
)

@Serializable
data class PullDiscussionData(val repository: PullDiscussionRepo? = null)

@Serializable
data class PullDiscussionRepo(val pullRequest: PullDiscussionNode? = null)

@Serializable
data class IssueDiscussionData(val repository: IssueDiscussionRepo? = null)

@Serializable
data class IssueDiscussionRepo(val issue: IssueDiscussionNode? = null)

private const val AUTHOR_FIELDS = "author { login avatarUrl __typename }"
private const val COMMENTS_FIELD = "comments(first: 50) { nodes { $AUTHOR_FIELDS body createdAt url } }"

/** #131 PR 하나의 타임라인 댓글 · 리뷰(+ 코드 댓글). PR 을 찾지 못하면 빈 결과. */
suspend fun GithubClient.fetchPullDiscussion(owner: String, name: String, number: Int): PullDiscussionNode {
    val query = """
        query(${'$'}owner: String!, ${'$'}name: String!) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            pullRequest(number: $number) {
              $COMMENTS_FIELD
              reviews(first: 30) {
                nodes {
                  $AUTHOR_FIELDS state body submittedAt url
                  comments(first: 30) {
                    nodes { $AUTHOR_FIELDS body createdAt path line originalLine diffHunk url }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()
    val data = query(query, PullDiscussionData.serializer(), discussionVariables(owner, name))
    return data.repository?.pullRequest ?: PullDiscussionNode()
}

/** #131 이슈 하나의 타임라인 댓글. 이슈를 찾지 못하면 빈 결과. */
suspend fun GithubClient.fetchIssueDiscussion(owner: String, name: String, number: Int): IssueDiscussionNode {
    val query = """
        query(${'$'}owner: String!, ${'$'}name: String!) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            issue(number: $number) { $COMMENTS_FIELD }
          }
        }
    """.trimIndent()
    val data = query(query, IssueDiscussionData.serializer(), discussionVariables(owner, name))
    return data.repository?.issue ?: IssueDiscussionNode()
}

/** 변수는 String 만 넘길 수 있어 number(Int!) 는 쿼리에 직접 넣는다. */
private fun discussionVariables(owner: String, name: String) = mapOf("owner" to owner, "name" to name)
