package com.aivn.meow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.DiscussionTarget
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.card.ExpandedCardBody
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.common.OpenOriginalButton
import com.aivn.meow.ui.markdown.MarkdownBody

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
 * 2열 그리드용 리뷰 대기 PR 카드. 좁은 폭에서도 제목 · 메타 · 칩이 줄바꿈된다.
 * 헤더(카드 윗부분) 클릭은 펼치기/접기, GitHub 열기는 오른쪽 위 버튼.
 * #131 펼치면 헤더 아래에 [ExpandedCardBody] (본문 · 댓글 · 리뷰 탭 + [footer]) 가 카드 폭으로 붙는다.
 *
 * @param footer 펼친 카드 맨 아래 영역 (#130 머지 푸터 자리). 접혀 있으면 그리지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrRow(
    pr: PullRequest,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    ExpandableCard(
        isSelected = isSelected,
        isExpanded = isExpanded,
        onToggleExpand = onToggleExpand,
        modifier = modifier,
        header = {
            AuthorAvatar(initials = pr.authorInitials, color = pr.repoColor)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PillChip(pr.repo, pr.repoColor)
                    DotSeparator(Modifier.align(Alignment.CenterVertically))
                    Text(
                        text = "@${pr.author}",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MeowType.Meta,
                        color = MeowColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = pr.relativeTime,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MeowType.Meta,
                        color = MeowColors.TextTertiary,
                    )
                }

                Text(text = titleWithNumber(pr.title, pr.number), style = MeowType.Title)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (pr.isDraft) PillChip("Draft", MeowColors.Grey)
                    CiChip(pr.ci)
                    pr.labels.forEach { label -> PillChip(label.text, label.color) }
                }
            }

            CardActions(onOpen = onOpen, hasBody = true, isExpanded = isExpanded)
        },
        expanded = {
            ExpandedCardBody(
                target = DiscussionTarget(
                    url = pr.url,
                    repoFullName = pr.repoFullName,
                    number = pr.number,
                    isPullRequest = true,
                    updatedAtIso = pr.updatedAtIso,
                ),
                body = pr.body,
                footer = footer,
            )
        },
    )
}

/**
 * #131 카드 틀: [header] 행만 클릭으로 펼치기/접기하고, 펼치면 그 아래 [expanded] 를 카드 폭으로 그린다.
 * [expanded] 가 null 이면 펼친 영역 없이 헤더만 그린다. 펼친 영역은 클릭을 받지 않으므로 그 안의 탭 · 링크를 눌러도 카드가 접히지 않는다.
 */
@Composable
internal fun ExpandableCard(
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable RowScope.() -> Unit,
    expanded: (@Composable () -> Unit)?,
) {
    val showExpanded = isExpanded && expanded != null
    // 카드에는 리플을 그리지 않는다. 접혀 있을 땐 카드 전체, 펼쳐 있을 땐 헤더만 눌러 펼치고 접는다.
    Column(
        modifier = modifier
            .glassSurface(
                corner = 20.dp,
                fill = MeowColors.GlassSurface,
                borderColor = cardBorderColor(isSelected, isExpanded),
                borderWidth = if (isSelected) 2.dp else 1.dp,
            )
            .clip(RoundedCornerShape(20.dp))
            .then(if (showExpanded) Modifier else Modifier.noRippleClickable(onToggleExpand)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showExpanded) {
                        Modifier.noRippleClickable(onToggleExpand)
                    } else {
                        Modifier
                    },
                )
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (showExpanded) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = header,
        )
        if (showExpanded) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                expanded?.invoke()
            }
        }
    }
}

/** 리플 없이 눌리고, 마우스를 올리면 손가락 커서를 보여 준다. */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)

/** 키보드 선택 > 펼침 > 기본 순으로 카드 테두리 색을 고른다. */
internal fun cardBorderColor(isSelected: Boolean, isExpanded: Boolean): Color = when {
    isSelected -> MeowColors.Brand
    isExpanded -> MeowColors.Brand.copy(alpha = 0.35f)
    else -> MeowColors.GlassBorder
}

/** 카드 본문. 펼쳤을 때만 GitHub 마크다운으로 전체를 보여주고(#119), 접혀 있거나 비었으면 영역을 그리지 않는다. */
@Composable
internal fun CardBody(body: String?, isExpanded: Boolean) {
    if (!isExpanded || body.isNullOrBlank()) return
    MarkdownBody(markdown = body)
}

/** 카드 오른쪽 위 열 — '원본 보기' 버튼, 본문이 있으면 그 아래 펼치기 표시(⌄ / ⌃). */
@Composable
internal fun CardActions(onOpen: () -> Unit, hasBody: Boolean, isExpanded: Boolean) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OpenOriginalButton(onClick = onOpen)
        if (hasBody) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "본문 접기" else "본문 펼치기",
                tint = MeowColors.TextTertiary,
                modifier = Modifier.padding(end = 6.dp).size(20.dp).rotate(if (isExpanded) 180f else 0f),
            )
        }
    }
}

/** 작성자 이니셜 원형 아바타. 레포 색으로 칠한다. */
@Composable
internal fun AuthorAvatar(initials: String, color: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 제목 뒤에 두 칸 띄우고 `#번호` 를 붙인 한 덩어리 텍스트. 좁은 카드에서 함께 줄바꿈된다.
 * 크기 · 줄 간격은 `style = MeowType.Title` 로 그리는 쪽에서 준다.
 */
internal fun titleWithNumber(title: String, number: Int): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = MeowColors.TextPrimary, fontWeight = FontWeight.SemiBold)) {
        append(title)
    }
    append("  ")
    withStyle(SpanStyle(color = MeowColors.TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Medium)) {
        append("#$number")
    }
}

@Composable
internal fun PillChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MeowType.Badge, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CiChip(status: CiStatus) {
    val (label, color) = when (status) {
        CiStatus.Pass -> "CI · 통과" to MeowColors.Success
        CiStatus.Fail -> "CI · 실패" to MeowColors.Error
        CiStatus.Pending -> "CI · 진행중" to MeowColors.Warning
        // CI 체크가 아예 없는 PR: MyPrStatusSection 과 동일하게 칩을 숨긴다.
        CiStatus.None -> return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text = label, style = MeowType.Badge, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DotSeparator(modifier: Modifier = Modifier) {
    Text(text = "·", modifier = modifier, style = MeowType.Meta, color = MeowColors.TextTertiary)
}
