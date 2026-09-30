package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.DashboardSnapshot
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.util.formatSyncLabel
import com.aivn.meow.util.relativeTime

@Composable
fun Dashboard(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MeowColors.Background),
    ) {
        when (state) {
            is DashboardUiState.Loading -> CenteredLoading("PR 목록을 불러오는 중…")
            is DashboardUiState.Error -> CenteredError(state.message, onRefresh)
            is DashboardUiState.Loaded -> DashboardContent(state, onRefresh, onOpenPr)
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Loaded,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
) {
    var includeDraft by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(PrSortOption.OLDEST) }

    val snapshot = state.snapshot
    val prs = snapshot.pullRequests
        .map { it.copy(relativeTime = relativeTime(it.updatedAtIso)) }
        .let { if (includeDraft) it else it.filterNot { pr -> pr.isDraft } }
        .let { list ->
            when (sortOption) {
                PrSortOption.OLDEST -> list.sortedBy { it.updatedAtIso }
                PrSortOption.NEWEST -> list.sortedByDescending { it.updatedAtIso }
                // 레포 이름순으로 묶고, 레포 내에서는 오래된 순.
                PrSortOption.BY_REPO -> list.sortedWith(compareBy({ it.repo }, { it.updatedAtIso }))
            }
        }

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
            PrListCard(
                pullRequests = prs,
                sortOption = sortOption,
                includeDraft = includeDraft,
                onRefresh = onRefresh,
                onSortSelect = { sortOption = it },
                onToggleDraft = { includeDraft = !includeDraft },
                onOpenPr = onOpenPr,
                modifier = Modifier.fillMaxWidth(),
            )
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

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "불러오는 중 문제가 발생했어요",
            color = MeowColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp),
            color = MeowColors.TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = "환경변수 GITHUB_TOKEN 또는 ~/.config/meow/token 을 확인한 뒤 다시 시도해 보세요.",
            modifier = Modifier.padding(top = 8.dp),
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
        )
    }
}
