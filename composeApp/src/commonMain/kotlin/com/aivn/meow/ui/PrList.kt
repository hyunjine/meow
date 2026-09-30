package com.aivn.meow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    sortOption: PrSortOption,
    includeDraft: Boolean,
    onRefresh: () -> Unit,
    onSortSelect: (PrSortOption) -> Unit,
    onToggleDraft: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    modifier: Modifier = Modifier,
    // 키보드 네비게이션으로 현재 포커스된 행의 인덱스 (없으면 -1)
    selectedIndex: Int = -1,
) {
    Column(
        modifier = modifier
            .glassSurface(corner = 28.dp)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PrToolbar(
            count = pullRequests.size,
            sortOption = sortOption,
            includeDraft = includeDraft,
            onRefresh = onRefresh,
            onSortSelect = onSortSelect,
            onToggleDraft = onToggleDraft,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pullRequests.isEmpty()) {
                EmptyPrState(includeDraft = includeDraft)
            } else {
                pullRequests.forEachIndexed { index, pr ->
                    PrRow(pr = pr, isSelected = index == selectedIndex, onOpen = { onOpenPr(pr) })
                }
            }
        }
    }
}

@Composable
private fun EmptyPrState(includeDraft: Boolean) {
    // 리뷰 요청 PR 이 0건일 때 표시되는 빈 상태
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "리뷰 요청이 없습니다 🎉",
            color = MeowColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            // Draft 필터로 인해 0건이 된 경우를 자연스럽게 안내
            text = if (includeDraft) {
                "여유로운 하루 보내세요"
            } else {
                "Draft PR 은 숨겨져 있어요 · 'Draft 포함'을 켜면 보일 수 있어요"
            },
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrToolbar(
    count: Int,
    sortOption: PrSortOption,
    includeDraft: Boolean,
    onRefresh: () -> Unit,
    onSortSelect: (PrSortOption) -> Unit,
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
            SortChip(sortOption = sortOption, onSortSelect = onSortSelect)
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

/** 정렬 칩. 클릭하면 세 정렬 옵션 중 하나를 고르는 드롭다운 메뉴가 뜬다. */
@Composable
private fun SortChip(sortOption: PrSortOption, onSortSelect: (PrSortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GlassChip(
            text = "정렬: ${sortOption.label}",
            trailingArrow = true,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = MeowColors.Surface,
            border = BorderStroke(1.dp, MeowColors.GlassBorder),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            PrSortOption.entries.forEach { option ->
                val isSelected = option == sortOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (isSelected) MeowColors.Brand else MeowColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = MeowColors.Brand,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSortSelect(option)
                        expanded = false
                    },
                )
            }
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
            .clickable(onClick = onClick)
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
private fun PrRow(pr: PullRequest, isSelected: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .glassSurface(
                corner = 20.dp,
                fill = MeowColors.GlassSurface,
                // 키보드로 선택된 행은 브랜드 컬러 테두리로 포커스를 표시
                borderColor = if (isSelected) MeowColors.Brand else MeowColors.GlassBorder,
                borderWidth = if (isSelected) 2.dp else 1.dp,
            )
            .clickable(onClick = onOpen)
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
internal fun PillChip(text: String, color: Color) {
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
        // CI 체크가 아예 없는 PR: MyPrStatusSection 과 동일하게 칩을 숨긴다.
        CiStatus.None -> return
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
internal fun DotSeparator() {
    Text(text = "·", color = MeowColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
