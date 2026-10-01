package com.aivn.meow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.config.AppConfig
import com.aivn.meow.data.PrRepository
import com.aivn.meow.github.GithubClient
import com.aivn.meow.model.PullRequest
import com.aivn.meow.realtime.RealtimeService
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.MeowTheme
import com.aivn.meow.ui.Dashboard
import com.aivn.meow.ui.DashboardUiState
import com.aivn.meow.ui.DashboardViewModel
import com.aivn.meow.ui.MeowNotice
import com.aivn.meow.ui.sections.DashboardSections

@Composable
fun App(
    config: AppConfig,
    githubClientFactory: (String) -> GithubClient,
    onOpenUrl: (String) -> Unit,
    onNotices: (List<MeowNotice>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(config.token) {
        val client = githubClientFactory(config.token)
        val repository = PrRepository(client, DashboardSections)
        DashboardViewModel(repository, config.org, scope)
    }
    val realtimeService = remember(config.supabase) {
        config.supabase?.let { RealtimeService(it) }
    }

    LaunchedEffect(viewModel) { viewModel.start() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { onNotices(it) }
    }

    val state by viewModel.state.collectAsState()
    val favoriteRepos by viewModel.favorites.collectAsState()
    val orgRepos by viewModel.orgRepos.collectAsState()
    val viewerLogin = (state as? DashboardUiState.Loaded)?.snapshot?.viewerLogin

    LaunchedEffect(realtimeService, viewerLogin) {
        val service = realtimeService ?: return@LaunchedEffect
        val login = viewerLogin ?: return@LaunchedEffect
        service.subscribeNotices(login).collect { notice ->
            viewModel.onRealtimeNotice(notice)
        }
    }

    MeowTheme {
        Dashboard(
            state = state,
            onRefresh = { viewModel.refresh() },
            onOpenPr = { pr: PullRequest -> onOpenUrl(pr.url) },
            onOpenUrl = onOpenUrl,
            favoriteRepos = favoriteRepos,
            orgRepos = orgRepos,
            onToggleFavorite = viewModel::toggleFavorite,
        )
    }
}

@Composable
fun MissingTokenApp() {
    MeowTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(MeowColors.Background).padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "GitHub PAT 이 필요합니다",
                    color = MeowColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "환경변수 GITHUB_TOKEN 에 repo:read + read:org 권한 토큰을 넣거나\n~/.config/meow/token 파일에 저장한 뒤 다시 실행해 주세요.",
                    color = MeowColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
