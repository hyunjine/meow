package com.aivn.meow.data

import com.aivn.meow.github.DiscussionAuthor
import com.aivn.meow.github.DiscussionCommentNode
import com.aivn.meow.github.NodeList
import com.aivn.meow.github.ReviewCommentNode
import com.aivn.meow.github.ReviewNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardDiscussionTest {

    private fun user(login: String) = DiscussionAuthor(login = login, avatarUrl = "https://a/$login", typename = "User")
    private val claudeBot = DiscussionAuthor(login = "claude", typename = "Bot")

    private fun comment(login: String, at: String, author: DiscussionAuthor? = user(login)) =
        DiscussionCommentNode(author = author, body = "c-$login", createdAt = at, url = "u-$login-$at")

    private fun codeComment(login: String, at: String, author: DiscussionAuthor? = user(login)) = ReviewCommentNode(
        author = author,
        body = "code-$login",
        createdAt = at,
        path = "src/A.kt",
        line = 12,
        diffHunk = "@@ -1,4 +1,5 @@\n a\n-b\n+c\n d",
        url = "rc-$login-$at",
    )

    private fun review(
        login: String,
        state: String,
        at: String?,
        body: String = "",
        comments: List<ReviewCommentNode> = emptyList(),
        author: DiscussionAuthor? = user(login),
    ) = ReviewNode(author = author, state = state, body = body, submittedAt = at, url = "r-$login", comments = NodeList(comments))

    @Test
    fun commentsMergeTimelineAndCodeCommentsOldestFirst() {
        val result = buildCardDiscussion(
            comments = listOf(comment("b", "2026-10-02T00:00:00Z"), comment("a", "2026-10-01T00:00:00Z")),
            reviews = listOf(
                review("r", "COMMENTED", "2026-10-03T00:00:00Z", comments = listOf(codeComment("r", "2026-10-01T12:00:00Z"))),
            ),
        )
        assertEquals(listOf("a", "r", "b"), result.comments.map { it.author })
        val code = result.comments[1].code!!
        assertEquals("src/A.kt", code.path)
        assertEquals(12, code.line)
        assertEquals(listOf("-b", "+c", " d"), code.snippet)
        assertNull(result.comments[0].code)
        assertEquals("https://a/a", result.comments[0].avatarUrl)
    }

    @Test
    fun claudeBotIsShownInCardTabs() {
        val result = buildCardDiscussion(
            comments = listOf(comment("claude", "2026-10-01T00:00:00Z", claudeBot), comment("a", "2026-10-02T00:00:00Z")),
            reviews = listOf(
                review("claude", "COMMENTED", "2026-10-03T00:00:00Z", body = "bot review", author = claudeBot),
                review(
                    "x",
                    "APPROVED",
                    "2026-10-04T00:00:00Z",
                    comments = listOf(codeComment("claude[bot]", "2026-10-04T00:00:00Z", DiscussionAuthor("claude[bot]"))),
                ),
            ),
        )
        assertEquals(listOf("claude", "a", "claude[bot]"), result.comments.map { it.author })
        assertEquals(listOf("claude", "x"), result.reviews.map { it.author })
    }

    @Test
    fun reviewsKeepVerdictsAndOnlyCommentedWithBody() {
        val result = buildCardDiscussion(
            comments = emptyList(),
            reviews = listOf(
                review("c", "COMMENTED", "2026-10-03T00:00:00Z", body = "looks fine"),
                review("e", "COMMENTED", "2026-10-01T00:00:00Z", body = "  "),
                review("a", "APPROVED", "2026-10-02T00:00:00Z"),
                review("ch", "CHANGES_REQUESTED", "2026-10-01T06:00:00Z", body = "fix"),
                review("d", "DISMISSED", "2026-10-01T07:00:00Z", body = "old"),
                review("p", "PENDING", null, body = "draft"),
            ),
        )
        assertEquals(listOf("ch", "a", "c"), result.reviews.map { it.author })
        assertEquals(
            listOf(ReviewVerdict.ChangesRequested, ReviewVerdict.Approved, ReviewVerdict.Commented),
            result.reviews.map { it.verdict },
        )
    }

    @Test
    fun pendingReviewCodeCommentsAreHiddenButEmptyCommentedReviewCommentsShow() {
        val result = buildCardDiscussion(
            comments = emptyList(),
            reviews = listOf(
                review("p", "PENDING", null, comments = listOf(codeComment("p", "2026-10-01T00:00:00Z"))),
                review("e", "COMMENTED", "2026-10-02T00:00:00Z", comments = listOf(codeComment("e", "2026-10-02T00:00:00Z"))),
            ),
        )
        assertEquals(listOf("e"), result.comments.map { it.author })
        assertTrue(result.reviews.isEmpty())
    }

    @Test
    fun ghostAuthorAndOriginalLineFallback() {
        val result = buildCardDiscussion(
            comments = listOf(comment("x", "2026-10-01T00:00:00Z", author = null)),
            reviews = listOf(
                review(
                    "r",
                    "COMMENTED",
                    "2026-10-02T00:00:00Z",
                    comments = listOf(codeComment("r", "2026-10-02T00:00:00Z").copy(line = null, originalLine = 7)),
                ),
            ),
        )
        assertEquals("ghost", result.comments[0].author)
        assertNull(result.comments[0].avatarUrl)
        assertEquals(7, result.comments[1].code!!.line)
    }

    @Test
    fun diffHunkTailDropsHeaderAndKeepsLastLines() {
        assertEquals(emptyList(), diffHunkTail(null))
        assertEquals(listOf("+x"), diffHunkTail("@@ -0,0 +1 @@\n+x\n"))
        assertEquals(listOf("2", "3", "4"), diffHunkTail("@@ h @@\n1\n2\n3\n4"))
    }
}
