package com.aivn.meow.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aivn.meow.ui.EmptyStateCard
import com.aivn.meow.ui.common.PageHeader
import com.aivn.meow.ui.common.PageHorizontalPadding
import com.aivn.meow.ui.common.PageMaxWidth
import com.aivn.meow.ui.common.PageVerticalPadding

/** 주간 업무 보고 화면. 지금은 자리만 — 동기화 · 보고 목록은 이후 이슈에서 붙인다. */
@Composable
fun WeeklyReportScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = PageHorizontalPadding, vertical = PageVerticalPadding)
                .fillMaxWidth()
                .widthIn(max = PageMaxWidth),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                title = "주간 업무 보고",
                subtitle = "보고 메일이 오면 동기화를 눌러 주세요",
                syncLabel = "마지막 동기화",
                syncValue = "아직 동기화 안 함",
                syncOk = false,
                syncEnabled = false,
                onSync = {},
                modifier = Modifier.fillMaxWidth(),
            )
            EmptyStateCard(
                title = "준비 중",
                hint = "주간 보고 기능을 곧 만나볼 수 있어요",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
