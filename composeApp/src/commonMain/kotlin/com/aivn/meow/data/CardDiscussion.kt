package com.aivn.meow.data

import com.aivn.meow.github.DiscussionAuthor
import com.aivn.meow.github.DiscussionCommentNode
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.ReviewNode
import com.aivn.meow.github.fetchIssueDiscussion
import com.aivn.meow.github.fetchPullDiscussion
import kotlin.time.Instant

/** #131 펼친 카드의 댓글 · 리뷰를 불러올 대상. [updatedAtIso] 가 바뀌면 캐시를 다시 받는다. */
data class DiscussionTarget(
    val url: String,
    /** `owner/name`. */
    val repoFullName: String,
    val number: Int,
    val isPullRequest: Boolean,
    val updatedAtIso: String,
)

/** #131 카드의 댓글 탭(타임라인 + 코드 댓글, 오래된 순)과 리뷰 탭(제출된 리뷰, 오래된 순). claude[bot] 은 빠져 있다. */
data class CardDiscussion(
    val comments: List<DiscussionComment>,
    val reviews: List<DiscussionReview>,
)

data class DiscussionComment(
    val author: String,
    val avatarUrl: String?,
    val body: String,
    val createdAtIso: String,
    val url: String,
    /** 코드(인라인) 댓글이면 파일 · 줄 · 주변 코드. 타임라인 댓글은 null. */
    val code: CodeContext? = null,
)

data class CodeContext(
    val path: String,
    val line: Int?,
    /** diffHunk 의 마지막 몇 줄 (`+` / `-` / ` ` 접두 포함). */
    val snippet: List<String>,
)

enum class ReviewVerdict { Approved, ChangesRequested, Commented }

data class DiscussionReview(
    val author: String,
    val avatarUrl: String?,
    val verdict: ReviewVerdict,
    val body: String,
    val submittedAtIso: String,
    val url: String,
)

private const val GHOST_LOGIN = "ghost"
private const val SNIPPET_LINES = 3

/** #131 대상이 PR 이면 댓글 · 리뷰, 이슈면 댓글만 받아 [CardDiscussion] 으로 만든다. */
suspend fun GithubClient.loadCardDiscussion(target: DiscussionTarget): CardDiscussion {
    val owner = target.repoFullName.substringBefore('/')
    val name = target.repoFullName.substringAfter('/')
    return if (target.isPullRequest) {
        val pr = fetchPullDiscussion(owner, name, target.number)
        buildCardDiscussion(pr.comments.nodes.filterNotNull(), pr.reviews.nodes.filterNotNull())
    } else {
        val issue = fetchIssueDiscussion(owner, name, target.number)
        buildCardDiscussion(issue.comments.nodes.filterNotNull(), emptyList())
    }
}

/**
 * #131 응답을 탭 목록으로 바꾼다.
 * - 댓글: 타임라인 댓글 + 제출된 리뷰의 코드 댓글(작성 중 PENDING 리뷰 제외), 작성 시각 오래된 순.
 * - 리뷰: 승인 · 변경 요청은 본문이 없어도, 의견(COMMENTED)은 본문이 있을 때만. 제출 시각 오래된 순.
 * - claude[bot] 이 쓴 항목은 모두 뺀다.
 */
fun buildCardDiscussion(comments: List<DiscussionCommentNode>, reviews: List<ReviewNode>): CardDiscussion {
    val timeline = comments
        .filterNot { it.author.isBot() }
        .map { node ->
            DiscussionComment(
                author = node.author?.login ?: GHOST_LOGIN,
                avatarUrl = node.author?.avatarUrl,
                body = node.body,
                createdAtIso = node.createdAt,
                url = node.url,
            )
        }
    val codeComments = reviews
        .filter { it.state != "PENDING" }
        .flatMap { review -> review.comments.nodes.filterNotNull() }
        .filterNot { it.author.isBot() }
        .map { node ->
            DiscussionComment(
                author = node.author?.login ?: GHOST_LOGIN,
                avatarUrl = node.author?.avatarUrl,
                body = node.body,
                createdAtIso = node.createdAt,
                url = node.url,
                code = CodeContext(
                    path = node.path.orEmpty(),
                    line = node.line ?: node.originalLine,
                    snippet = diffHunkTail(node.diffHunk),
                ),
            )
        }
    val submitted = reviews
        .filterNot { it.author.isBot() }
        .mapNotNull { node ->
            val verdict = when (node.state) {
                "APPROVED" -> ReviewVerdict.Approved
                "CHANGES_REQUESTED" -> ReviewVerdict.ChangesRequested
                "COMMENTED" -> ReviewVerdict.Commented.takeIf { node.body.isNotBlank() }
                else -> null
            } ?: return@mapNotNull null
            DiscussionReview(
                author = node.author?.login ?: GHOST_LOGIN,
                avatarUrl = node.author?.avatarUrl,
                verdict = verdict,
                body = node.body,
                submittedAtIso = node.submittedAt.orEmpty(),
                url = node.url,
            )
        }
    return CardDiscussion(
        comments = (timeline + codeComments).sortedBy { epochMillisOrMax(it.createdAtIso) },
        reviews = submitted.sortedBy { epochMillisOrMax(it.submittedAtIso) },
    )
}

/** diffHunk 에서 `@@ … @@` 머리줄을 빼고 마지막 [lines] 줄. 댓글이 달린 줄이 hunk 의 끝이다. */
fun diffHunkTail(diffHunk: String?, lines: Int = SNIPPET_LINES): List<String> {
    if (diffHunk.isNullOrBlank()) return emptyList()
    return diffHunk.lines()
        .filterNot { it.startsWith("@@") }
        .dropLastWhile { it.isBlank() }
        .takeLast(lines)
}

private fun DiscussionAuthor?.isBot(): Boolean = this != null && isClaudeBot(login, typename)

/** 시각을 알 수 없으면 맨 뒤로. sortedBy 는 안정 정렬이라 같은 시각은 원래 순서를 유지한다. */
private fun epochMillisOrMax(iso: String): Long =
    runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrDefault(Long.MAX_VALUE)
