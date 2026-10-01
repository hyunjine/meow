package com.aivn.meow.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** #24 내가 작성한 이슈 + 최근 댓글. 공통 필드에 `comments(last: 10)` 를 더해 조회한다. */
@Serializable
data class IssueWithCommentsNode(
    @SerialName("__typename") override val typename: String,
    override val number: Int,
    override val title: String,
    override val bodyText: String = "",
    override val url: String,
    override val updatedAt: String,
    override val author: Author? = null,
    override val repository: RepositoryNode,
    override val labels: LabelConnection,
    val comments: IssueCommentConnection = IssueCommentConnection(),
) : SearchItemFields

@Serializable
data class IssueCommentConnection(val nodes: List<IssueCommentNode> = emptyList())

@Serializable
data class IssueCommentNode(
    val author: CommentAuthor? = null,
    val bodyText: String = "",
    val createdAt: String,
    val url: String,
)

@Serializable
data class CommentAuthor(val login: String)

const val MY_ISSUE_COMMENTS_FIELDS = SEARCH_ITEM_FIELDS + """
    comments(last: 10) { nodes { author { login } bodyText createdAt url } }
"""
