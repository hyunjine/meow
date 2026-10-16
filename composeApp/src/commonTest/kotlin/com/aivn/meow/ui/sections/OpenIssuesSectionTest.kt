package com.aivn.meow.ui.sections

import com.aivn.meow.github.Author
import com.aivn.meow.github.LabelConnection
import com.aivn.meow.github.LabelNode
import com.aivn.meow.github.RepositoryNode
import com.aivn.meow.github.SearchItemNode
import com.aivn.meow.model.ItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenIssuesSectionTest {
    private fun node(
        number: Int,
        updatedAt: String,
        typename: String = "Issue",
        repo: String = "Team-AIVN/meow",
    ) = SearchItemNode(
        typename = typename,
        number = number,
        title = "title $number",
        body = "body $number",
        url = "https://github.com/$repo/issues/$number",
        updatedAt = updatedAt,
        author = Author(login = "hyunjine", avatarUrl = ""),
        repository = RepositoryNode(repo),
        labels = LabelConnection(listOf(LabelNode("bug", "d73a4a"))),
    )

    @Test
    fun queryCoversDashboardScopeOpenIssuesSortedByUpdated() {
        assertEquals(
            "org:Team-AIVN user:@me is:issue is:open sort:updated-desc",
            openIssuesQuery("Team-AIVN"),
        )
    }

    @Test
    fun itemsAreIssuesOnlySortedByUpdatedDesc() {
        val items = openIssueItems(
            listOf(
                node(1, "2026-10-01T00:00:00Z"),
                node(2, "2026-10-03T00:00:00Z", typename = "PullRequest"),
                node(3, "2026-10-02T00:00:00Z", repo = "hyunjine/side"),
            ),
        )
        assertEquals(listOf(3, 1), items.map { it.number })
        assertTrue(items.all { it.kind == ItemKind.Issue })
        val first = items.first()
        assertEquals("hyunjine/side", first.repoFullName)
        assertEquals("side", first.repo)
        assertEquals("body 3", first.body)
        assertEquals(listOf("bug"), first.labels.map { it.text })
    }

    @Test
    fun sectionIsDiscussionTabAndDoesNotSeedFavorites() {
        assertEquals("이슈", OpenIssuesSection.tabLabel)
        assertTrue(OpenIssuesSection.showsDiscussion)
        assertFalse(OpenIssuesSection.seedsFavorites)
        assertEquals(
            listOf(MyPrStatusSection, OpenIssuesSection, AssignedIssuesSection, MentionsSection, MyIssueCommentsSection),
            DashboardSections,
        )
    }
}
