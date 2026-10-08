package com.aivn.meow.ui.pr

import com.aivn.meow.github.CommitConnection
import com.aivn.meow.github.CommitDetail
import com.aivn.meow.github.CommitEdgeNode
import com.aivn.meow.github.MergeRepoNode
import com.aivn.meow.github.MergeStateNode
import com.aivn.meow.github.OpinionatedReviewConnection
import com.aivn.meow.github.OpinionatedReviewNode
import com.aivn.meow.github.StatusCheckRollup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MergeStatusTest {

    private fun pr(
        state: String = "OPEN",
        isDraft: Boolean = false,
        mergeable: String = "MERGEABLE",
        status: String = "CLEAN",
        reviewDecision: String? = "APPROVED",
        reviews: List<String> = listOf("APPROVED", "APPROVED"),
        ci: String? = "SUCCESS",
        repo: MergeRepoNode = MergeRepoNode(),
    ) = MergeStateNode(
        id = "PR_1",
        number = 1,
        title = "t",
        state = state,
        isDraft = isDraft,
        mergeable = mergeable,
        mergeStateStatus = status,
        reviewDecision = reviewDecision,
        latestOpinionatedReviews = OpinionatedReviewConnection(reviews.map { OpinionatedReviewNode(it) }),
        commits = CommitConnection(
            listOfNotNull(ci?.let { CommitEdgeNode(CommitDetail(StatusCheckRollup(it))) }),
        ),
        repository = repo,
    )

    @Test
    fun cleanIsMergeableWithSummary() {
        val v = mergeVerdict(pr())
        assertTrue(v.enabled)
        assertEquals(MergeTone.Ready, v.tone)
        assertEquals("머지할 수 있어요", v.title)
        assertEquals("승인 2 · CI 통과 · 충돌 없음", v.subtitle)
    }

    @Test
    fun hasHooksAndUnstableAreMergeable() {
        assertTrue(mergeVerdict(pr(status = "HAS_HOOKS")).enabled)
        val unstable = mergeVerdict(pr(status = "UNSTABLE", ci = "FAILURE"))
        assertTrue(unstable.enabled)
        assertEquals("승인 2 · CI 실패 · 충돌 없음", unstable.subtitle)
    }

    @Test
    fun summaryFallsBackToReviewDecisionAndNoChecks() {
        assertEquals("승인됨 · 충돌 없음", mergeVerdict(pr(reviews = emptyList(), ci = null)).subtitle)
        assertEquals("충돌 없음", mergeVerdict(pr(reviewDecision = null, reviews = emptyList(), ci = null)).subtitle)
    }

    @Test
    fun conflictsBlock() {
        val dirty = mergeVerdict(pr(mergeable = "CONFLICTING", status = "DIRTY"))
        assertFalse(dirty.enabled)
        assertEquals(MergeTone.Blocked, dirty.tone)
        assertEquals("충돌을 해결해야 해요", dirty.title)
        assertEquals("충돌을 해결해야 해요", mergeVerdict(pr(mergeable = "MERGEABLE", status = "DIRTY")).title)
    }

    @Test
    fun blockedReasons() {
        assertEquals(
            "승인이 필요해요",
            mergeVerdict(pr(status = "BLOCKED", reviewDecision = "REVIEW_REQUIRED", reviews = emptyList())).title,
        )
        assertEquals(
            "변경 요청이 있어요",
            mergeVerdict(pr(status = "BLOCKED", reviewDecision = "CHANGES_REQUESTED", reviews = listOf("CHANGES_REQUESTED"))).title,
        )
        assertEquals("CI 가 실패했어요", mergeVerdict(pr(status = "BLOCKED", ci = "FAILURE")).title)
        assertEquals("CI 가 실패했어요", mergeVerdict(pr(status = "BLOCKED", ci = "ERROR")).title)
        assertEquals("CI 가 진행 중이에요", mergeVerdict(pr(status = "BLOCKED", ci = "PENDING")).title)
        assertEquals("브랜치 보호 규칙에 막혀 있어요", mergeVerdict(pr(status = "BLOCKED")).title)
        assertFalse(mergeVerdict(pr(status = "BLOCKED")).enabled)
    }

    @Test
    fun behindAndDraft() {
        assertEquals("베이스 브랜치보다 뒤처져 있어요", mergeVerdict(pr(status = "BEHIND")).title)
        assertEquals("초안 PR 이에요", mergeVerdict(pr(isDraft = true, status = "DRAFT")).title)
        assertEquals("초안 PR 이에요", mergeVerdict(pr(isDraft = true, status = "CLEAN")).title)
        assertFalse(mergeVerdict(pr(isDraft = true)).enabled)
    }

    @Test
    fun unknownRetries() {
        val v = mergeVerdict(pr(mergeable = "UNKNOWN", status = "UNKNOWN"))
        assertFalse(v.enabled)
        assertTrue(v.retry)
        assertEquals(MergeTone.Checking, v.tone)
        assertEquals("머지 가능 여부 확인 중…", v.title)
        assertTrue(mergeVerdict(pr(mergeable = "MERGEABLE", status = "UNKNOWN")).retry)
        assertFalse(mergeVerdict(pr()).retry)
    }

    @Test
    fun noAllowedMethodBlocks() {
        val repo = MergeRepoNode("MERGE", mergeCommitAllowed = false, squashMergeAllowed = false, rebaseMergeAllowed = false)
        val v = mergeVerdict(pr(repo = repo))
        assertFalse(v.enabled)
        assertEquals("허용된 머지 방식이 없어요", v.title)
    }

    @Test
    fun allowedMethodsFollowRepoSettings() {
        assertEquals(MergeMethod.entries, allowedMergeMethods(MergeRepoNode()))
        assertEquals(
            listOf(MergeMethod.SQUASH),
            allowedMergeMethods(MergeRepoNode("SQUASH", mergeCommitAllowed = false, squashMergeAllowed = true, rebaseMergeAllowed = false)),
        )
    }

    @Test
    fun selectsRepoDefaultThenChosenThenFirstAllowed() {
        val all = MergeRepoNode(viewerDefaultMergeMethod = "SQUASH")
        assertEquals(MergeMethod.SQUASH, selectMergeMethod(all))
        assertEquals(MergeMethod.REBASE, selectMergeMethod(all, chosen = MergeMethod.REBASE))

        // 기본값이 허용되지 않으면 허용 목록 첫 번째
        val noMerge = MergeRepoNode("MERGE", mergeCommitAllowed = false, squashMergeAllowed = true, rebaseMergeAllowed = true)
        assertEquals(MergeMethod.SQUASH, selectMergeMethod(noMerge))
        // 고른 방식이 허용되지 않으면 무시
        assertEquals(MergeMethod.SQUASH, selectMergeMethod(noMerge, chosen = MergeMethod.MERGE))

        val none = MergeRepoNode("MERGE", mergeCommitAllowed = false, squashMergeAllowed = false, rebaseMergeAllowed = false)
        assertNull(selectMergeMethod(none))
    }

    @Test
    fun labels() {
        assertEquals("Merge 커밋", MergeMethod.MERGE.label)
        assertEquals("Squash", MergeMethod.SQUASH.label)
        assertEquals("Rebase", MergeMethod.REBASE.label)
    }

    @Test
    fun errorTextStripsClientPrefix() {
        val e = IllegalStateException("GitHub GraphQL error: Required status check \"build\" is expected.")
        assertEquals("Required status check \"build\" is expected.", githubErrorText(e))
    }
}
