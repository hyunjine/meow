package com.aivn.meow.ui.pr

import com.aivn.meow.github.MergeStateNode
import com.aivn.meow.github.MessageCommit

/** #130 머지 커밋 제목 · 내용. */
data class MergeCommitMessage(val title: String, val body: String)

/**
 * GitHub 머지 박스가 미리 채우는 커밋 제목 · 내용을 레포 설정대로 만든다. REBASE 는 메시지가 없어 null.
 *
 * - Merge 커밋 제목: MERGE_MESSAGE → `Merge pull request #N from {owner}/{headRefName}`, PR_TITLE → PR 제목
 * - Merge 커밋 내용: PR_BODY → PR 본문, PR_TITLE → PR 제목, BLANK → 빈 값
 * - Squash 제목: PR_TITLE → `{PR 제목} (#N)`, COMMIT_OR_PR_TITLE → 커밋이 하나면 그 제목 + ` (#N)`, 아니면 PR 제목 형태
 * - Squash 내용: PR_BODY → PR 본문, COMMIT_MESSAGES → 커밋마다 `* {제목}` (+ 들여쓴 본문), BLANK → 빈 값
 */
fun defaultMergeMessage(pr: MergeStateNode, method: MergeMethod): MergeCommitMessage? {
    val repo = pr.repository
    return when (method) {
        MergeMethod.MERGE -> MergeCommitMessage(
            title = when (repo.mergeCommitTitle) {
                "PR_TITLE" -> pr.title
                else -> "Merge pull request #${pr.number} from ${headOwner(pr)}/${pr.headRefName}"
            },
            body = when (repo.mergeCommitMessage) {
                "PR_BODY" -> pr.body
                "BLANK" -> ""
                else -> pr.title
            },
        )
        MergeMethod.SQUASH -> MergeCommitMessage(
            title = when (repo.squashMergeCommitTitle) {
                "PR_TITLE" -> "${pr.title} (#${pr.number})"
                else -> {
                    val single = pr.allCommits.nodes.singleOrNull()?.takeIf { pr.allCommits.totalCount <= 1 }
                    "${single?.commit?.let { splitMessage(it).first } ?: pr.title} (#${pr.number})"
                }
            },
            body = when (repo.squashMergeCommitMessage) {
                "PR_BODY" -> pr.body
                "BLANK" -> ""
                else -> commitMessagesBody(pr)
            },
        )
        MergeMethod.REBASE -> null
    }
}

private fun headOwner(pr: MergeStateNode): String =
    pr.headRepositoryOwner?.login ?: pr.author?.login ?: ""

/** `* 제목` 다음 줄부터 본문을 두 칸 들여쓰고, 커밋 사이는 빈 줄로 띄운다. */
private fun commitMessagesBody(pr: MergeStateNode): String =
    pr.allCommits.nodes.joinToString("\n\n") { edge ->
        val (title, rest) = splitMessage(edge.commit)
        val headline = "* $title"
        val body = rest.trim('\n')
        if (body.isBlank()) {
            headline
        } else {
            headline + "\n" + body.lines().joinToString("\n") { if (it.isBlank()) "" else "  $it" }
        }
    }

/**
 * GitHub 은 긴 첫 줄을 `…` 로 잘라 headline 끝과 body 앞에 나눠 담는다. 둘을 다시 이어 (제목, 본문) 으로 돌려준다.
 */
internal fun splitMessage(commit: MessageCommit): Pair<String, String> {
    val headline = commit.messageHeadline
    val body = commit.messageBody
    if (!headline.endsWith("…") || !body.startsWith("…")) return headline to body
    val rest = body.removePrefix("…")
    val firstLine = rest.substringBefore('\n')
    return (headline.removeSuffix("…") + firstLine) to rest.substringAfter('\n', "")
}
