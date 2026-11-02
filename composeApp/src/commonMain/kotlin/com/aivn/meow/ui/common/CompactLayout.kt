package com.aivn.meow.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface

/**
 * #165 폰(아이폰) 레이아웃 여부. true 면 드로워 대신 하단 탭 바, 좁은 폭용 한 열 배치 · 16dp 여백을 쓴다.
 * 데스크톱은 기본값(false) 그대로라 지금 화면이 바뀌지 않는다.
 */
val LocalCompactLayout = staticCompositionLocalOf { false }

/** 폰 화면 좌우 여백. */
val CompactGutter = 16.dp

/**
 * #165 설정 화면(GitHub 토큰 · Microsoft · Supabase)을 여는 동작. 아이폰만 넘기고, 데스크톱은 null 이라
 * 폰 제목 줄의 ⚙︎ · 연결 안내 카드의 버튼이 나타나지 않는다.
 */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * #165 연결이 필요한 화면(GitHub 토큰 없음 등) 자리에 보여 주는 안내 카드.
 * [LocalOpenSettings] 가 있으면 설정 화면을 여는 [actionLabel] 버튼을 함께 보여 준다.
 */
@Composable
fun ConnectLaterCard(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    hint: String = "설정에서 연결해 주세요",
    actionLabel: String = "설정 열기",
) {
    val openSettings = LocalOpenSettings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(corner = 20.dp)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = emoji, fontSize = 28.sp)
        Text(
            text = title,
            style = MeowType.SectionHeading,
            color = MeowColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MeowType.Meta,
            color = MeowColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
        if (openSettings != null) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeowColors.Brand)
                    .clickable(onClick = openSettings)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                style = MeowType.Meta,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
