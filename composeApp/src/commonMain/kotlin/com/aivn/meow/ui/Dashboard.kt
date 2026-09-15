package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aivn.meow.data.samplePullRequests
import com.aivn.meow.theme.MeowColors

@Composable
fun Dashboard() {
    var includeDraft by remember { mutableStateOf(true) }
    var sortLabel by remember { mutableStateOf("오래된 순") }

    val stats = listOf(
        StatItem(
            label = "리뷰 대기 PR",
            value = samplePullRequests.size.toString(),
            suffix = "건",
            hint = "가장 오래된 건 · 3일 전",
            icon = "📥",
            accent = MeowColors.Brand,
        ),
        StatItem(
            label = "48시간 초과",
            value = "2",
            suffix = "건",
            hint = "우선 처리 권장",
            icon = "⏱️",
            accent = MeowColors.Warning,
        ),
        StatItem(
            label = "이번 주 리뷰 완료",
            value = "12",
            suffix = "건",
            hint = "지난주 대비 +3",
            icon = "✅",
            accent = MeowColors.Success,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeowColors.Background)
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
                lastSyncLabel = "2026-09-15 · 방금 전",
                userInitials = "HJ",
                modifier = Modifier.fillMaxWidth(),
            )
            StatCards(stats = stats, modifier = Modifier.fillMaxWidth())
            PrListCard(
                pullRequests = samplePullRequests,
                sortLabel = sortLabel,
                includeDraft = includeDraft,
                onRefresh = { /* TODO */ },
                onSortClick = {
                    sortLabel = if (sortLabel == "오래된 순") "최신 순" else "오래된 순"
                },
                onToggleDraft = { includeDraft = !includeDraft },
                onOpenPr = { /* TODO */ },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
