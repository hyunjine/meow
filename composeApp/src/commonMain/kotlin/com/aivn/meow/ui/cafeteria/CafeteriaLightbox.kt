package com.aivn.meow.ui.cafeteria

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.aivn.meow.cafeteria.CafeteriaPhoto
import com.aivn.meow.ui.common.OpenOriginalButton
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.number
import kotlinx.datetime.plus

private val Scrim = Color(0xED0B0D1F)
private val GlassButton = Color.White.copy(alpha = 0.12f)
private const val MAX_ZOOM = 5f

/** 창 전체를 덮는 위치(드로워 포함). */
private object FullWindowPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** 사진 · 메뉴표 크게 보기. 중식은 ← → 로 넘기고, 메뉴표는 스크롤로 확대 · 드래그로 이동한다. esc · 바깥 클릭으로 닫는다. */
@Composable
internal fun CafeteriaLightbox(
    target: LightboxTarget,
    onChange: (LightboxTarget) -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val photos: List<CafeteriaPhoto>
    val index: Int
    val title: String
    val subtitle: String
    when (target) {
        is LightboxTarget.Lunch -> {
            photos = target.day.lunch?.photos.orEmpty()
            index = target.index.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            val d = target.day.date
            title = "${d.month.number}월 ${d.day}일 ${d.dayLetter()}요일 중식"
            subtitle = target.day.lunch?.menu.orEmpty()
        }
        is LightboxTarget.Weekly -> {
            photos = listOfNotNull(target.post.photo)
            index = 0
            val m = target.monday
            val f = m.plus(4, DateTimeUnit.DAY)
            title = "주간 메뉴표"
            subtitle = listOfNotNull(target.post.label, "${m.month.number}/${m.day} – ${f.month.number}/${f.day}")
                .joinToString(" · ")
        }
    }
    val photo = photos.getOrNull(index)
    if (photo == null) {
        LaunchedEffect(target) { onClose() }
        return
    }
    val isLunch = target is LightboxTarget.Lunch
    fun go(delta: Int) {
        if (target !is LightboxTarget.Lunch || photos.size < 2) return
        onChange(target.copy(index = (index + delta + photos.size) % photos.size))
    }

    val focusRequester = remember { FocusRequester() }
    Popup(popupPositionProvider = FullWindowPosition, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Escape -> onClose().let { true }
                        Key.DirectionLeft -> go(-1).let { isLunch }
                        Key.DirectionRight -> go(1).let { isLunch }
                        else -> false
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        ) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp, vertical = 88.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val imageModifier = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxWidth = 1100.dp, maxHeight = 660.dp)
                    .aspectRatio(photo.ratio(default = if (isLunch) 4f / 3f else 1.4f))
                    .clip(RoundedCornerShape(10.dp))
                    // 이미지 위 클릭은 닫지 않는다.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                if (isLunch) {
                    KakaoImage(
                        url = photo.xlarge,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        placeholderUrls = listOf(photo.large, photo.medium),
                        modifier = imageModifier,
                    )
                    Text(text = "${index + 1} / ${photos.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Hint("← → 로 넘기기 · esc 로 닫기")
                } else {
                    ZoomableImage(photo = photo, contentDescription = title, modifier = imageModifier)
                    Hint("esc 로 닫기 · 스크롤/핀치로 확대")
                }
            }

            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OpenOriginalButton(onClick = { onOpenUrl(photo.xlarge) }, dark = true)
                CircleButton(text = "✕", size = 36, fontSize = 14, onClick = onClose)
            }

            if (isLunch && photos.size > 1) {
                CircleButton(
                    text = "‹",
                    size = 48,
                    fontSize = 24,
                    onClick = { go(-1) },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp),
                )
                CircleButton(
                    text = "›",
                    size = 48,
                    fontSize = 24,
                    onClick = { go(1) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
}

@Composable
private fun CircleButton(text: String, size: Int, fontSize: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(GlassButton).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

/** 스크롤로 확대(커서 기준) · 드래그로 이동 · 더블클릭으로 원래 크기. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ZoomableImage(photo: CafeteriaPhoto, contentDescription: String, modifier: Modifier) {
    var scale by remember(photo) { mutableFloatStateOf(1f) }
    var offset by remember(photo) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .pointerInput(photo) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val delta = change.scrollDelta.y
                        if (delta == 0f) continue
                        val newScale = (scale * (if (delta < 0) 1.12f else 1f / 1.12f)).coerceIn(1f, MAX_ZOOM)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val p = change.position - center
                        // 커서 아래 지점이 제자리에 있도록 이동량을 맞춘다.
                        offset = if (newScale == 1f) Offset.Zero else p - (p - offset) * (newScale / scale)
                        scale = newScale
                        change.consume()
                    }
                }
            }
            .pointerInput(photo) {
                detectDragGestures { change, drag ->
                    if (scale > 1f) {
                        change.consume()
                        offset += drag
                    }
                }
            }
            .pointerInput(photo) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                })
            },
    ) {
        KakaoImage(
            url = photo.xlarge,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            placeholderUrls = listOf(photo.large, photo.medium),
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}
