package com.aivn.meow.model

import androidx.compose.ui.graphics.Color

enum class CiStatus { Pass, Fail, Pending }

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
