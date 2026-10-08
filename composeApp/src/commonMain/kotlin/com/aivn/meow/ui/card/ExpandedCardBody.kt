package com.aivn.meow.ui.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.CardDiscussion
import com.aivn.meow.data.CodeContext
import com.aivn.meow.data.DiscussionComment
import com.aivn.meow.data.DiscussionReview
import com.aivn.meow.data.DiscussionTarget
import com.aivn.meow.data.ReviewVerdict
import com.aivn.meow.data.initialsFrom
import com.aivn.meow.data.colorForRepo
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.loadImageBitmap
import com.aivn.meow.ui.markdown.MarkdownBody
import com.aivn.meow.util.relativeAgo

// #131 Figma 4720:79341 '내 PR 펼침 — 세그먼트 탭 + 하단 머지' 색.
private val TrackGray = Color(0xFFF1F2F6)
private val SegmentSelectedText = Color(0xFF0D0F26)
private val SegmentText = Color(0xFF616A94)
private val TimeText = Color(0xFF9AA0BD)
private val ApprovedText = Color(0xFF14A06B)
private val ApprovedBg = Color(0xFFE6F7EF)
private val ChangesText = Color(0xFFD9534F)
private val ChangesBg = Color(0xFFFDF1EF)

/**
 * #131 펼친 카드의 헤더 아래 내용 — 세그먼트 탭(본문 · 댓글 · 리뷰) + 선택한 탭 내용 + [footer].
 * 카드 접기는 헤더 쪽에서만 처리하므로, 이 영역 안의 클릭(탭 · 링크)은 카드를 접지 않는다.
 *
 * @param footer 탭 내용 아래 카드 맨 밑에 붙는 영역 (#130 머지 푸터 자리). null 이면 그리지 않는다.
 */
@Composable
internal fun ExpandedCardBody(
    target: DiscussionTarget,
    body: String?,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val store = LocalCardDiscussions.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (store == null) {
            BodyTab(body)
        } else {
            LaunchedEffect(target.url, target.updatedAtIso) { store.ensureLoaded(target) }
            val states by store.states.collectAsState()
            val selectedTabs by store.selectedTabs.collectAsState()
            val state = states[target.url]
            val data = state?.data
            val tabs = if (target.isPullRequest) CardTab.entries else listOf(CardTab.Body, CardTab.Comments)
            val selected = selectedTabs[target.url]?.takeIf { it in tabs } ?: CardTab.Body

            SegmentedTabs(
                labels = tabs.map { tab -> tab.label(data) },
                selectedIndex = tabs.indexOf(selected),
                onSelect = { index -> store.selectTab(target.url, tabs[index]) },
            )
            when (selected) {
                CardTab.Body -> BodyTab(body)
                CardTab.Comments -> DiscussionTabContent(state, onRetry = { store.retry(target) }) { loaded ->
                    CommentList(loaded.comments)
                }
                CardTab.Reviews -> DiscussionTabContent(state, onRetry = { store.retry(target) }) { loaded ->
                    ReviewList(loaded.reviews)
                }
            }
        }
        footer?.invoke()
    }
}

/** 세그먼트 이름. 개수는 불러온 뒤에만 붙인다. */
private fun CardTab.label(data: CardDiscussion?): String = when (this) {
    CardTab.Body -> "본문"
    CardTab.Comments -> data?.let { "댓글 ${it.comments.size}" } ?: "댓글"
    CardTab.Reviews -> data?.let { "리뷰 ${it.reviews.size}" } ?: "리뷰"
}

/** 회색 트랙 위 흰 선택 세그먼트. 카드 폭을 채운다. */
@Composable
private fun SegmentedTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val segmentShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TrackGray)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val base = Modifier.weight(1f)
            val surface = if (isSelected) {
                base
                    .shadow(1.5.dp, segmentShape, clip = false, ambientColor = Color(0x1F0D0F26), spotColor = Color(0x1F0D0F26))
                    .background(MeowColors.Surface, segmentShape)
            } else {
                base
            }
            Box(
                modifier = surface
                    .clip(segmentShape)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) SegmentSelectedText else SegmentText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BodyTab(body: String?) {
    if (body.isNullOrBlank()) {
        EmptyTabMessage("본문이 없어요")
    } else {
        MarkdownBody(markdown = body)
    }
}

