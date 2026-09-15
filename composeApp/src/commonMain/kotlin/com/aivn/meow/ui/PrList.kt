package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface

@Composable
fun PrListCard(
    pullRequests: List<PullRequest>,
    sortLabel: String,
    includeDraft: Boolean,
    onRefresh: () -> Unit,
    onSortClick: () -> Unit,
    onToggleDraft: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glassSurface(corner = 28.dp)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PrToolbar(
            count = pullRequests.size,
            sortLabel = sortLabel,
            includeDraft = includeDraft,
            onRefresh = onRefresh,
            onSortClick = onSortClick,
            onToggleDraft = onToggleDraft,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pullRequests.forEach { pr ->
                PrRow(pr = pr, onOpen = { onOpenPr(pr) })
            }
        }
    }
}

@Composable
private fun PrToolbar(
    count: Int,
    sortLabel: String,
    includeDraft: Boolean,
    onRefresh: () -> Unit,
    onSortClick: () -> Unit,
    onToggleDraft: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "리뷰 대기 PR",
                color = MeowColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MeowColors.Brand.copy(alpha = 0.16f))
                    .border(1.dp, MeowColors.Brand.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = count.toString(),
                    color = MeowColors.Brand,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassChip(text = "정렬: $sortLabel", trailingArrow = true, onClick = onSortClick)
            GlassChip(
                text = "Draft 포함",
                active = includeDraft,
                onClick = onToggleDraft,
            )
            GlassChip(
                text = "새로고침",
                leading = { Icon(Icons.Default.Refresh, null, tint = MeowColors.TextPrimary, modifier = Modifier.size(14.dp)) },
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun GlassChip(
    text: String,
    active: Boolean = false,
    trailingArrow: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg = if (active) MeowColors.Brand.copy(alpha = 0.22f) else MeowColors.GlassSurfaceStrong
    val stroke = if (active) MeowColors.Brand.copy(alpha = 0.55f) else MeowColors.GlassBorder
    val textColor = if (active) MeowColors.Brand else MeowColors.TextPrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (trailingArrow) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MeowColors.TextPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun PrRow(pr: PullRequest, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .glassSurface(corner = 20.dp, fill = MeowColors.GlassSurface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(pr.repoColor.copy(alpha = 0.18f))
                .border(1.dp, pr.repoColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = pr.authorInitials,
                color = pr.repoColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillChip(pr.repo, pr.repoColor)
                DotSeparator()
                Text(
                    text = "@${pr.author}",
                    color = MeowColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                DotSeparator()
                Text(
                    text = pr.relativeTime,
                    color = MeowColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pr.title,
                    color = MeowColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "#${pr.number}",
                    color = MeowColors.TextTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pr.isDraft) PillChip("Draft", MeowColors.Grey)
                CiChip(pr.ci)
                pr.labels.forEach { label -> PillChip(label.text, label.color) }
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MeowColors.GlassSurfaceStrong)
                .border(1.dp, MeowColors.GlassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MeowColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PillChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CiChip(status: CiStatus) {
    val (label, color) = when (status) {
        CiStatus.Pass -> "CI · 통과" to MeowColors.Success
        CiStatus.Fail -> "CI · 실패" to MeowColors.Error
        CiStatus.Pending -> "CI · 진행중" to MeowColors.Warning
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DotSeparator() {
    Text(text = "·", color = MeowColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
