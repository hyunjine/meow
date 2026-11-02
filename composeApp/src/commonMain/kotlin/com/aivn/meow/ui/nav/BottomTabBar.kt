package com.aivn.meow.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors

/**
 * #165 아이폰용 하단 탭 바. 데스크톱 드로워와 같은 메뉴([AppDrawerItems])를 아이콘 + 라벨로 보여 준다.
 * 홈 인디케이터 영역(아래 safe area)은 라벨 아래가 너무 비지 않도록 [HomeIndicatorOverlap] 만큼 겹쳐 쓴다
 * (인디케이터는 라벨보다 아래에 그려져 겹치지 않는다).
 */
@Composable
fun BottomTabBar(
    selected: AppScreen,
    onSelect: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
    settingsSelected: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
) {
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomPadding = (bottomInset - HomeIndicatorOverlap).coerceAtLeast(MinBottomPadding)
    Column(modifier = modifier.fillMaxWidth().background(MeowColors.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MeowColors.GlassBorder))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = TopPadding, bottom = bottomPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AppDrawerItems.forEach { item ->
                TabItem(
                    label = item.label,
                    icon = item.icon(),
                    isSelected = !settingsSelected && item.screen == selected,
                    onClick = { onSelect(item.screen) },
                )
            }
            // #165 설정: 각 화면 제목 줄의 ⚙︎ 대신 하단 탭 맨 끝 칸.
            if (onOpenSettings != null) {
                TabItem(
                    label = "설정",
                    icon = rememberVectorPainter(Icons.Outlined.Settings),
                    isSelected = settingsSelected,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: Painter, isSelected: Boolean, onClick: () -> Unit) {
    val tint = if (isSelected) MeowColors.Brand else MeowColors.TextTertiary
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(painter = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** 탭 바 위쪽 여백. */
private val TopPadding = 12.dp

/** 아래 safe area(홈 인디케이터, 보통 34pt) 중 탭 바가 겹쳐 쓰는 높이. 라벨 아래도 여유 있게 조금만 겹친다. */
private val HomeIndicatorOverlap = 4.dp

/** 홈 버튼 기종처럼 아래 safe area 가 없을 때의 최소 아래 여백. */
private val MinBottomPadding = 12.dp
