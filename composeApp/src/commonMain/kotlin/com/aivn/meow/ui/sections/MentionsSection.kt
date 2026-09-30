package com.aivn.meow.ui.sections

import com.aivn.meow.data.DashboardSection
import com.aivn.meow.data.SectionData
import com.aivn.meow.data.toSectionItem
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.searchItems

/** #14 나를 멘션한 열린 이슈 · PR. */
object MentionsSection : DashboardSection {
    override val id = "mentions"
    override val title = "나를 멘션한 이슈 · PR"
    override val emptyTitle = "멘션된 스레드가 없어요"
    override val emptyHint = "누군가 나를 멘션하면 여기에 표시돼요"

    override suspend fun load(client: GithubClient, org: String): SectionData {
        val result = client.searchItems("org:$org mentions:@me is:open")
        return SectionData(
            items = result.nodes.map { it.toSectionItem() }.sortedByDescending { it.updatedAtIso },
            totalCount = result.issueCount,
        )
    }
}
