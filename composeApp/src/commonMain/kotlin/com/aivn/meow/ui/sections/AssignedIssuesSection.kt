package com.aivn.meow.ui.sections

import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
import com.aivn.meow.data.toSectionItem
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.searchItems

/** #16 나에게 할당된 열린 이슈. */
object AssignedIssuesSection : DashboardSection {
    override val id = "assigned-issues"
    override val title = "나에게 할당된 이슈"
    override val emptyTitle = "할당된 이슈가 없습니다 🎉"
    override val emptyHint = "새로 할당되면 여기에 표시돼요"

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val result = client.searchItems("org:$org assignee:@me is:issue is:open")
        return SectionData(
            items = result.nodes.map { it.toSectionItem() }.sortedByDescending { it.updatedAtIso },
            totalCount = result.issueCount,
        )
    }
}
