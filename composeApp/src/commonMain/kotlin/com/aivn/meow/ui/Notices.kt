package com.aivn.meow.ui

import com.aivn.meow.data.SectionResult
import com.aivn.meow.model.PullRequest
import com.aivn.meow.model.SectionItem
import com.aivn.meow.ui.sections.AssignedIssuesSection
import com.aivn.meow.ui.sections.MentionsSection
import com.aivn.meow.ui.sections.MyIssueCommentsSection
import com.aivn.meow.ui.sections.MyPrStatusSection

/** #66 macOS 알림 한 건의 원인. 표시 문구는 플랫폼(Main.kt)에서 만든다. */
sealed interface MeowNotice {
    data class ReviewRequested(val pr: PullRequest) : MeowNotice
    data class Mentioned(val item: SectionItem) : MeowNotice
    data class NewComment(val item: SectionItem) : MeowNotice
    data class Assigned(val item: SectionItem) : MeowNotice
    /** 내 PR 의 리뷰 상태가 승인([approved]) 또는 변경 요청으로 바뀜. */
    data class MyPrReviewed(val item: SectionItem, val approved: Boolean) : MeowNotice
}

/**
 * 보조 섹션 결과를 직전 조회와 비교해 알림거리를 뽑는다. 섹션마다 첫 정상 결과는 기준값(알림 없음)이고,
 * 실패한 섹션(직전 목록 유지 포함)은 비교하지 않는다.
 */
internal class SectionNoticeTracker {
    /** 섹션 id → 직전 url 집합. 새 댓글 섹션은 '모두 확인' 으로 비워졌다 다시 나타날 수 있어 한 번이라도 본 url 을 누적한다. */
    private val seenUrls = mutableMapOf<String, Set<String>>()

    /** 내 PR 현황의 url → reviewDecision. null 이면 아직 기준값 없음. */
    private var prReviewStates: Map<String, String?>? = null

    fun diff(sections: List<SectionResult>): List<MeowNotice> = buildList {
        for (result in sections) {
            if (result.errorMessage != null) continue
            val id = result.section.id
            if (id == MyPrStatusSection.id) {
                addAll(diffMyPrs(result.items))
                continue
            }
            val notice: ((SectionItem) -> MeowNotice)? = when (id) {
                AssignedIssuesSection.id -> MeowNotice::Assigned
                MentionsSection.id -> MeowNotice::Mentioned
                MyIssueCommentsSection.id -> MeowNotice::NewComment
                else -> null
            }
            if (notice == null) continue
            val previous = seenUrls[id]
            val current = result.items.map { it.url }.toSet()
            seenUrls[id] = if (id == MyIssueCommentsSection.id) previous.orEmpty() + current else current
            if (previous == null) continue
            result.items.filter { it.url !in previous }.forEach { add(notice(it)) }
        }
    }

    private fun diffMyPrs(items: List<SectionItem>): List<MeowNotice> {
        val previous = prReviewStates
        prReviewStates = items.associate { it.url to it.reviewState }
        if (previous == null) return emptyList()
        return items.mapNotNull { item ->
            // 처음 등장한 PR 은 알리지 않고, 기존 PR 의 상태가 승인 · 변경 요청으로 바뀐 경우만 알린다.
            if (item.url !in previous) return@mapNotNull null
            val state = item.reviewState
            if (state == previous[item.url]) return@mapNotNull null
            when (state) {
                "APPROVED" -> MeowNotice.MyPrReviewed(item, approved = true)
                "CHANGES_REQUESTED" -> MeowNotice.MyPrReviewed(item, approved = false)
                else -> null
            }
        }
    }
}
