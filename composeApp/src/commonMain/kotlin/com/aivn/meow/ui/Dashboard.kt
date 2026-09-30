package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.DashboardSnapshot
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.sections.SectionCard
import com.aivn.meow.util.formatKst
import com.aivn.meow.util.formatSyncLabel
import com.aivn.meow.util.relativeTime

@Composable
fun Dashboard(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MeowColors.Background),
    ) {
        when (state) {
            is DashboardUiState.Loading -> CenteredLoading("PR 목록을 불러오는 중…")
            is DashboardUiState.Error -> CenteredError(state.failure, onRefresh)
            is DashboardUiState.Loaded -> DashboardContent(state, onRefresh, onOpenPr, onOpenUrl)
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Loaded,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var sortOption by remember { mutableStateOf(PrSortOption.OLDEST) }
    // ↑/↓ · j/k 로 이동하는 키보드 포커스 인덱스
    var selectedIndex by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val snapshot = state.snapshot
    val prs = snapshot.pullRequests
        .map { it.copy(relativeTime = relativeTime(it.updatedAtIso)) }
        .let { list ->
            when (sortOption) {
                PrSortOption.OLDEST -> list.sortedBy { it.updatedAtIso }
                PrSortOption.NEWEST -> list.sortedByDescending { it.updatedAtIso }
                // 레포 이름순으로 묶고, 레포 내에서는 오래된 순.
                PrSortOption.BY_REPO -> list.sortedWith(compareBy({ it.repo }, { it.updatedAtIso }))
            }
        }

    // 정렬 · 새로고침으로 목록이 줄어들면 선택 인덱스를 유효 범위로 맞춘다
    LaunchedEffect(prs.size) {
        selectedIndex = selectedIndex.coerceIn(0, (prs.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val stats = listOf(
        StatItem(
            label = "리뷰 대기 PR",
            value = snapshot.totalOpen.toString(),
            suffix = "건",
            hint = prs.firstOrNull()?.relativeTime?.let { "가장 오래된 건 · $it" } ?: "대기 중인 PR 없음",
            icon = "📥",
            accent = MeowColors.Brand,
        ),
        StatItem(
            label = "48시간 초과",
            value = snapshot.overdue48h.toString(),
            suffix = "건",
            hint = if (snapshot.overdue48h > 0) "우선 처리 권장" else "쾌적한 상태",
            icon = "⏱️",
            accent = MeowColors.Warning,
        ),
        StatItem(
            label = "리뷰 요청 총계",
            value = snapshot.pullRequests.size.toString(),
            suffix = "건",
            hint = "org: Team-AIVN",
            icon = "📊",
            accent = MeowColors.Success,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionDown, Key.J -> {
                        if (prs.isNotEmpty()) selectedIndex = (selectedIndex + 1).coerceAtMost(prs.lastIndex)
                        true
                    }
                    Key.DirectionUp, Key.K -> {
                        if (prs.isNotEmpty()) selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        prs.getOrNull(selectedIndex)?.let(onOpenPr)
                        true
                    }
                    Key.R -> {
                        if (keyEvent.isMetaPressed) {
                            onRefresh()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1440.dp)
                .padding(horizontal = 40.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DashboardHeader(
                lastSyncLabel = formatSyncLabel(snapshot.fetchedAtIso),
                userInitials = snapshot.viewerInitials,
                avatarUrl = snapshot.avatarUrl,
                modifier = Modifier.fillMaxWidth(),
            )
            StatCards(stats = stats, modifier = Modifier.fillMaxWidth())
            state.refreshError?.let { failure ->
                RefreshErrorBanner(failure = failure, onRetry = onRefresh, modifier = Modifier.fillMaxWidth())
            }
            PrListCard(
                pullRequests = prs,
                sortOption = sortOption,
                onRefresh = onRefresh,
                onSortSelect = { sortOption = it },
                onOpenPr = onOpenPr,
                modifier = Modifier.fillMaxWidth(),
                selectedIndex = selectedIndex,
            )
            // 보조 섹션은 ui/sections/DashboardSections.kt 에 등록된 순서대로 렌더링된다.
            snapshot.sections.forEach { result ->
                SectionCard(result = result, onOpenUrl = onOpenUrl, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CenteredLoading(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MeowColors.Brand)
        Text(
            text = message,
            modifier = Modifier.padding(top = 16.dp),
            color = MeowColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 실패 원인별 제목 · 해결 안내 문구. */
private data class FailureGuide(val title: String, val hint: String)

private fun LoadFailure.guide(): FailureGuide = when (this) {
    is LoadFailure.Auth -> FailureGuide(
        title = "GitHub 토큰이 만료됐거나 권한이 부족해요",
        hint = "repo:read + read:org 권한 토큰을 새로 발급해 환경변수 GITHUB_TOKEN 또는 " +
            "~/.config/meow/token 에 교체한 뒤 다시 시도를 눌러 주세요.",
    )
    is LoadFailure.RateLimited -> {
        val quota = if (remaining != null && limit != null) "남은 쿼터 $remaining/$limit · " else ""
        val reset = resetAt?.let { "${formatKst(it)} (KST) 이후 다시 시도해 주세요." }
            ?: "잠시 후 다시 시도해 주세요."
        FailureGuide(title = "GitHub API 사용 한도를 초과했어요", hint = quota + reset)
    }
    is LoadFailure.Other -> FailureGuide(
        title = "불러오는 중 문제가 발생했어요",
        hint = "환경변수 GITHUB_TOKEN 또는 ~/.config/meow/token 을 확인한 뒤 다시 시도해 보세요.",
    )
}

@Composable
private fun CenteredError(failure: LoadFailure, onRetry: () -> Unit) {
    val guide = failure.guide()
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = guide.title,
            color = MeowColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = failure.message,
            modifier = Modifier.padding(top = 12.dp),
            color = MeowColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = guide.hint,
            modifier = Modifier.padding(top = 8.dp),
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
        )
        RetryButton(onClick = onRetry, modifier = Modifier.padding(top = 20.dp))
    }
}

/** 목록은 유지한 채 새로고침 실패 사실과 재시도를 안내하는 배너. */
@Composable
private fun RefreshErrorBanner(failure: LoadFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    // 일반 오류는 원문 메시지를, 토큰 · rate limit 은 해결 안내를 보여준다.
    val (title, detail) = when (failure) {
        is LoadFailure.Other -> "새로고침에 실패했어요" to failure.message
        else -> failure.guide().let { it.title to it.hint }
    }
    Row(
        modifier = modifier
            .glassSurface(corner = 20.dp, borderColor = MeowColors.Error.copy(alpha = 0.45f), elevation = 4.dp)
            .background(MeowColors.Error.copy(alpha = 0.06f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "$title · 마지막으로 불러온 목록을 표시 중이에요",
                color = MeowColors.Error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                color = MeowColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RetryButton(onClick = onRetry)
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MeowColors.Brand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Refresh, null, tint = MeowColors.Surface, modifier = Modifier.size(14.dp))
        Text(text = "다시 시도", color = MeowColors.Surface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
