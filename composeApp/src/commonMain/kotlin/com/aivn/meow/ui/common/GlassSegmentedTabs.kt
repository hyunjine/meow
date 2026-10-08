package com.aivn.meow.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors

/** 선택된 라벨 색 (iOS system blue 계열 브랜드 블루). */
private val SelectedLabel = Color(0xFF3D5EFF)
private val Label = Color(0xFF1A1A1A)

/** 유리 캡슐: 위는 조금 더 밝고 아래는 조금 더 비치는 반투명 흰색. */
private val GlassFill = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.50f)),
)

/** 위쪽 가장자리에 맺히는 하이라이트 → 아래로 갈수록 옅어지는 테두리. */
private val GlassRim = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.06f)),
)
private val GlassShadow = Color(0x261A1F4D)

/** 선택 캡슐: 유리 안에 한 겹 더 짙게 비치는 캡슐. */
private val ThumbFill = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.075f), Color.Black.copy(alpha = 0.10f)),
)
private val ThumbRim = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.04f), Color.White.copy(alpha = 0.55f)),
)
private val HoverTint = Color.Black.copy(alpha = 0.04f)

private val BarHeight = 44.dp
private val BarPadding = 4.dp

/**
 * iOS 26 'Liquid Glass' 탭 바 느낌의 세그먼트 탭.
 *
 * Compose Desktop 1.7 에는 배경을 굴절시키는 셰이더 라이브러리(liquid)를 쓸 수 없어서(Compose 1.9+/Kotlin 2.2+ 필요)
 * 반투명 흰색 그라데이션 + 위쪽 하이라이트 테두리 + 부드러운 그림자로 유리 질감을 흉내 낸다.
 * 세그먼트는 같은 폭으로 나누고, 선택 캡슐은 스프링 애니메이션으로 미끄러진다.
 * [backdrop] 은 탭 바 뒤의 바탕색 — 유리 아래에 한 겹 깔아 그림자가 비쳐 보이지 않게 한다.
 */
@Composable
fun GlassSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Color = MeowColors.Background,
) {
    if (labels.isEmpty()) return
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .shadow(elevation = 10.dp, shape = CircleShape, clip = false, ambientColor = GlassShadow, spotColor = GlassShadow)
            .clip(CircleShape)
            // 그림자가 반투명 유리 안으로 비쳐 보이지 않도록 바탕색을 한 겹 깔고 그 위에 유리를 올린다
            .background(backdrop)
            .background(GlassFill)
            .border(1.dp, GlassRim, CircleShape)
            .padding(BarPadding),
    ) {
        val segmentWidth = maxWidth / labels.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex.coerceIn(0, labels.lastIndex),
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "glassThumbOffset",
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(ThumbFill)
                .border(0.5.dp, ThumbRim, CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                GlassSegment(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.width(segmentWidth).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun GlassSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(if (selected) SelectedLabel else Label, label = "glassLabel")
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (hovered && !selected) HoverTint else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