/** 불러온 데이터가 있으면 [content], 없으면 스피너 또는 오류 + 다시 시도. */
@Composable
private fun DiscussionTabContent(
    state: DiscussionState?,
    onRetry: () -> Unit,
    content: @Composable (CardDiscussion) -> Unit,
) {
    val data = state?.data
    when {
        data != null -> content(data)
        state?.error != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "불러오지 못했어요 · ${state.error}",
                style = MeowType.Meta,
                color = MeowColors.Error,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "다시 시도",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MeowType.Meta,
                color = MeowColors.Brand,
                fontWeight = FontWeight.SemiBold,
            )
        }
        else -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MeowColors.Brand, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CommentList(comments: List<DiscussionComment>) {
    if (comments.isEmpty()) {
        EmptyTabMessage("댓글이 없어요")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        comments.forEach { comment ->
            DiscussionEntry(
                author = comment.author,
                avatarUrl = comment.avatarUrl,
                timeIso = comment.createdAtIso,
                body = comment.body,
                chip = comment.code?.let { code ->
                    { MetaChip(text = codeChipText(code), textColor = SegmentText, background = TrackGray) }
                },
                beforeBody = comment.code?.takeIf { it.snippet.isNotEmpty() }?.let { code -> { CodeSnippet(code.snippet) } },
            )
        }
    }
}

@Composable
private fun ReviewList(reviews: List<DiscussionReview>) {
    if (reviews.isEmpty()) {
        EmptyTabMessage("리뷰가 없어요")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        reviews.forEach { review ->
            val (label, textColor, background) = when (review.verdict) {
                ReviewVerdict.Approved -> Triple("승인", ApprovedText, ApprovedBg)
                ReviewVerdict.ChangesRequested -> Triple("변경 요청", ChangesText, ChangesBg)
                ReviewVerdict.Commented -> Triple("의견", SegmentText, TrackGray)
            }
            DiscussionEntry(
                author = review.author,
                avatarUrl = review.avatarUrl,
                timeIso = review.submittedAtIso,
                body = review.body,
                chip = { MetaChip(text = label, textColor = textColor, background = background) },
            )
        }
    }
}

private fun codeChipText(code: CodeContext): String {
    val location = if (code.line != null) "${code.path}:${code.line}" else code.path
    return if (location.isBlank()) "코드" else "코드 · $location"
}

/** 댓글 · 리뷰 한 건: 아바타 | 작성자 · 시각 · 칩 / (코드) / 본문 마크다운. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscussionEntry(
    author: String,
    avatarUrl: String?,
    timeIso: String,
    body: String,
    chip: (@Composable () -> Unit)? = null,
    beforeBody: (@Composable () -> Unit)? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallAvatar(login = author, avatarUrl = avatarUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = author,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MeowColors.TextPrimary,
                )
                Text(
                    text = relativeAgo(timeIso),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = TimeText,
                )
                if (chip != null) Box(modifier = Modifier.align(Alignment.CenterVertically)) { chip() }
            }
            beforeBody?.invoke()
            if (body.isNotBlank()) MarkdownBody(markdown = body)
        }
    }
}

/** 28dp 원형 아바타. 사진을 받기 전 · 실패 시 이니셜. */
@Composable
private fun SmallAvatar(login: String, avatarUrl: String?) {
    val color = remember(login) { colorForRepo(login) }
    val bitmap by produceState<ImageBitmap?>(AvatarCache[avatarUrl], avatarUrl) {
        if (value == null && !avatarUrl.isNullOrBlank()) {
            value = loadImageBitmap(avatarUrl)?.also { AvatarCache[avatarUrl] = it }
        }
    }
    Box(
        modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = login, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp))
        } else {
            Text(
                text = remember(login) { initialsFrom(name = null, login = login) },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

/** 탭을 오갈 때 아바타를 다시 받지 않도록. 컴포지션(UI) 스레드에서만 접근한다. */
private object AvatarCache {
    private const val MAX_ENTRIES = 64
    private val entries = LinkedHashMap<String, ImageBitmap>()

    operator fun get(url: String?): ImageBitmap? = url?.let { entries[it] }

    operator fun set(url: String, bitmap: ImageBitmap) {
        entries.remove(url)
        entries[url] = bitmap
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }
}

@Composable
private fun MetaChip(text: String, textColor: Color, background: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MeowType.Badge,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 코드 댓글이 달린 줄 주변(diffHunk 마지막 몇 줄). 추가 · 삭제 줄은 색으로 구분한다. */
@Composable
private fun CodeSnippet(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MeowColors.TextPrimary.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        lines.forEach { line ->
            val color = when {
                line.startsWith("+") -> ApprovedText
                line.startsWith("-") -> ChangesText
                else -> MeowColors.TextSecondary
            }
            Text(
                text = line,
                style = MeowType.Code.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        style = MeowType.Meta,
        color = MeowColors.TextTertiary,
        textAlign = TextAlign.Center,
    )
}
