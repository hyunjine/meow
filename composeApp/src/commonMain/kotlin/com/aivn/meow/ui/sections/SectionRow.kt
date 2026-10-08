package com.aivn.meow.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivn.meow.model.ItemKind
import com.aivn.meow.model.SectionItem
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.AuthorAvatar
import com.aivn.meow.ui.CardBody
import com.aivn.meow.ui.DotSeparator
import com.aivn.meow.ui.CardActions
import com.aivn.meow.ui.PillChip
import com.aivn.meow.ui.cardBorderColor
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.titleWithNumber
import com.aivn.meow.util.relativeTime

/** 보조 섹션 탭의 2열 그리드 카드. [PrRow][com.aivn.meow.ui.PrRow] 와 같은 스타일 · 동작(클릭 = 펼치기). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionRow(
    item: SectionItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
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
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuthorAvatar(initials = item.authorInitials, color = item.repoColor)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillChip(item.repo, item.repoColor)
                PillChip(
                    text = if (item.kind == ItemKind.PullRequest) "PR" else "Issue",
                    color = if (item.kind == ItemKind.PullRequest) MeowColors.Violet else MeowColors.Teal,
                )
                DotSeparator(Modifier.align(Alignment.CenterVertically))
                Text(
                    text = "@${item.author}",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MeowType.Meta,
                    color = MeowColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = relativeTime(item.updatedAtIso),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MeowType.Meta,
                    color = MeowColors.TextTertiary,
                )
            }

            Text(text = titleWithNumber(item.title, item.number), style = MeowType.Title)

            item.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MeowType.Meta,
                    color = MeowColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            CardBody(body = item.body, isExpanded = isExpanded)

            if (item.badges.isNotEmpty() || item.labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.badges.forEach { badge -> PillChip(badge.text, badge.color) }
                    item.labels.forEach { label -> PillChip(label.text, label.color) }
                }
            }

            // #130 펼친 카드 맨 아래 슬롯 (내 PR 현황의 머지 바 등)
            if (isExpanded) footer?.invoke()
        }

        CardActions(onOpen = onOpen, hasBody = !item.body.isNullOrBlank() || footer != null, isExpanded = isExpanded)
    }
}
