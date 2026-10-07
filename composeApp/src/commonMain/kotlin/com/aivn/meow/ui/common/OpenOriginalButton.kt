package com.aivn.meow.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors

private val ButtonShape = RoundedCornerShape(10.dp)
private val LightHover = Color(0xFFF5F6FA)

/**
 * 외부 원본(GitHub · 주간보고 문서 · 식단 게시물 등)을 여는 공통 '원본 보기' 테두리 버튼.
 * [dark] 는 라이트박스처럼 어두운 배경 위에서 쓰는 반투명 흰색 버전.
 */
@Composable
fun OpenOriginalButton(onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        dark -> Color.White.copy(alpha = if (hovered) 0.18f else 0.12f)
        hovered -> LightHover
        else -> MeowColors.Surface
    }
    val border = if (dark) Color.White.copy(alpha = 0.20f) else MeowColors.GlassBorder
    val textColor = if (dark) Color.White else MeowColors.TextPrimary
    val iconColor = if (dark) Color.White else MeowColors.TextTertiary

    Row(
        modifier = modifier
            .clip(ButtonShape)
            .background(background)
            .border(1.dp, border, ButtonShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "원본 보기", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
    }
}
