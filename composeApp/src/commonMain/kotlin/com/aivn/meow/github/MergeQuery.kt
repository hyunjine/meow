package com.aivn.meow.github

import kotlinx.serialization.Serializable

/** #130 PR 머지 버튼에 필요한 PR 의 머지 가능 상태 + 레포의 허용 머지 방식. */
@Serializable
data class MergeStateNode(
    /** mergePullRequest 의 pullRequestId. */
    val id: String,
    val number: Int,
    val title: String,
    /** OPEN / CLOSED / MERGED. */
    val state: String = "OPEN",
    val isDraft: Boolean = false,
    /** MERGEABLE / CONFLICTING / UNKNOWN. GitHub 이 지연 계산해 처음엔 UNKNOWN 일 수 있다. */
    val mergeable: String = "UNKNOWN",
    /** CLEAN / BLOCKED / BEHIND / DIRTY / UNSTABLE / HAS_HOOKS / DRAFT / UNKNOWN. */
    val mergeStateStatus: String = "UNKNOWN",
    /** APPROVED / CHANGES_REQUESTED / REVIEW_REQUIRED. 리뷰 규칙이 없으면 null. */
    val reviewDecision: String? = null,
    /** 리뷰어별 마지막 의견(APPROVED / CHANGES_REQUESTED / COMMENTED) 리뷰. */
    val latestOpinionatedReviews: OpinionatedReviewConnection = OpinionatedReviewConnection(emptyList()),
    val commits: CommitConnection = CommitConnection(emptyList()),
    val repository: MergeRepoNode,
    /** 머지 커밋 기본 내용(PR_BODY) 용 본문. */
    val body: String = "",
    /** `Merge pull request #N from {owner}/{headRefName}` 용. */
    val headRefName: String = "",
    val headRepositoryOwner: MergeLogin? = null,
    val author: MergeLogin? = null,
    /** Squash 기본 제목 · 내용(COMMIT_OR_PR_TITLE / COMMIT_MESSAGES) 용 커밋 메시지. 최대 100개. */
    val allCommits: MessageCommitConnection = MessageCommitConnection(),
) {
    /** 리뷰어별 최신 리뷰 중 승인 수. */
    val approvalCount: Int get() = latestOpinionatedReviews.nodes.count { it.state == "APPROVED" }

    /** 마지막 커밋의 statusCheckRollup 상태 (SUCCESS / FAILURE / ERROR / PENDING / EXPECTED). 체크가 없으면 null. */
    val ciState: String? get() = commits.nodes.firstOrNull()?.commit?.statusCheckRollup?.state
}

@Serializable
data class MergeLogin(val login: String)

@Serializable
data class MessageCommitConnection(
    val totalCount: Int = 0,
    val nodes: List<MessageCommitEdge> = emptyList(),
)

@Serializable
data class MessageCommitEdge(val commit: MessageCommit)

@Serializable
data class MessageCommit(val messageHeadline: String = "", val messageBody: String = "")

@Serializable
data class OpinionatedReviewConnection(val nodes: List<OpinionatedReviewNode>)

@Serializable
data class OpinionatedReviewNode(val state: String)

@Serializable
data class MergeRepoNode(
    /** MERGE / SQUASH / REBASE. */
    val viewerDefaultMergeMethod: String = "MERGE",
    val mergeCommitAllowed: Boolean = true,
    val squashMergeAllowed: Boolean = true,
    val rebaseMergeAllowed: Boolean = true,
    /** 머지 커밋 기본 제목: PR_TITLE / MERGE_MESSAGE. */
    val mergeCommitTitle: String = "MERGE_MESSAGE",
    /** 머지 커밋 기본 내용: PR_BODY / PR_TITLE / BLANK. */
    val mergeCommitMessage: String = "PR_TITLE",
    /** Squash 기본 제목: PR_TITLE / COMMIT_OR_PR_TITLE. */
    val squashMergeCommitTitle: String = "COMMIT_OR_PR_TITLE",
    /** Squash 기본 내용: PR_BODY / COMMIT_MESSAGES / BLANK. */
    val squashMergeCommitMessage: String = "COMMIT_MESSAGES",
)

@Serializable
private data class MergeStateData(val resource: MergeStateNode? = null)

@Serializable
private data class MergeResultData(val mergePullRequest: MergeResultPayload? = null)

@Serializable
private data class MergeResultPayload(val pullRequest: MergedPullRequest? = null)

@Serializable
private data class MergedPullRequest(val state: String)

private val MERGE_STATE_QUERY = """
    query(${'$'}url: URI!) {
      resource(url: ${'$'}url) {
        ... on PullRequest {
          id
          number
          title
          state
          isDraft
          mergeable
          mergeStateStatus
          reviewDecision
          latestOpinionatedReviews(first: 50) { nodes { state } }
          commits(last: 1) { nodes { commit { statusCheckRollup { state } } } }
          body
          headRefName
          headRepositoryOwner { login }
          author { login }
          allCommits: commits(first: 100) { totalCount nodes { commit { messageHeadline messageBody } } }
          repository {
            viewerDefaultMergeMethod
            mergeCommitAllowed
            squashMergeAllowed
            rebaseMergeAllowed
            mergeCommitTitle
            mergeCommitMessage
            squashMergeCommitTitle
            squashMergeCommitMessage
          }
        }
      }
    }
""".trimIndent()

private val MERGE_MUTATION = """
    mutation(${'$'}id: ID!, ${'$'}method: PullRequestMergeMethod!) {
      mergePullRequest(input: { pullRequestId: ${'$'}id, mergeMethod: ${'$'}method }) {
        pullRequest { state }
      }
    }
""".trimIndent()

/** MERGE / SQUASH 용 — 커밋 제목 · 내용을 함께 보낸다. */
private val MERGE_WITH_MESSAGE_MUTATION = """
    mutation(${'$'}id: ID!, ${'$'}method: PullRequestMergeMethod!, ${'$'}headline: String!, ${'$'}body: String!) {
      mergePullRequest(input: {
        pullRequestId: ${'$'}id, mergeMethod: ${'$'}method,
        commitHeadline: ${'$'}headline, commitBody: ${'$'}body
      }) {
        pullRequest { state }
      }
    }
""".trimIndent()

/** PR url 로 머지 가능 상태를 조회한다. PR 이 아니거나 찾을 수 없으면 오류. */
suspend fun GithubClient.fetchMergeState(prUrl: String): MergeStateNode =
    query(MERGE_STATE_QUERY, MergeStateData.serializer(), mapOf("url" to prUrl)).resource
        ?: error("PR 을 찾을 수 없어요")

/**
 * PR 을 [mergeMethod] (MERGE / SQUASH / REBASE) 로 머지한다.
 * [commitHeadline] 이 있으면 (MERGE / SQUASH) 커밋 제목 · 내용으로 보낸다. REBASE 는 무시한다.
 * 브랜치 보호 등으로 거절되면 GitHub 오류 메시지를 담은 예외가 난다.
 */
suspend fun GithubClient.mergePullRequest(
    pullRequestId: String,
    mergeMethod: String,
    commitHeadline: String? = null,
    commitBody: String = "",
) {
    if (commitHeadline != null && mergeMethod != "REBASE") {
        query(
            MERGE_WITH_MESSAGE_MUTATION,
            MergeResultData.serializer(),
            mapOf("id" to pullRequestId, "method" to mergeMethod, "headline" to commitHeadline, "body" to commitBody),
        )
    } else {
        query(MERGE_MUTATION, MergeResultData.serializer(), mapOf("id" to pullRequestId, "method" to mergeMethod))
    }
}
