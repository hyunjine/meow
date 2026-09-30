package com.aivn.meow.model

import androidx.compose.ui.graphics.Color

/** [None] 은 CI 체크 자체가 없는 PR (statusCheckRollup == null) — 칩을 렌더하지 않는다. */
enum class CiStatus { Pass, Fail, Pending, None }

data class Label(val text: String, val color: Color)

data class PullRequest(
    val repo: String,
    val repoColor: Color,
    val number: Int,
    val title: String,
    val author: String,
    val authorInitials: String,
    val relativeTime: String,
    val updatedAtIso: String,
    val isDraft: Boolean,
    val ci: CiStatus,
    val labels: List<Label>,
    val url: String,
)
