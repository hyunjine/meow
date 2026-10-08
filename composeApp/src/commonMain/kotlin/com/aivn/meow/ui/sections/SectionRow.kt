package com.aivn.meow.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivn.meow.data.DiscussionTarget
import com.aivn.meow.model.ItemKind
import com.aivn.meow.model.SectionItem
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.AuthorAvatar
import com.aivn.meow.ui.CardBody
import com.aivn.meow.ui.DotSeparator
import com.aivn.meow.ui.CardActions
import com.aivn.meow.ui.PillChip
import com.aivn.meow.ui.ExpandableCard
import com.aivn.meow.ui.card.ExpandedCardBody
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.titleWithNumber
import com.aivn.meow.util.relativeTime

/**
 * 보조 섹션 탭의 2열 그리드 카드. [PrRow][com.aivn.meow.ui.PrRow] 와 같은 스타일 · 동작(헤더 클릭 = 펼치기).
 * #131 [showDiscussion] 이면 펼쳤을 때 헤더 아래에 본문 · 댓글 · 리뷰 탭 + [footer] 를, 아니면 예전처럼 본문만 보여준다.
 *
 * @param footer 펼친 카드 맨 아래 영역 (#130 머지 푸터 자리). [showDiscussion] 일 때만 그린다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionRow(
    item: SectionItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showDiscussion: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    ExpandableCard(
        isSelected = isSelected,
        isExpanded = isExpanded,
        onToggleExpand = onToggleExpand,
        modifier = modifier,
        header = {
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

                if (!showDiscussion) CardBody(body = item.body, isExpanded = isExpanded)

                if (item.badges.isNotEmpty() || item.labels.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.badges.forEach { badge -> PillChip(badge.text, badge.color) }
                        item.labels.forEach { label -> PillChip(label.text, label.color) }
                    }
                }
            }

            CardActions(
                onOpen = onOpen,
                hasBody = showDiscussion || footer != null || !item.body.isNullOrBlank(),
                isExpanded = isExpanded,
            )
        },
        expanded = if (showDiscussion) {
            {
                ExpandedCardBody(
                    target = DiscussionTarget(
                        url = item.url,
                        repoFullName = item.repoFullName,
                        number = item.number,
                        isPullRequest = item.kind == ItemKind.PullRequest,
                        updatedAtIso = item.updatedAtIso,
                    ),
                    body = item.body,
                    footer = footer,
                )
            }
        } else {
            null
        },
    )
}
