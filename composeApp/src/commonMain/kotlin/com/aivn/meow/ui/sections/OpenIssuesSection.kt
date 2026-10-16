package com.aivn.meow.ui.sections

import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
import com.aivn.meow.data.toSectionItem
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.SearchItemFields
import com.aivn.meow.github.dashboardScope
import com.aivn.meow.github.searchItems
import com.aivn.meow.model.SectionItem

/**
 * #144 대시보드 범위(조직 + 개인 레포)의 열린 이슈 전체. 다른 탭처럼 사이드바에서 체크한 레포만 보여준다.
 * 체크 필터가 조회 뒤에 걸리므로 검색 한도(100)까지 넉넉히 받는다. 알림 대상은 아니다.
 */
object OpenIssuesSection : DashboardSection {
    override val id = "open-issues"
    override val title = "열린 이슈"
    override val tabLabel = "이슈"
    override val emptyTitle = "열린 이슈가 없어요"
    override val emptyHint = "체크한 레포에 열린 이슈가 생기면 여기에 표시돼요"
    override val showsDiscussion = true

    // 조직 전체의 이슈라 '작업 중' 레포(첫 실행 즐겨찾기 기본값)로 치지 않는다.
    override val seedsFavorites = false

    private const val MAX_ITEMS = 100

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val result = client.searchItems(openIssuesQuery(org), first = MAX_ITEMS)
        return SectionData(items = openIssueItems(result.nodes), totalCount = result.issueCount)
    }
}

/** #144 이슈 탭 검색어. 최근 갱신 순. */
internal fun openIssuesQuery(org: String): String = "${dashboardScope(org)} is:issue is:open sort:updated-desc"

/** #144 검색 노드 → 이슈 탭 항목. 검색 결과에 PR 이 섞여 와도 이슈만 남기고 최근 갱신 순으로 정렬한다. */
internal fun openIssueItems(nodes: List<SearchItemFields>): List<SectionItem> =
    nodes.filter { it.typename == "Issue" }
        .map { it.toSectionItem() }
        .sortedByDescending { it.updatedAtIso }
