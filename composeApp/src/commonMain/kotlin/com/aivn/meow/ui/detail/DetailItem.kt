package com.aivn.meow.ui.detail

import androidx.compose.ui.graphics.Color
import com.aivn.meow.data.DiscussionTarget
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.ItemKind
import com.aivn.meow.model.PrBranches
import com.aivn.meow.model.PullRequest
import com.aivn.meow.model.SectionItem
import com.aivn.meow.theme.MeowColors

/** #144 목록 행 오른쪽 · 상세 모달 제목 아래의 상태 칩. [dot] 이면 글자 앞에 같은 색 점을 찍는다 (CI). */
internal data class StatusChip(val text: String, val color: Color, val dot: Boolean = false)

/**
 * #144 1열 목록 행과 상세 모달이 함께 쓰는 항목 하나. 리뷰 대기 PR · 섹션 항목을 같은 모양으로 맞춘다.
 *
 * @param kindLabel 이슈 · PR 이 섞이는 탭(멘션 · 새 댓글)에서 레포 칩 옆에 붙는 `PR` / `Issue`. 아니면 null.
 * @param showDiscussion 모달에서 본문 · 댓글(· 리뷰) 탭을 보여줄지. false 면 본문만.
 * @param mergeable 모달 아래에 머지 바(#130)를 붙일지 (내 PR 현황).
 */
internal data class DetailItem(
    val url: String,
    val repo: String,
    val repoColor: Color,
    val repoFullName: String,
    val number: Int,
    val title: String,
    val author: String,
    val authorInitials: String,
    val updatedAtIso: String,
    val isPullRequest: Boolean,
    val body: String?,
    val chips: List<StatusChip>,
    val detail: String? = null,
    val kindLabel: StatusChip? = null,
    val showDiscussion: Boolean = false,
    val mergeable: Boolean = false,
    /** #168 PR 의 head → base. 이슈는 null. */
    val branches: PrBranches? = null,
) {
    val discussionTarget: DiscussionTarget
        get() = DiscussionTarget(
            url = url,
            repoFullName = repoFullName,
            number = number,
            isPullRequest = isPullRequest,
            updatedAtIso = updatedAtIso,
        )
}

internal fun PullRequest.toDetailItem(): DetailItem = DetailItem(
    url = url,
    repo = repo,
    repoColor = repoColor,
    repoFullName = repoFullName,
    number = number,
    title = title,
    author = author,
    authorInitials = authorInitials,
    updatedAtIso = updatedAtIso,
    isPullRequest = true,
    body = body,
    chips = buildList {
        ciChip(ci)?.let(::add)
        if (isDraft) add(StatusChip("초안", MeowColors.Grey))
        labels.forEach { add(StatusChip(it.text, it.color)) }
    },
    showDiscussion = true,
    branches = branches,
)

internal fun SectionItem.toDetailItem(showDiscussion: Boolean, showKind: Boolean, mergeable: Boolean): DetailItem = DetailItem(
    url = url,
    repo = repo,
    repoColor = repoColor,
    repoFullName = repoFullName,
    number = number,
    title = title,
    author = author,
    authorInitials = authorInitials,
    updatedAtIso = updatedAtIso,
    isPullRequest = kind == ItemKind.PullRequest,
    body = body,
    chips = badges.map { StatusChip(it.text, it.color) } + labels.map { StatusChip(it.text, it.color) },
    detail = detail,
    kindLabel = if (showKind) {
        if (kind == ItemKind.PullRequest) StatusChip("PR", MeowColors.Violet) else StatusChip("Issue", MeowColors.Teal)
    } else {
        null
    },
    showDiscussion = showDiscussion,
    mergeable = mergeable,
    branches = branches.takeIf { kind == ItemKind.PullRequest },
)

/** CI 체크가 아예 없는 PR 은 칩을 숨긴다 (내 PR 현황과 같은 규칙). */
private fun ciChip(status: CiStatus): StatusChip? = when (status) {
    CiStatus.Pass -> StatusChip("CI · 통과", MeowColors.Success, dot = true)
    CiStatus.Fail -> StatusChip("CI · 실패", MeowColors.Error, dot = true)
    CiStatus.Pending -> StatusChip("CI · 진행 중", MeowColors.Warning, dot = true)
    CiStatus.None -> null
}
