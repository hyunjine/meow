package com.aivn.meow.ui.sections

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.SectionResult
import com.aivn.meow.model.ItemKind
import com.aivn.meow.model.SectionItem
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.DotSeparator
import com.aivn.meow.ui.PillChip
import com.aivn.meow.util.relativeTime

/** 제목 + 개수 뱃지 + 항목 리스트 + 빈 상태를 갖는 공통 섹션 카드. [PrListCard][com.aivn.meow.ui.PrListCard] 와 같은 스타일. */
@Composable
fun SectionCard(
    result: SectionResult,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val section = result.section
    Column(
        modifier = modifier
            .glassSurface(corner = 28.dp)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                color = MeowColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            CountBadge(count = result.totalCount)
        }

        result.errorMessage?.let { message ->
            Text(
                text = "불러오지 못했어요 · $message",
                color = MeowColors.Error,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result.items.isEmpty()) {
                // 첫 로딩부터 실패했으면 위 오류 문구만으로 충분하므로 빈 상태는 생략
                if (result.errorMessage == null) SectionEmptyState(section.emptyTitle, section.emptyHint)
            } else {
                result.items.forEach { item ->
                    SectionRow(item = item, onOpen = { onOpenUrl(item.url) })
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
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

@Composable
private fun SectionEmptyState(title: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = MeowColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = hint,
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionRow(item: SectionItem, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .glassSurface(corner = 20.dp, fill = MeowColors.GlassSurface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(item.repoColor.copy(alpha = 0.18f))
                .border(1.dp, item.repoColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.authorInitials,
                color = item.repoColor,
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
                PillChip(item.repo, item.repoColor)
                PillChip(
                    text = if (item.kind == ItemKind.PullRequest) "PR" else "Issue",
                    color = if (item.kind == ItemKind.PullRequest) MeowColors.Violet else MeowColors.Teal,
                )
                DotSeparator()
                Text(
                    text = "@${item.author}",
                    color = MeowColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                DotSeparator()
                Text(
                    text = relativeTime(item.updatedAtIso),
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
                    text = item.title,
                    color = MeowColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "#${item.number}",
                    color = MeowColors.TextTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item.detail?.let { detail ->
                Text(
                    text = detail,
                    color = MeowColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.badges.isNotEmpty() || item.labels.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.badges.forEach { badge -> PillChip(badge.text, badge.color) }
                    item.labels.forEach { label -> PillChip(label.text, label.color) }
                }
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
