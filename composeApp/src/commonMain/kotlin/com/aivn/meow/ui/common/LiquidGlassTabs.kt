package com.aivn.meow.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs

/** 선택된 라벨 색 (브랜드 블루). */
private val SelectedLabel = Color(0xFF3D5EFF)
private val Label = Color(0xFF1A1A1A)

/** 유리 바 위에 얹는 옅은 흰 막 — 뒤 콘텐츠는 비치되 라벨이 읽히도록. */
private val BarSurface = Color(0xFFFAFAFA).copy(alpha = 0.45f)

/** 선택 캡슐: 유리 안에서 한 톤 짙게 비치는 캡슐 (iOS 26 Tab Bar 선택 상태). */
private val ThumbSurface = Color.Black.copy(alpha = 0.07f)
private val HoverTint = Color.Black.copy(alpha = 0.035f)

val LiquidGlassTabsHeight: Dp = 44.dp
private val InnerPadding = 4.dp

/**
 * iOS 26 'Liquid Glass' 탭 바 (Figma: Tab Bar - iPad) 를 Kyant0 backdrop 라이브러리로 그린 세그먼트 탭.
 *
 * - 바: [backdrop] (탭 바 뒤 콘텐츠를 `layerBackdrop` 으로 기록한 레이어) 을 vibrancy → blur → lens 순으로
 *   채도 · 흐림 · 가장자리 굴절을 걸어 캡슐 안에 그리고, 위에 옅은 흰 막과 하이라이트 · 그림자를 얹는다.
 * - 선택 캡슐(thumb): 바의 유리 결과를 다시 backdrop 으로 받아(`exportedBackdrop`) 한 톤 짙게 비치는 유리 캡슐.
 *   요일을 바꾸면 스프링으로 미끄러지고, 움직이는 동안 렌즈 굴절 · 색수차 · 하이라이트가 살아나며 살짝 부푼다.
 *
 * 뒤 콘텐츠가 비치려면 호출하는 쪽에서 이 탭 바를 [backdrop] 기록 대상 바깥(형제 레이어)에 두어야 한다.
 */
@Composable
fun LiquidGlassTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val target = selectedIndex.coerceIn(0, labels.lastIndex)
    // 선택 캡슐의 위치(세그먼트 인덱스 단위, 소수). 처음엔 애니메이션 없이 제자리.
    val position = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        position.animateTo(target.toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    // 0(멈춤) … 1(빠르게 이동 중). 움직일 때만 thumb 의 굴절 · 하이라이트를 키운다.
    val motion = { (abs(position.velocity) / 6f).coerceIn(0f, 1f) }
    val barBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(LiquidGlassTabsHeight)) {
        val segmentWidth = (maxWidth - InnerPadding * 2) / labels.size

        // 유리 바
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(6.dp.toPx())
                        lens(refractionHeight = 14.dp.toPx(), refractionAmount = 20.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.9f) },
                    shadow = { Shadow(radius = 18.dp, color = Color(0xFF1A1F4D).copy(alpha = 0.10f)) },
                    exportedBackdrop = barBackdrop,
                    onDrawSurface = { drawRect(BarSurface) },
                ),
        )

        // 선택 캡슐
        Box(
            Modifier
                .padding(InnerPadding)
                .graphicsLayer { translationX = position.value * segmentWidth.toPx() }
                .width(segmentWidth)
                .fillMaxHeight()
                .drawBackdrop(
                    backdrop = barBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val m = motion()
                        lens(
                            refractionHeight = 6.dp.toPx() + 6.dp.toPx() * m,
                            refractionAmount = 8.dp.toPx() + 10.dp.toPx() * m,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = lerp(0.35f, 1f, motion())) },
                    shadow = null,
                    innerShadow = { InnerShadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.06f)) },
                    layerBlock = {
                        val scale = lerp(1f, 1.06f, motion())
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(ThumbSurface) },
                ),
        )

        // 라벨
        Row(Modifier.fillMaxSize().padding(InnerPadding)) {
            labels.forEachIndexed { index, label ->
                GlassSegment(
                    label = label,
                    selected = index == target,
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
            .clip(Capsule())
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
