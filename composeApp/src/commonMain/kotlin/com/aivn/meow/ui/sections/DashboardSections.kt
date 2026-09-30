package com.aivn.meow.ui.sections

import com.aivn.meow.data.DashboardSection

/*
 * 새 섹션 추가하는 법
 * 1. `ui/sections/<Name>Section.kt` 에 `object <Name>Section : DashboardSection` 을 만든다 (AssignedIssuesSection 참고).
 * 2. 공통 필드만 필요하면 load() 에서 `client.searchItems("org:$org ...")` + `toSectionItem()` 으로 끝.
 *    필드가 더 필요하면 `github/<Name>Query.kt` 에 SearchItemFields 를 구현한 노드 DTO 를 두고
 *    `client.search(q, Node.serializer(), issueFields = SEARCH_ITEM_FIELDS + "...")` 로 조회, badges/detail 로 표시.
 * 3. 아래 목록에서 자기 이슈 번호 자리의 주석을 풀어 등록한다. 로딩 · 60초 폴링 · 오류 격리 · 카드 렌더링은 자동.
 *    (병렬 작업 시 merge 충돌을 피하려고 슬롯 사이에 빈 줄을 둔다. 빈 줄은 지우지 말 것.)
 */
val DashboardSections: List<DashboardSection> = listOf(
    AssignedIssuesSection,

    MentionsSection,

    // #15 MyPrStatusSection,

    // #24 MyIssueCommentsSection,
)
