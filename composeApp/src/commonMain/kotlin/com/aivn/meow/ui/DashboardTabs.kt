package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface

/** 탭 칩 하나에 표시할 내용. [hasNew] 면 칩 오른쪽 위에 빨간 점을 찍는다. */
internal data class TabChipInfo(val label: String, val count: Int, val hasNew: Boolean)

/** 좌: 탭 칩들, 우: 선택 탭의 도구. 폭이 모자라면 도구가 다음 줄로 내려간다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardTabBar(
    tabs: List<TabChipInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tools: @Composable RowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                TabChip(tab = tab, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = tools,
        )
    }
}

@Composable
private fun TabChip(tab: TabChipInfo, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box {
        Row(
            modifier = Modifier
                .then(
                    if (selected) {
                        val glow = MeowColors.Brand.copy(alpha = 0.25f)
                        Modifier.shadow(8.dp, shape, clip = false, ambientColor = glow, spotColor = glow)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(if (selected) MeowColors.Brand else MeowColors.Surface)
                .border(1.dp, if (selected) MeowColors.Brand else MeowColors.GlassBorder, shape)
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tab.label,
                color = if (selected) Color.White else MeowColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else MeowColors.Brand.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = tab.count.toString(),
                    color = if (selected) Color.White else MeowColors.Brand,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (tab.hasNew) {
            // 칩 모서리에 살짝 걸치는 새 항목 표시
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MeowColors.Error)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/**
 * [items] 를 2열로 배치한다. 폭이 [OneColumnBelow] 보다 좁으면 1열로 쌓는다.
 * 상위 verticalScroll 안에서 쓰므로 Lazy 그리드 대신 열 수만큼 Row 로 묶는다.
 * [cell] 은 목록 인덱스 · 항목 · 칸 폭 modifier 를 받는다.
 */
@Composable
internal fun <T> TwoColumnGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    spacing: Dp = 24.dp,
    cell: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = if (maxWidth < OneColumnBelow) 1 else 2
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Top,
                ) {
                    row.forEachIndexed { column, item ->
                        cell(rowIndex * columns + column, item, Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 이보다 좁으면 카드 한 장이 너무 좁아져 1열로 바꾼다. */
private val OneColumnBelow = 640.dp

/** 그리드 자리에 표시하는 빈 상태 · 오류 카드. */
@Composable
internal fun EmptyStateCard(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    titleColor: Color = MeowColors.TextPrimary,
) {
    Column(
        modifier = modifier
            .glassSurface(corner = 20.dp)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
