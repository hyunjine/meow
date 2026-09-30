package com.aivn.meow.ui.sections

import com.aivn.meow.config.loadCommentsLastSeen
import com.aivn.meow.config.saveCommentsLastSeen
import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
import com.aivn.meow.data.SectionHeaderAction
import com.aivn.meow.data.toSectionItem
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.IssueCommentNode
import com.aivn.meow.github.IssueWithCommentsNode
import com.aivn.meow.github.MY_ISSUE_COMMENTS_FIELDS
import com.aivn.meow.github.search
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * #24 내가 작성한 이슈에 달린 새 댓글. 댓글 하나가 항목 하나.
 * "새 댓글" = 마지막 확인 이후 + 이슈 작성자(= 나)가 아닌 사람이 단 댓글.
 * #29 확인 시각은 헤더의 '모두 확인' 으로 갱신하며, 저장된 값이 없으면 최근 7일을 기준으로 한다.
 */
object MyIssueCommentsSection : DashboardSection {
    override val id = "my-issue-comments"
    override val title = "내 이슈에 달린 새 댓글"
    override val emptyTitle = "새 댓글이 없어요"
    override val emptyHint = "마지막 확인 이후 내 이슈에 달린 댓글이 여기에 표시돼요"
    override val headerAction = SectionHeaderAction("모두 확인") {
        saveCommentsLastSeen(Clock.System.now().toString())
    }

    private val DEFAULT_WINDOW = 7.days
    private const val MAX_ITEMS = 30
    private const val PREVIEW_LENGTH = 80

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val sinceDate = lastSeen().toString().substringBefore('T')
        val result = client.search(
            "org:$org author:@me is:issue updated:>=$sinceDate",
            IssueWithCommentsNode.serializer(),
            issueFields = MY_ISSUE_COMMENTS_FIELDS,
        )
        // 조회 중 '모두 확인' 이 눌렸을 수 있으니 필터 기준은 응답 후 다시 읽는다.
        val since = lastSeen()
        val items = result.nodes
            .flatMap { issue ->
                // author:@me 검색이라 이슈 작성자가 곧 나. 내가 단 댓글은 제외한다.
                val me = issue.author?.login
                issue.comments.nodes
                    .filter { it.author?.login != me && (it.createdAtInstant() ?: return@filter false) >= since }
                    .map { comment ->
                        issue.toSectionItem(
                            updatedAtIso = comment.createdAt,
                            detail = "@${comment.author?.login ?: "ghost"}: ${comment.bodyText.preview()}",
                        ).copy(url = comment.url)
                    }
            }
            .sortedByDescending { it.updatedAtIso }
            .take(MAX_ITEMS)
        return SectionData(items = items)
    }

    private fun lastSeen(): Instant =
        loadCommentsLastSeen()?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: (Clock.System.now() - DEFAULT_WINDOW)

    private fun IssueCommentNode.createdAtInstant(): Instant? =
        runCatching { Instant.parse(createdAt) }.getOrNull()

    private fun String.preview(): String {
        val oneLine = replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= PREVIEW_LENGTH) oneLine else oneLine.take(PREVIEW_LENGTH).trimEnd() + "…"
    }
}
