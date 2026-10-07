package com.aivn.meow.data

import com.aivn.meow.github.CommentThreadNode
import com.aivn.meow.github.ThreadCommentNode
import com.aivn.meow.model.CommentSource
import kotlinx.datetime.Instant

/** #84 스레드 하나에서 고른 새 댓글 · Comment 리뷰 한 건. */
data class PickedComment(
    val source: CommentSource,
    val node: ThreadCommentNode,
    val createdAt: Instant,
    val mentionsMe: Boolean,
)

/**
 * #84 스레드 하나에서 [me] 에게 알릴 새 댓글을 고른다. 공통으로 작성자 = 나 인 것과 [since] 이전 것은 제외한다.
 * - 내 이슈 · PR: 다른 사람의 댓글(MyIssue / MyPr) + 내 PR 의 Comment 리뷰 중 본문이 있는 것(PrReview).
 *   본문 없는 Comment 리뷰는 코드 줄 댓글 · 답글만 남긴 경우라 이번 범위에서 뺀다.
 * - 참여한 스레드: 내 마지막 댓글 · 리뷰 이후 다른 사람의 댓글. 최근 목록에 내 참여가 없으면 그보다 앞서 참여한 것으로 본다.
 * 같은 url 은 한 번만 담는다.
 */
fun CommentThreadNode.newCommentsFor(me: String, since: Instant): List<PickedComment> {
    val isMine = author?.login.equals(me, ignoreCase = true)
    val isPr = typename == "PullRequest"
    val byOthers = { node: ThreadCommentNode -> !node.author?.login.equals(me, ignoreCase = true) }
    // #104 claude[bot] 의 댓글 · 리뷰는 알리지 않는다 (내 마지막 참여 시각 계산은 byOthers 그대로).
    val notify = { node: ThreadCommentNode -> byOthers(node) && !isClaudeBot(node.author?.login, node.author?.typename) }
    val picked = mutableListOf<PickedComment>()
    fun pick(source: CommentSource, node: ThreadCommentNode, at: Instant) {
        picked += PickedComment(source, node, at, mentionsLogin(node.body, me))
    }
    if (isMine) {
        val source = if (isPr) CommentSource.MyPr else CommentSource.MyIssue
        comments.nodes.filter(notify).forEach { node ->
            val at = node.createdInstant()?.takeIf { it >= since } ?: return@forEach
            pick(source, node, at)
        }
        if (isPr) {
            reviews.nodes.filter { notify(it) && it.body.isNotBlank() }.forEach { node ->
                val at = node.createdInstant()?.takeIf { it >= since } ?: return@forEach
                pick(CommentSource.PrReview, node, at)
            }
        }
    } else {
        val myLast = (comments.nodes + reviews.nodes)
            .filterNot(byOthers)
            .mapNotNull { it.createdInstant() }
            .maxOrNull()
        comments.nodes.filter(notify).forEach { node ->
            val at = node.createdInstant()?.takeIf { it >= since && (myLast == null || it > myLast) } ?: return@forEach
            pick(CommentSource.Thread, node, at)
        }
    }
    return picked.distinctBy { it.node.url }
}

/** 리뷰는 제출 시각, 댓글은 작성 시각. */
private fun ThreadCommentNode.createdInstant(): Instant? =
    runCatching { Instant.parse(submittedAt ?: createdAt) }.getOrNull()

/**
 * markdown [body] 가 [login] 을 멘션하는지. gh-webhook 의 `mentionsIn` 과 같은 기준으로
 * 코드 블록 · 인라인 코드 · 인용 줄 · 이메일 · 팀 멘션은 제외한다.
 */
fun mentionsLogin(body: String, login: String): Boolean {
    if (login.isBlank()) return false
    val text = body
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`[^`\\n]*`"), " ")
        .replace(Regex("(?m)^\\s*>.*$"), " ")
    val pattern = Regex(
        "(^|[^A-Za-z0-9_`/@.-])@${Regex.escape(login)}(?![A-Za-z0-9-]|/[A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    )
    return pattern.containsMatchIn(text)
}
