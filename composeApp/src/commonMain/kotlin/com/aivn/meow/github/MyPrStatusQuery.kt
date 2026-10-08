package com.aivn.meow.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** #15 내가 작성한 열린 PR 의 리뷰 · CI · 댓글 현황 노드. */
@Serializable
data class MyPrStatusNode(
    @SerialName("__typename") override val typename: String,
    override val number: Int,
    override val title: String,
    override val body: String = "",
    override val url: String,
    override val updatedAt: String,
    override val author: Author? = null,
    override val repository: RepositoryNode,
    override val labels: LabelConnection,
    val isDraft: Boolean = false,
    /** APPROVED / CHANGES_REQUESTED / REVIEW_REQUIRED. 리뷰 규칙이 없는 저장소는 null. */
    val reviewDecision: String? = null,
    val reviewThreads: ReviewThreadConnection = ReviewThreadConnection(emptyList()),
    val commits: CommitConnection = CommitConnection(emptyList()),
    val comments: TotalCountNode = TotalCountNode(0),
) : SearchItemFields {
    val unresolvedThreadCount: Int get() = reviewThreads.nodes.count { !it.isResolved }

    /** 마지막 커밋의 statusCheckRollup 상태. 체크가 없으면 null. */
    val ciState: String? get() = commits.nodes.firstOrNull()?.commit?.statusCheckRollup?.state
}

@Serializable
data class ReviewThreadConnection(val nodes: List<ReviewThreadNode>)

@Serializable
data class ReviewThreadNode(val isResolved: Boolean)

@Serializable
data class TotalCountNode(val totalCount: Int)

private const val MY_PR_STATUS_FIELDS = SEARCH_ITEM_FIELDS + """
    isDraft
    reviewDecision
    reviewThreads(first: 50) { nodes { isResolved } }
    commits(last: 1) { nodes { commit { statusCheckRollup { state } } } }
    comments { totalCount }
"""

/** `org:$org user:@me author:@me is:pr is:open` 으로 내 열린 PR 과 리뷰 · CI · 댓글 현황을 조회한다. */
suspend fun GithubClient.searchMyPrStatus(org: String): SearchConnection<MyPrStatusNode> =
    search(
        searchQuery = "${dashboardScope(org)} author:@me is:pr is:open",
        nodeSerializer = MyPrStatusNode.serializer(),
        pullRequestFields = MY_PR_STATUS_FIELDS,
    )
