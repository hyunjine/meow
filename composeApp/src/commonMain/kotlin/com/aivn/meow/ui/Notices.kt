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

    /**
     * #3 Realtime 으로 먼저 알린 섹션 id → url. 조회 결과에 나타나면 알림을 생략하고 지운다.
     * 검색 인덱스가 늦어 몇 번의 조회 동안 안 보일 수 있어, 직전 결과로 덮이는 seen 집합과 따로 유지한다.
     */
    private val realtimeUrls = mutableMapOf<String, MutableSet<String>>()

    /** #3 Realtime 으로 먼저 알린 내 PR url → reviewDecision. 조회에서 같은 상태가 보이면 생략하고 지운다. */
    private val realtimeReviewStates = mutableMapOf<String, String>()

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
            val announced = realtimeUrls[id]
            if (previous != null) {
                result.items
                    .filter { it.url !in previous && (announced == null || it.url !in announced) }
                    .forEach { add(notice(it)) }
            }
            announced?.removeAll(current)
        }
    }

    /**
     * #3 Realtime(웹훅)으로 도착한 알림을 알릴지 판단한다. 같은 url · 종류가 이미 조회 결과(기준값 포함)에 있거나
     * Realtime 으로 알린 적이 있으면 false. true 면 기록해 두어 이후 [diff] 가 같은 항목을 다시 알리지 않는다.
     */
    fun acceptRealtime(notice: MeowNotice): Boolean {
        val (id, item) = when (notice) {
            is MeowNotice.ReviewRequested -> return true
            is MeowNotice.Mentioned -> MentionsSection.id to notice.item
            is MeowNotice.NewComment -> MyIssueCommentsSection.id to notice.item
            is MeowNotice.Assigned -> AssignedIssuesSection.id to notice.item
            is MeowNotice.MyPrReviewed -> {
                val url = notice.item.url
                val state = notice.item.reviewState ?: return true
                if (prReviewStates?.get(url) == state || realtimeReviewStates[url] == state) return false
                realtimeReviewStates[url] = state
                return true
            }
        }
        if (seenUrls[id]?.contains(item.url) == true) return false
        return realtimeUrls.getOrPut(id) { mutableSetOf() }.add(item.url)
    }

    private fun diffMyPrs(items: List<SectionItem>): List<MeowNotice> {
        val previous = prReviewStates
        prReviewStates = items.associate { it.url to it.reviewState }
        val notices = if (previous == null) emptyList() else items.mapNotNull { item ->
            // 처음 등장한 PR 은 알리지 않고, 기존 PR 의 상태가 승인 · 변경 요청으로 바뀐 경우만 알린다.
            if (item.url !in previous) return@mapNotNull null
            val state = item.reviewState
            if (state == previous[item.url]) return@mapNotNull null
            // Realtime 으로 이미 알린 상태 변화는 생략.
            if (state != null && realtimeReviewStates[item.url] == state) return@mapNotNull null
            when (state) {
                "APPROVED" -> MeowNotice.MyPrReviewed(item, approved = true)
                "CHANGES_REQUESTED" -> MeowNotice.MyPrReviewed(item, approved = false)
                else -> null
            }
        }
        // 조회에서 Realtime 과 같은 상태를 확인했으면 기록을 지운다.
        items.forEach { item -> if (realtimeReviewStates[item.url] == item.reviewState) realtimeReviewStates.remove(item.url) }
        return notices
    }
}
