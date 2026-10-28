package com.aivn.meow.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.PillChip
import com.aivn.meow.ui.StatusChipView
import com.aivn.meow.ui.card.DetailBody
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.common.CompactGutter
import com.aivn.meow.ui.common.LocalCompactLayout
import com.aivn.meow.ui.common.OpenOriginalButton
import com.aivn.meow.ui.common.consumeClicks
import com.aivn.meow.ui.pr.MergeFooter
import com.aivn.meow.util.relativeTime

// #144 Figma 4867:79579 'GitHub 상세 — A. 가운데 모달'.
private val Scrim = Color(0x660B0D1F)
private val ModalShadow = Color(0x330B0D1F)
private val ModalShape = RoundedCornerShape(22.dp)
private val ModalWidth = 760.dp
private val ModalVerticalMargin = 120.dp
private val ModalMinHeight = 360.dp

/** 창 전체(드로워 포함)를 덮는 위치. */
private object FullWindowPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/**
 * #144 목록 항목의 상세를 창 가운데 모달로 보여준다.
 * 헤더(레포 · `#번호 · @작성자 · 시각` · 원본 보기 · 닫기) / 제목 / 상태 칩 / 본문 · 댓글 · 리뷰 탭(내용만 스크롤) / 머지 바.
 * Esc · 바깥(어두운 영역) 클릭으로 닫고, ← → · ↑ ↓ 로 이전 · 다음 항목으로 넘긴다.
 * 머지 확인창은 이 모달 안에서 띄운 Popup 이라 모달 위에 그려진다.
 */
@Composable
internal fun DetailModal(
    item: DetailItem,
    onClose: () -> Unit,
    onOpenOriginal: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    Popup(
        popupPositionProvider = FullWindowPosition,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Escape -> onClose().let { true }
                        Key.DirectionLeft, Key.DirectionUp -> onPrevious().let { true }
                        Key.DirectionRight, Key.DirectionDown -> onNext().let { true }
                        else -> false
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            // #155 항목을 넘길 때마다 다시 포커스를 가져온다 — 포커스를 가진 선택 영역(머지 바 문구)이 사라져도 Esc · ← → 가 계속 동작하도록.
            LaunchedEffect(item.url) { focusRequester.requestFocus() }
            // #165 폰에서는 화면 전체를 덮는 시트로 띄우고, 노치 · 홈 인디케이터(safe area) 안쪽에 내용을 둔다.
            val compact = LocalCompactLayout.current
            val sheetModifier = if (compact) {
                Modifier
                    .fillMaxSize()
                    .background(MeowColors.Surface)
                    .consumeClicks()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = CompactGutter, vertical = 12.dp)
            } else {
                val width = ModalWidth.coerceAtMost(maxWidth - 48.dp)
                val height = (maxHeight - ModalVerticalMargin).coerceAtLeast(ModalMinHeight).coerceAtMost(maxHeight)
                Modifier
                    .width(width)
                    .height(height)
                    .shadow(elevation = 40.dp, shape = ModalShape, ambientColor = ModalShadow, spotColor = ModalShadow)
                    .background(MeowColors.Surface, ModalShape)
                    .clip(ModalShape)
                    // 카드 안 클릭이 어두운 영역으로 전달돼 닫히지 않도록 소비한다.
                    // #161 clickable 로 막으면 누를 때 카드가 포커스를 가져가 선택 영역의 ⌘C 복사가 안 되므로 누름만 소비한다.
                    .consumeClicks()
                    .padding(horizontal = 28.dp, vertical = 24.dp)
            }
            Box(modifier = sheetModifier) {
                // #155 제목 · 메타 · 본문 · 댓글 · 리뷰를 드래그로 골라 ⌘C 로 복사할 수 있게 한다.
                // 글자를 누르면 선택 컨테이너가 포커스를 가져가 ⌘C 를 받고, 다른 키는 바깥 Box 의 onKeyEvent 로 올라가 Esc · ← → 가 그대로 동작한다.
                // 버튼 · 탭 · 머지 바처럼 누르는 곳은 DisableSelection 으로 감싸 클릭이 선택에 먹히지 않게 한다.
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    ModalContent(item = item, onOpenOriginal = onOpenOriginal, onClose = onClose)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalContent(item: DetailItem, onOpenOriginal: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(item = item, onOpenOriginal = onOpenOriginal, onClose = onClose)
        Text(
            text = item.title,
            color = MeowColors.TextPrimary,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val chips = listOfNotNull(item.kindLabel) + item.chips
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { StatusChipView(it) }
            }
        }
        item.detail?.let { detail ->
            Text(text = detail, style = MeowType.Meta, color = MeowColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DetailBody(target = item.discussionTarget, body = item.body, showDiscussion = item.showDiscussion)
        if (item.mergeable) {
            // 머지 바 · 확인창(Popup)은 선택 대상에서 뺀다. 상태 문구는 MergeFooter 안에서 따로 선택할 수 있다.
            DisableSelection {
                MergeFooter(prUrl = item.url, prNumber = item.number, prTitle = item.title)
            }
        }
    }
}

@Composable
private fun Header(item: DetailItem, onOpenOriginal: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillChip(item.repo, item.repoColor, small = true)
        Text(
            text = "#${item.number} · @${item.author} · ${relativeTime(item.updatedAtIso)}",
            modifier = Modifier.weight(1f),
            style = MeowType.Meta,
            color = MeowColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OpenOriginalButton(onClick = onOpenOriginal)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = MeowColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
