package com.aivn.meow.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphQlRequest(val query: String, val variables: Map<String, String> = emptyMap())

@Serializable
data class GraphQlResponse<T>(
    val data: T? = null,
    val errors: List<GraphQlError>? = null,
)

@Serializable
data class GraphQlError(val message: String, val type: String? = null)

@Serializable
data class ReviewSearchData(
    val viewer: Viewer,
    val search: Search,
)

@Serializable
data class Viewer(
    val login: String,
    val name: String? = null,
    val avatarUrl: String,
)

@Serializable
data class Search(
    val issueCount: Int,
    val nodes: List<PullRequestNode>,
)

@Serializable
data class PullRequestNode(
    val number: Int,
    val title: String,
    val bodyText: String = "",
    val url: String,
    val isDraft: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val author: Author? = null,
    val repository: RepositoryNode,
    val labels: LabelConnection,
    val commits: CommitConnection,
)

@Serializable
data class Author(val login: String, val avatarUrl: String)

@Serializable
data class RepositoryNode(val nameWithOwner: String)

@Serializable
data class LabelConnection(val nodes: List<LabelNode>)

@Serializable
data class LabelNode(val name: String, val color: String)

@Serializable
data class CommitConnection(val nodes: List<CommitEdgeNode>)

@Serializable
data class CommitEdgeNode(val commit: CommitDetail)

@Serializable
data class CommitDetail(
    @SerialName("statusCheckRollup")
    val statusCheckRollup: StatusCheckRollup? = null,
)

@Serializable
data class StatusCheckRollup(val state: String)
