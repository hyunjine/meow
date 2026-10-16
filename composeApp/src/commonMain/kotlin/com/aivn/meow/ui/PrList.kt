package com.aivn.meow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.detail.DetailItem
import com.aivn.meow.ui.detail.StatusChip
import com.aivn.meow.util.relativeTime

/** 정렬 칩. 클릭하면 세 정렬 옵션 중 하나를 고르는 드롭다운 메뉴가 뜬다. */
@Composable
internal fun SortChip(sortOption: PrSortOption, onSortSelect: (PrSortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GlassChip(
            text = "정렬: ${sortOption.label}",
            trailingArrow = true,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = MeowColors.Surface,
            border = BorderStroke(1.dp, MeowColors.GlassBorder),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            PrSortOption.entries.forEach { option ->
                val isSelected = option == sortOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = MeowType.Meta,
                            color = if (isSelected) MeowColors.Brand else MeowColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = MeowColors.Brand,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSortSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun GlassChip(
    text: String,
    active: Boolean = false,
    trailingArrow: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg = if (active) MeowColors.Brand.copy(alpha = 0.22f) else MeowColors.GlassSurfaceStrong
    val stroke = if (active) MeowColors.Brand.copy(alpha = 0.55f) else MeowColors.GlassBorder
    val textColor = if (active) MeowColors.Brand else MeowColors.TextPrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text = text, style = MeowType.Meta, color = textColor, fontWeight = FontWeight.SemiBold)
        if (trailingArrow) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MeowColors.TextPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * #144 1열 목록의 한 행 (Figma 4867:79342). 아바타 · [레포 칩 + `#번호 · 시각`, 제목] · 상태 칩 · `›`.
 * 클릭(리플 없음)하면 [onClick] 으로 상세 모달을 연다. 키보드 선택 중이면 브랜드색 1.5dp 테두리.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListRow(
    item: DetailItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MeowColors.Surface)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) MeowColors.Brand else MeowColors.GlassBorder, shape)
            .noRippleClickable(onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthorAvatar(initials = item.authorInitials, color = item.repoColor, size = 32.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillChip(item.repo, item.repoColor, small = true)
                item.kindLabel?.let { PillChip(it.text, it.color, small = true) }
                Text(
                    text = listOfNotNull("#${item.number}", relativeTime(item.updatedAtIso), item.detail).joinToString(" · "),
                    style = MeowType.Meta.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MeowColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.title,
                style = MeowType.Title,
                color = MeowColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.widthIn(max = 280.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.chips.forEach { StatusChipView(it) }
            }
        }
        Text(text = "›", color = ChevronColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

private val ChevronColor = Color(0xFFC9CCDA)

/** 리플 없이 눌리고, 마우스를 올리면 손가락 커서를 보여 준다. */
@Composable
internal fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)

/** 상태 칩 하나 (CI 는 앞에 색 점). */
@Composable
internal fun StatusChipView(chip: StatusChip) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(chip.color.copy(alpha = 0.14f))
            .border(1.dp, chip.color.copy(alpha = 0.4f), shape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.dot) Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(chip.color))
        Text(text = chip.text, style = MeowType.Badge, color = chip.color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 작성자 이니셜 원형 아바타. 레포 색으로 칠한다. */
@Composable
internal fun AuthorAvatar(initials: String, color: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = color,
            fontSize = if (size < 40.dp) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun PillChip(text: String, color: Color, small: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 2.dp else 4.dp),
    ) {
        Text(
            text = text,
            style = if (small) MeowType.Badge.copy(fontSize = 11.sp, lineHeight = 14.sp) else MeowType.Badge,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
