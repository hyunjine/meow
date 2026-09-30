package com.aivn.meow.ui.sections

import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
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
 * "새 댓글" = 최근 7일 이내 + 이슈 작성자(= 나)가 아닌 사람이 단 댓글.
 * 마지막 확인 시각 저장은 별도 인프라가 필요해 고정 기간으로 단순화했다.
 */
object MyIssueCommentsSection : DashboardSection {
    override val id = "my-issue-comments"
    override val title = "내 이슈에 달린 새 댓글"
    override val emptyTitle = "새 댓글이 없어요"
    override val emptyHint = "최근 7일 동안 내 이슈에 달린 댓글이 여기에 표시돼요"

    private val RECENT_WINDOW = 7.days
    private const val MAX_ITEMS = 30
    private const val PREVIEW_LENGTH = 80

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val since = Clock.System.now() - RECENT_WINDOW
        val sinceDate = since.toString().substringBefore('T')
        val result = client.search(
            "org:$org author:@me is:issue updated:>=$sinceDate",
            IssueWithCommentsNode.serializer(),
            issueFields = MY_ISSUE_COMMENTS_FIELDS,
        )
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

    private fun IssueCommentNode.createdAtInstant(): Instant? =
        runCatching { Instant.parse(createdAt) }.getOrNull()

    private fun String.preview(): String {
        val oneLine = replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= PREVIEW_LENGTH) oneLine else oneLine.take(PREVIEW_LENGTH).trimEnd() + "…"
    }
}
