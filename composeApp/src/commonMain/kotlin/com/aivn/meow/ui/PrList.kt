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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface

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
                            color = if (isSelected) MeowColors.Brand else MeowColors.TextPrimary,
                            fontSize = 12.sp,
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
 * 카드 클릭은 본문 펼치기/접기, GitHub 열기는 오른쪽 위 버튼.
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
) {
    Row(
        modifier = modifier
            .glassSurface(
                corner = 20.dp,
                fill = MeowColors.GlassSurface,
                borderColor = cardBorderColor(isSelected, isExpanded),
                borderWidth = if (isSelected) 2.dp else 1.dp,
            )
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuthorAvatar(initials = pr.authorInitials, color = pr.repoColor)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    color = MeowColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pr.relativeTime,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MeowColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(text = titleWithNumber(pr.title, pr.number))

            CardBody(body = pr.body, isExpanded = isExpanded)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (pr.isDraft) PillChip("Draft", MeowColors.Grey)
                CiChip(pr.ci)
                pr.labels.forEach { label -> PillChip(label.text, label.color) }
            }
        }

        OpenInGithubButton(onClick = onOpen)
    }
}

/** 키보드 선택 > 펼침 > 기본 순으로 카드 테두리 색을 고른다. */
internal fun cardBorderColor(isSelected: Boolean, isExpanded: Boolean): Color = when {
    isSelected -> MeowColors.Brand
    isExpanded -> MeowColors.Brand.copy(alpha = 0.35f)
    else -> MeowColors.GlassBorder
}

/** 카드 본문. 접힌 상태는 3줄 미리보기, 펼치면 전체. 비었거나 공백뿐이면 영역을 그리지 않는다. */
@Composable
internal fun CardBody(body: String?, isExpanded: Boolean) {
    val text = remember(body) { body?.let(::tidyBody).orEmpty() }
    if (text.isEmpty()) return
    Text(
        text = text,
        color = MeowColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 줄 끝 공백을 지우고 연속된 빈 줄을 하나로 줄인다. */
private fun tidyBody(raw: String): String =
    raw.lines()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

/** 카드 오른쪽 위의 'GitHub에서 열기' 원형 아이콘 버튼. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenInGithubButton(onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("GitHub에서 열기") } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MeowColors.Surface)
                .border(1.dp, MeowColors.GlassBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "GitHub에서 열기",
                tint = MeowColors.TextSecondary,
                modifier = Modifier.size(16.dp),
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

/** 제목 뒤에 두 칸 띄우고 `#번호` 를 붙인 한 덩어리 텍스트. 좁은 카드에서 함께 줄바꿈된다. */
internal fun titleWithNumber(title: String, number: Int): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = MeowColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)) {
        append(title)
    }
    append("  ")
    withStyle(SpanStyle(color = MeowColors.TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) {
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
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DotSeparator(modifier: Modifier = Modifier) {
    Text(text = "·", modifier = modifier, color = MeowColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
