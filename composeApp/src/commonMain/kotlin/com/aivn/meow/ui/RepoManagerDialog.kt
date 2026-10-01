package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.SectionResult
import com.aivn.meow.data.colorForRepo
import com.aivn.meow.theme.MeowColors

private enum class RepoFilter(val label: String) { All("전체"), Working("작업 중"), Favorites("즐겨찾기") }

/** 레포 카드 요약에 쓰는 탭별 짧은 이름. 없는 섹션은 탭 이름을 그대로 쓴다. */
private val SummaryLabels = mapOf(
    "my-pr-status" to "내 PR",
    "assigned-issues" to "할당",
    "mentions" to "멘션",
    "my-issue-comments" to "새 댓글",
)

/**
 * '작업 중' 레포별 요약 (예: '리뷰 2 · 멘션 1'). 탭 순서대로 0 인 탭은 생략한다.
 * 키 집합이 곧 '작업 중' 레포 = 어느 탭에든 항목이 있는 레포.
 */
internal fun repoSummaries(reviewRepos: List<String>, sections: List<Pair<SectionResult, List<String>>>): Map<String, String> {
    val tabs = listOf("리뷰" to reviewRepos) +
        sections.map { (result, repos) -> (SummaryLabels[result.section.id] ?: result.section.tabLabel) to repos }
    val repos = tabs.flatMap { it.second }.toSet()
    return repos.associateWith { repo ->
        tabs.mapNotNull { (label, list) -> list.count { it == repo }.takeIf { it > 0 }?.let { "$label $it" } }
            .joinToString(" · ")
    }
}

/**
 * 레포 즐겨찾기 관리 모달 (같은 창 위 오버레이). 별 토글은 즉시 [onToggleFavorite] 로 반영된다.
 * 완료 · ✕ · Esc · 바깥 dim 클릭으로 닫는다.
 */
@Composable
internal fun RepoManagerDialog(
    allRepos: List<String>,
    summaries: Map<String, String>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RepoFilter.Working) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }

    val counts = mapOf(
        RepoFilter.All to allRepos.size,
        RepoFilter.Working to summaries.size,
        RepoFilter.Favorites to favorites.size,
    )
    val visible = when (filter) {
        RepoFilter.All -> allRepos
        RepoFilter.Working -> allRepos.filter { it in summaries }
        RepoFilter.Favorites -> allRepos.filter { it in favorites }
    }.filter { it.contains(query.trim(), ignoreCase = true) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MeowColors.TextPrimary.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val cardShape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.8f)
                .shadow(elevation = 32.dp, shape = cardShape)
                .background(MeowColors.Surface, cardShape)
                // 카드 안 클릭이 dim 으로 전달돼 모달이 닫히지 않도록 소비한다
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("레포 즐겨찾기 관리", color = MeowColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "☆ 를 누르면 사이드바에 고정돼요",
                        color = MeowColors.TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = MeowColors.TextTertiary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(4.dp).size(20.dp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                singleLine = true,
                textStyle = TextStyle(color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(MeowColors.Brand),
                decorationBox = { field ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeowColors.Background)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, tint = MeowColors.TextTertiary, modifier = Modifier.size(16.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text("Team-AIVN 레포 검색", color = MeowColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            field()
                        }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepoFilter.entries.forEach { option ->
                    FilterChip(
                        label = option.label,
                        count = counts.getValue(option),
                        selected = option == filter,
                        onClick = { filter = option },
                    )
                }
            }
            Box(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (visible.isEmpty()) {
                    Text(
                        text = "조건에 맞는 레포가 없어요",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        color = MeowColors.TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    TwoColumnGrid(visible, Modifier.fillMaxWidth(), spacing = 12.dp) { _, repo, cellModifier ->
                        RepoFavoriteCard(
                            name = repo,
                            summary = summaries[repo] ?: "항목 없음",
                            favorite = repo in favorites,
                            onToggle = { onToggleFavorite(repo) },
                            modifier = cellModifier,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "즐겨찾기 ${favorites.size}개",
                    modifier = Modifier.weight(1f),
                    color = MeowColors.TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "완료",
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeowColors.Brand)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    color = MeowColors.Surface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MeowColors.Brand else MeowColors.Surface)
            .border(1.dp, if (selected) MeowColors.Brand else MeowColors.GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) MeowColors.Surface else MeowColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            color = if (selected) MeowColors.Surface else MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RepoFavoriteCard(
    name: String,
    summary: String,
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (favorite) MeowColors.Warning.copy(alpha = 0.08f) else MeowColors.Surface)
            .border(1.dp, if (favorite) MeowColors.Warning.copy(alpha = 0.45f) else MeowColors.GlassBorder, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colorForRepo(name)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                color = MeowColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (favorite) "즐겨찾기 해제" else "즐겨찾기",
            tint = if (favorite) MeowColors.Warning else MeowColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}
