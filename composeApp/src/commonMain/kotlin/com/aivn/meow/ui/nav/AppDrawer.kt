package com.aivn.meow.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.loadImageBitmap
import meow.composeapp.generated.resources.Res
import meow.composeapp.generated.resources.app_icon
import meow.composeapp.generated.resources.github_mark
import org.jetbrains.compose.resources.painterResource

/** 드로워로 고르는 화면. */
enum class AppScreen { GITHUB, WEEKLY_REPORT, SCHEDULE, CAFETERIA }

data class DrawerItem(val screen: AppScreen, val label: String, val icon: @Composable () -> Painter)

/** 드로워 메뉴. 새 화면은 항목을 여기에 더한다. */
val AppDrawerItems = listOf(
    DrawerItem(AppScreen.GITHUB, "GitHub") { painterResource(Res.drawable.github_mark) },
    DrawerItem(AppScreen.WEEKLY_REPORT, "주간 보고") { rememberVectorPainter(Icons.Outlined.Description) },
    DrawerItem(AppScreen.SCHEDULE, "일정") { rememberVectorPainter(Icons.Outlined.CalendarMonth) },
    DrawerItem(AppScreen.CAFETERIA, "구내 식당") { rememberVectorPainter(Icons.Outlined.Restaurant) },
)

private val DrawerBackground = Color(0xFF2B2F55)
/** 드로워 폭. 각 화면 오른쪽 여백도 이 값을 쓴다. */
val DrawerWidth = 256.dp

/** 창 왼쪽에 항상 열린 메뉴 드로워. 화면별 정보(개수 · 동기화 상태)는 두지 않는다. */
@Composable
fun AppDrawer(
    selected: AppScreen,
    onSelect: (AppScreen) -> Unit,
    viewerLogin: String?,
    viewerInitials: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DrawerWidth)
            .fillMaxHeight()
            .background(DrawerBackground)
            .padding(horizontal = 12.dp, vertical = 18.dp),
    ) {
        Workspace()
        Spacer(Modifier.height(20.dp))
        AppDrawerItems.forEach { item ->
            DrawerMenuItem(item = item, selected = item.screen == selected, onClick = { onSelect(item.screen) })
        }
        Spacer(Modifier.weight(1f))
        AccountRow(login = viewerLogin, initials = viewerInitials, avatarUrl = avatarUrl)
    }
}

@Composable
private fun Workspace() {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column {
            Text(text = "Meow", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Team-AIVN",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(item: DrawerItem, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) Color.White else Color.White.copy(alpha = 0.75f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MeowColors.Brand),
            )
        }
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = item.icon(), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(
                text = item.label,
                color = content,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AccountRow(login: String?, initials: String?, avatarUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(avatarUrl = avatarUrl, initials = initials.orEmpty(), size = 32.dp)
        Text(
            text = login.orEmpty(),
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 설정 화면은 아직 없다
        Icon(
            Icons.Outlined.Settings,
            contentDescription = "설정",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 원형 GitHub 아바타. 로딩 중이거나 실패하면 이니셜 원으로 폴백. */
@Composable
private fun UserAvatar(avatarUrl: String?, initials: String, size: Dp) {
    val avatar by produceState<ImageBitmap?>(initialValue = null, avatarUrl) {
        value = avatarUrl?.takeIf { it.isNotBlank() }?.let { loadImageBitmap(it) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MeowColors.Brand),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = avatar
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "GitHub 아바타",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = initials, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
