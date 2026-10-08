package com.aivn.meow.ui.pr

import com.aivn.meow.github.MergeLogin
import com.aivn.meow.github.MergeRepoNode
import com.aivn.meow.github.MergeStateNode
import com.aivn.meow.github.MessageCommit
import com.aivn.meow.github.MessageCommitConnection
import com.aivn.meow.github.MessageCommitEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergeMessageTest {

    private fun pr(
        repo: MergeRepoNode = MergeRepoNode(),
        commits: List<MessageCommit> = listOf(MessageCommit("첫 커밋"), MessageCommit("둘째 커밋")),
        totalCount: Int = commits.size,
        body: String = "PR 본문",
        owner: String? = "hyunjine",
        author: String? = "someone",
    ) = MergeStateNode(
        id = "PR_1",
        number = 42,
        title = "PR 제목",
        repository = repo,
        body = body,
        headRefName = "feat/#41-thing",
        headRepositoryOwner = owner?.let(::MergeLogin),
        author = author?.let(::MergeLogin),
        allCommits = MessageCommitConnection(totalCount, commits.map(::MessageCommitEdge)),
    )

    private fun merge(title: String, message: String) =
        MergeRepoNode(mergeCommitTitle = title, mergeCommitMessage = message)

    private fun squash(title: String, message: String) =
        MergeRepoNode(squashMergeCommitTitle = title, squashMergeCommitMessage = message)

    // ---- Merge 커밋 ----

    @Test
    fun mergeMessageTitleUsesHeadOwnerAndBranch() {
        val msg = defaultMergeMessage(pr(merge("MERGE_MESSAGE", "PR_TITLE")), MergeMethod.MERGE)
        assertEquals(MergeCommitMessage("Merge pull request #42 from hyunjine/feat/#41-thing", "PR 제목"), msg)
    }

    @Test
    fun mergeMessageTitleFallsBackToAuthorWhenHeadOwnerMissing() {
        val msg = defaultMergeMessage(pr(merge("MERGE_MESSAGE", "BLANK"), owner = null), MergeMethod.MERGE)
        assertEquals("Merge pull request #42 from someone/feat/#41-thing", msg?.title)
    }

    @Test
    fun mergePrTitleWithPrBody() {
        val msg = defaultMergeMessage(pr(merge("PR_TITLE", "PR_BODY")), MergeMethod.MERGE)
        assertEquals(MergeCommitMessage("PR 제목", "PR 본문"), msg)
    }

    @Test
    fun mergePrTitleWithPrTitleBody() {
        assertEquals(
            MergeCommitMessage("PR 제목", "PR 제목"),
            defaultMergeMessage(pr(merge("PR_TITLE", "PR_TITLE")), MergeMethod.MERGE),
        )
    }

    @Test
    fun mergeBlankBody() {
        assertEquals("", defaultMergeMessage(pr(merge("MERGE_MESSAGE", "BLANK")), MergeMethod.MERGE)?.body)
        assertEquals("", defaultMergeMessage(pr(merge("PR_TITLE", "BLANK")), MergeMethod.MERGE)?.body)
    }

    // ---- Squash ----

    @Test
    fun squashPrTitleAppendsNumber() {
        val msg = defaultMergeMessage(pr(squash("PR_TITLE", "PR_BODY")), MergeMethod.SQUASH)
        assertEquals(MergeCommitMessage("PR 제목 (#42)", "PR 본문"), msg)
    }

    @Test
    fun squashCommitOrPrTitleUsesSingleCommitHeadline() {
        val repo = squash("COMMIT_OR_PR_TITLE", "BLANK")
        val msg = defaultMergeMessage(pr(repo, commits = listOf(MessageCommit("유일한 커밋"))), MergeMethod.SQUASH)
        assertEquals(MergeCommitMessage("유일한 커밋 (#42)", ""), msg)
    }

    @Test
    fun squashCommitOrPrTitleUsesPrTitleForManyCommits() {
        val msg = defaultMergeMessage(pr(squash("COMMIT_OR_PR_TITLE", "BLANK")), MergeMethod.SQUASH)
        assertEquals("PR 제목 (#42)", msg?.title)
    }

    @Test
    fun squashCommitOrPrTitleUsesPrTitleWhenMoreCommitsThanFetched() {
        val repo = squash("COMMIT_OR_PR_TITLE", "BLANK")
        val msg = defaultMergeMessage(pr(repo, commits = listOf(MessageCommit("하나")), totalCount = 150), MergeMethod.SQUASH)
        assertEquals("PR 제목 (#42)", msg?.title)
    }

    @Test
    fun squashCommitMessagesListsEachCommitWithIndentedBody() {
        val repo = squash("PR_TITLE", "COMMIT_MESSAGES")
        val commits = listOf(
            MessageCommit("첫 커밋", "자세한 설명\n\n둘째 줄"),
            MessageCommit("둘째 커밋"),
        )
        val msg = defaultMergeMessage(pr(repo, commits = commits), MergeMethod.SQUASH)
        assertEquals("* 첫 커밋\n  자세한 설명\n\n  둘째 줄\n\n* 둘째 커밋", msg?.body)
    }

    @Test
    fun squashCommitMessagesRejoinsTruncatedHeadline() {
        val repo = squash("COMMIT_OR_PR_TITLE", "COMMIT_MESSAGES")
        val commits = listOf(MessageCommit("Merge remote-tracking branch 'origin/x' into fe…", "…at/y\n\n# Conflicts:"))
        val msg = defaultMergeMessage(pr(repo, commits = commits), MergeMethod.SQUASH)
        assertEquals("Merge remote-tracking branch 'origin/x' into feat/y (#42)", msg?.title)
        assertEquals("* Merge remote-tracking branch 'origin/x' into feat/y\n  # Conflicts:", msg?.body)
    }

    @Test
    fun squashBlankBody() {
        assertEquals("", defaultMergeMessage(pr(squash("PR_TITLE", "BLANK")), MergeMethod.SQUASH)?.body)
    }

    @Test
    fun squashPrBodyMayBeEmpty() {
        assertEquals("", defaultMergeMessage(pr(squash("PR_TITLE", "PR_BODY"), body = ""), MergeMethod.SQUASH)?.body)
    }

    // ---- Rebase ----

    @Test
    fun rebaseHasNoMessage() {
        assertNull(defaultMergeMessage(pr(), MergeMethod.REBASE))
    }
}
