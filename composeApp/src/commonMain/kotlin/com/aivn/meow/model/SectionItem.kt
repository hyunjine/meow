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
)
