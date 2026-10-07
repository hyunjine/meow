package com.aivn.meow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import com.aivn.meow.ms.GraphClient
import com.aivn.meow.ms.MsAuth
import com.aivn.meow.realtime.RealtimeService
import com.aivn.meow.schedule.ScheduleRepository
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.MeowTheme
import com.aivn.meow.ui.Dashboard
import com.aivn.meow.ui.DashboardUiState
import com.aivn.meow.ui.DashboardViewModel
import com.aivn.meow.ui.MeowNotice
import com.aivn.meow.ui.nav.AppDrawer
import com.aivn.meow.ui.nav.AppScreen
import com.aivn.meow.ui.schedule.ScheduleScreen
import com.aivn.meow.ui.schedule.ScheduleViewModel
import com.aivn.meow.ui.sections.DashboardSections
import com.aivn.meow.ui.weekly.WeeklyReportScreen
import com.aivn.meow.ui.weekly.WeeklyReportViewModel
import com.aivn.meow.weekly.WeeklyDraftBuilder
import com.aivn.meow.weekly.WeeklyReportRepository
import io.ktor.client.engine.HttpClientEngineFactory

@Composable
fun App(
    config: AppConfig,
    githubClientFactory: (String) -> GithubClient,
    onOpenUrl: (String) -> Unit,
    msEngine: HttpClientEngineFactory<*>,
    onNotices: (List<MeowNotice>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(config.token) {
        val client = githubClientFactory(config.token)
        val repository = PrRepository(client, DashboardSections)
        DashboardViewModel(repository, config.org, scope)
    }
    // 주간 보고 상태는 화면 전환에도 유지되도록 App 수준에 둔다.
    val weeklyViewModel = remember(config.token) {
        val auth = MsAuth(msEngine)
        WeeklyReportViewModel(
            auth = auth,
            repository = WeeklyReportRepository(GraphClient(auth, msEngine)),
            draftBuilder = WeeklyDraftBuilder(githubClientFactory(config.token), config.org),
            scope = scope,
        )
    }
    // 일정 화면도 주간 보고와 같은 Microsoft 계정(MsAuth)을 쓴다.
    val scheduleViewModel = remember(weeklyViewModel) {
        ScheduleViewModel(
            auth = weeklyViewModel.auth,
            repository = ScheduleRepository(GraphClient(weeklyViewModel.auth, msEngine)),
            scope = scope,
        )
    }
    val realtimeService = remember(config.supabase) {
        config.supabase?.let { RealtimeService(it) }
    }

    LaunchedEffect(viewModel) { viewModel.start() }
    LaunchedEffect(weeklyViewModel) { weeklyViewModel.start() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { onNotices(it) }
    }

    val state by viewModel.state.collectAsState()
    val favoriteRepos by viewModel.favorites.collectAsState()
    val orgRepos by viewModel.orgRepos.collectAsState()
    val snapshot = (state as? DashboardUiState.Loaded)?.snapshot
    val viewerLogin = snapshot?.viewerLogin
    // 앱은 항상 GitHub 화면으로 시작한다 (선택은 저장하지 않음)
    var screen by remember { mutableStateOf(AppScreen.GITHUB) }
    // 화면별 rememberSaveable 상태(탭 · 정렬 · 스크롤 등)를 화면 전환 후에도 유지
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(realtimeService, viewerLogin) {
        val service = realtimeService ?: return@LaunchedEffect
        val login = viewerLogin ?: return@LaunchedEffect
        service.subscribeNotices(login).collect { notice ->
            viewModel.onRealtimeNotice(notice)
        }
    }

    MeowTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            AppDrawer(
                selected = screen,
                onSelect = { screen = it },
                viewerLogin = viewerLogin,
                viewerInitials = snapshot?.viewerInitials,
                avatarUrl = snapshot?.avatarUrl,
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MeowColors.Background)) {
                screenStateHolder.SaveableStateProvider(screen.name) {
                    when (screen) {
                        AppScreen.GITHUB -> Dashboard(
                            state = state,
                            onRefresh = { viewModel.refresh() },
                            onOpenPr = { pr: PullRequest -> onOpenUrl(pr.url) },
                            onOpenUrl = onOpenUrl,
                            favoriteRepos = favoriteRepos,
                            orgRepos = orgRepos,
                            onToggleFavorite = viewModel::toggleFavorite,
                        )
                        AppScreen.WEEKLY_REPORT -> WeeklyReportScreen(viewModel = weeklyViewModel, onOpenUrl = onOpenUrl)
                        AppScreen.SCHEDULE -> ScheduleScreen(viewModel = scheduleViewModel)
                    }
                }
            }
        }
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
