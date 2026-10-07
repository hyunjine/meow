package com.aivn.meow.ui.sections

import com.aivn.meow.config.loadCommentsLastSeen
import com.aivn.meow.config.saveCommentsLastSeen
import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
import com.aivn.meow.data.SectionHeaderAction
import com.aivn.meow.data.newCommentsFor
import com.aivn.meow.data.toSectionItem
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.searchCommentThreads
import com.aivn.meow.model.CommentMeta
import com.aivn.meow.model.CommentSource
import com.aivn.meow.model.Label
import com.aivn.meow.theme.MeowColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * #24 · #84 새 댓글. 댓글(또는 Comment 리뷰) 하나가 항목 하나.
 * 마지막 확인 이후 다른 사람이 단 것 중
 * - 내가 작성한 이슈 · PR 의 댓글, 내 PR 의 Comment 리뷰
 * - 내가 댓글 · 리뷰로 참여한 남의 이슈 · PR 에서 내 마지막 참여 이후 달린 댓글
 * 고르는 기준은 [newCommentsFor]. 카드에는 출처 칩을 붙인다.
 * #29 확인 시각은 탭 바의 '모두 확인' 으로 갱신하며, 저장된 값이 없으면 최근 7일을 기준으로 한다.
 */
object MyIssueCommentsSection : DashboardSection {
    override val id = "my-issue-comments"
    override val title = "새 댓글"
    override val tabLabel = "새 댓글"
    override val emptyTitle = "새 댓글이 없어요"
    override val emptyHint = "마지막 확인 이후 내 이슈 · PR 과 참여한 스레드에 달린 댓글이 여기에 표시돼요"
    override val headerAction = SectionHeaderAction("모두 확인") {
        saveCommentsLastSeen(Clock.System.now().toString())
    }

    private val DEFAULT_WINDOW = 7.days
    private const val MAX_ITEMS = 30

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val sinceDate = lastSeen().toString().substringBefore('T')
        val result = client.searchCommentThreads(org, sinceDate)
        // 조회 중 '모두 확인' 이 눌렸을 수 있으니 필터 기준은 응답 후 다시 읽는다.
        val since = lastSeen()
        val me = result.viewer.login
        val items = (result.own.nodes + result.joined.nodes)
            .distinctBy { it.url }
            .flatMap { thread ->
                thread.newCommentsFor(me, since).map { picked ->
                    val commenter = picked.node.author?.login ?: "ghost"
                    val isReview = picked.source == CommentSource.PrReview
                    thread.toSectionItem(
                        badges = listOf(Label(picked.source.label, picked.source.color())),
                        updatedAtIso = picked.createdAt.toString(),
                        detail = "@$commenter 님의 ${if (isReview) "리뷰 의견" else "댓글"}",
                        body = picked.node.bodyText,
                    ).copy(
                        url = picked.node.url,
                        comment = CommentMeta(
                            source = picked.source,
                            commenter = commenter,
                            threadUrl = thread.url,
                            createdAtIso = picked.createdAt.toString(),
                            mentionsMe = picked.mentionsMe,
                        ),
                    )
                }
            }
            .distinctBy { it.url }
            .sortedByDescending { it.updatedAtIso }
            .take(MAX_ITEMS)
        return SectionData(items = items)
    }

    private fun lastSeen(): Instant =
        loadCommentsLastSeen()?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: (Clock.System.now() - DEFAULT_WINDOW)

    private fun CommentSource.color() = when (this) {
        CommentSource.MyIssue -> MeowColors.Teal
        CommentSource.MyPr -> MeowColors.Brand
        CommentSource.PrReview -> MeowColors.Violet
        CommentSource.Thread -> MeowColors.Grey
    }
}
