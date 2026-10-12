package com.aivn.meow.ui.pr

import com.aivn.meow.github.MergeRepoNode
import com.aivn.meow.github.MergeStateNode

/** #130 GitHub 머지 방식. [label] 은 버튼 · 메뉴 문구 (`{label} 머지`). */
enum class MergeMethod(val label: String) {
    MERGE("Merge 커밋"),
    SQUASH("Squash"),
    REBASE("Rebase"),
}

/** 레포가 허용하는 머지 방식 (GitHub 의 표시 순서). */
fun allowedMergeMethods(repo: MergeRepoNode): List<MergeMethod> = buildList {
    if (repo.mergeCommitAllowed) add(MergeMethod.MERGE)
    if (repo.squashMergeAllowed) add(MergeMethod.SQUASH)
    if (repo.rebaseMergeAllowed) add(MergeMethod.REBASE)
}

/**
 * 처음 선택돼 있을 머지 방식: [chosen] (사용자가 고른 것) → 레포 기본값(viewerDefaultMergeMethod) → 허용 목록 첫 번째.
 * 허용되지 않은 방식은 건너뛴다. 허용된 방식이 없으면 null.
 */
fun selectMergeMethod(repo: MergeRepoNode, chosen: MergeMethod? = null): MergeMethod? {
    val allowed = allowedMergeMethods(repo)
    val repoDefault = MergeMethod.entries.firstOrNull { it.name == repo.viewerDefaultMergeMethod }
    return chosen?.takeIf { it in allowed }
        ?: repoDefault?.takeIf { it in allowed }
        ?: allowed.firstOrNull()
}

enum class MergeTone {
    /** 머지 가능 — 초록. */
    Ready,

    /** 머지 불가 — 주황. */
    Blocked,

    /** GitHub 이 아직 계산 중 — 회색, 잠시 후 다시 조회. */
    Checking,
}

/** 푸터 왼쪽 문구와 버튼 활성 여부. [retry] 면 몇 초 뒤 상태를 다시 조회한다. */
data class MergeVerdict(
    val enabled: Boolean,
    val title: String,
    val subtitle: String,
    val tone: MergeTone,
    val retry: Boolean = false,
)

private val MERGEABLE_STATUSES = setOf("CLEAN", "HAS_HOOKS", "UNSTABLE")
private val FAILED_CI = setOf("FAILURE", "ERROR")
private val PENDING_CI = setOf("PENDING", "EXPECTED")

/** PR 의 mergeable · mergeStateStatus · 리뷰 · CI 상태를 푸터 문구로 바꾼다. 판단 기준은 GitHub 의 mergeStateStatus. */
fun mergeVerdict(pr: MergeStateNode): MergeVerdict {
    val summary = statusSummary(pr)
    fun blocked(title: String) = MergeVerdict(enabled = false, title = title, subtitle = summary, tone = MergeTone.Blocked)

    return when {
        pr.state == "MERGED" -> MergeVerdict(false, "이미 머지됐어요", summary, MergeTone.Ready)
        pr.state != "OPEN" -> blocked("닫힌 PR 이에요")
        pr.isDraft || pr.mergeStateStatus == "DRAFT" -> blocked("초안 PR 이에요")
        pr.mergeable == "CONFLICTING" || pr.mergeStateStatus == "DIRTY" -> blocked("충돌을 해결해야 해요")
        pr.mergeable == "UNKNOWN" || pr.mergeStateStatus == "UNKNOWN" -> MergeVerdict(
            enabled = false,
            title = "머지 가능 여부 확인 중…",
            subtitle = "GitHub 이 머지 가능 여부를 계산하고 있어요",
            tone = MergeTone.Checking,
            retry = true,
        )
        allowedMergeMethods(pr.repository).isEmpty() -> blocked("허용된 머지 방식이 없어요")
        pr.mergeStateStatus in MERGEABLE_STATUSES -> MergeVerdict(
            enabled = true,
            title = "머지할 수 있어요",
            subtitle = summary,
            tone = MergeTone.Ready,
        )
        pr.mergeStateStatus == "BEHIND" -> blocked("베이스 브랜치보다 뒤처져 있어요")
        // BLOCKED: 브랜치 보호 규칙에 걸린 원인을 리뷰 → CI 순으로 짚는다.
        pr.reviewDecision == "CHANGES_REQUESTED" -> blocked("변경 요청이 있어요")
        pr.reviewDecision == "REVIEW_REQUIRED" -> blocked("승인이 필요해요")
        pr.ciState in FAILED_CI -> blocked("CI 가 실패했어요")
        pr.ciState in PENDING_CI -> blocked("CI 가 진행 중이에요")
        else -> blocked("브랜치 보호 규칙에 막혀 있어요")
    }
}

/** 예: `승인 2 · CI 통과 · 충돌 없음`. */
internal fun statusSummary(pr: MergeStateNode): String {
    val parts = buildList {
        val approvals = pr.approvalCount
        when {
            approvals > 0 -> add("승인 $approvals")
            pr.reviewDecision == "APPROVED" -> add("승인됨")
            pr.reviewDecision == "CHANGES_REQUESTED" -> add("변경 요청")
            pr.reviewDecision == "REVIEW_REQUIRED" -> add("승인 0")
        }
        when (pr.ciState) {
            null -> Unit
            "SUCCESS" -> add("CI 통과")
            in FAILED_CI -> add("CI 실패")
            else -> add("CI 진행 중")
        }
        when (pr.mergeable) {
            "MERGEABLE" -> add("충돌 없음")
            "CONFLICTING" -> add("충돌 있음")
        }
    }
    return parts.joinToString(" · ").ifEmpty { "리뷰 · CI 조건 없음" }
}

/** GithubClient 가 붙이는 접두어를 떼고 GitHub 오류 원문만 남긴다. */
internal fun githubErrorText(error: Throwable): String =
    (error.message ?: error::class.simpleName ?: "알 수 없는 오류")
        .removePrefix("GitHub GraphQL error: ")
