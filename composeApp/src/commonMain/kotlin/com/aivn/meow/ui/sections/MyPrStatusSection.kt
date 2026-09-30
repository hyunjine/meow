package com.aivn.meow.ui.sections

import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
import com.aivn.meow.data.toSectionItem
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.MyPrStatusNode
import com.aivn.meow.github.searchMyPrStatus
import com.aivn.meow.model.Label
import com.aivn.meow.theme.MeowColors

/** #15 내가 작성한 열린 PR 의 리뷰 · CI · 댓글 현황. */
object MyPrStatusSection : DashboardSection {
    override val id = "my-pr-status"
    override val title = "내 PR 현황"
    override val emptyTitle = "열려 있는 내 PR 이 없어요"
    override val emptyHint = "PR 을 올리면 리뷰 · CI 현황이 여기에 표시돼요"

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val result = client.searchMyPrStatus(org)
        val sorted = result.nodes.sortedWith(
            compareBy<MyPrStatusNode> { it.priority() }.thenByDescending { it.updatedAt },
        )
        return SectionData(
            items = sorted.map { it.toSectionItem(badges = it.badges(), detail = it.detail()) },
            totalCount = result.issueCount,
        )
    }
}

/** 변경 요청 > 미해결 스레드 있음 > 나머지. */
private fun MyPrStatusNode.priority(): Int = when {
    reviewDecision == "CHANGES_REQUESTED" -> 0
    unresolvedThreadCount > 0 -> 1
    else -> 2
}

private fun MyPrStatusNode.badges(): List<Label> = buildList {
    when (reviewDecision) {
        "CHANGES_REQUESTED" -> add(Label("변경 요청", MeowColors.Error))
        "APPROVED" -> add(Label("승인", MeowColors.Success))
        "REVIEW_REQUIRED" -> add(Label("리뷰 대기", MeowColors.Grey))
    }
    // PrList 의 CiChip 과 같은 문구 · 색. 체크가 아예 없으면 표시하지 않는다.
    when (ciState) {
        null -> Unit
        "SUCCESS" -> add(Label("CI · 통과", MeowColors.Success))
        "FAILURE", "ERROR" -> add(Label("CI · 실패", MeowColors.Error))
        else -> add(Label("CI · 진행중", MeowColors.Warning))
    }
    if (isDraft) add(Label("Draft", MeowColors.Grey))
}

private fun MyPrStatusNode.detail(): String =
    "미해결 스레드 $unresolvedThreadCount · 댓글 ${comments.totalCount}"
