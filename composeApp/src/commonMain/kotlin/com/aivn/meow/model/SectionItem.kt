package com.aivn.meow.model

import androidx.compose.ui.graphics.Color

enum class ItemKind { Issue, PullRequest }

/** 대시보드 보조 섹션(할당 이슈 · 멘션 등)에 표시되는 이슈 / PR 한 줄. */
data class SectionItem(
    val kind: ItemKind,
    val repo: String,
    val repoColor: Color,
    val number: Int,
    val title: String,
    val author: String,
    val authorInitials: String,
    /** 상대 시각 표시 기준. 댓글 섹션처럼 다른 시각을 쓰려면 매핑 시 덮어쓴다. */
    val updatedAtIso: String,
    val labels: List<Label>,
    val url: String,
    /** 라벨 앞에 붙는 섹션 전용 상태 칩 (예: 리뷰 상태, CI). */
    val badges: List<Label> = emptyList(),
    /** 제목 아래 한 줄 보조 문구 (예: 댓글 요약). */
    val detail: String? = null,
    /** 카드를 펼쳤을 때 표시하는 본문 (plain text). 새 댓글 섹션은 댓글 전문. */
    val body: String? = null,
    /** 내 PR 현황의 GitHub reviewDecision (APPROVED 등). 상태 변화 알림 비교용, 다른 섹션은 null. */
    val reviewState: String? = null,
    /** #84 새 댓글 섹션 전용 출처 · 작성자 · 멘션 여부. 다른 섹션은 null. */
    val comment: CommentMeta? = null,
    /** #127 `owner/name` (예: `hyunjine/meow`). 사이드바 체크 필터 기준. [repo] 는 표시용 짧은 이름. */
    val repoFullName: String = repo,
)

/** #84 새 댓글이 달린 곳. [label] 은 카드의 출처 칩 문구. */
enum class CommentSource(val label: String) {
    MyIssue("내 이슈"),
    MyPr("내 PR"),
    /** 내 PR 에 'Comment' 로 제출된 리뷰. */
    PrReview("Comment 리뷰"),
    /** 남의 이슈 · PR 중 내가 댓글을 단 스레드. */
    Thread("참여한 스레드"),
}

/**
 * #84 새 댓글 항목의 알림 판단용 정보.
 * [threadUrl] 은 이슈 · PR url (항목 url 은 댓글 url), [mentionsMe] 면 새 댓글 대신 멘션으로 한 번만 알린다.
 */
data class CommentMeta(
    val source: CommentSource,
    val commenter: String,
    val threadUrl: String,
    /** 댓글 작성 시각 (ISO-8601). Realtime 항목은 알 수 없어 null. */
    val createdAtIso: String? = null,
    val mentionsMe: Boolean = false,
)
