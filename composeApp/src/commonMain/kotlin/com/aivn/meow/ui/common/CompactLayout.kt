package com.aivn.meow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * #165 아이폰에서 아직 쓸 수 없는 기능(로그인이 필요한 화면) 자리에 보여 주는 안내 카드.
 * 로그인은 2단계에서 설정 화면과 함께 들어온다.
 */
@Composable
fun ConnectLaterCard(emoji: String, title: String, modifier: Modifier = Modifier) {
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
            text = "설정에서 연결해 주세요 (2단계 예정)",
            style = MeowType.Meta,
            color = MeowColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
