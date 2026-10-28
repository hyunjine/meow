package com.aivn.meow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.aivn.meow.cafeteria.CafeteriaRepository
import com.aivn.meow.cafeteria.KakaoChannelClient
import com.aivn.meow.config.AccountSettings
import com.aivn.meow.config.AppConfig
import com.aivn.meow.data.PrRepository
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.fetchViewerLogin
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
import com.aivn.meow.ui.cafeteria.CafeteriaScreen
import com.aivn.meow.ui.cafeteria.CafeteriaViewModel
import com.aivn.meow.ui.common.ConnectLaterCard
import com.aivn.meow.ui.common.LocalCompactLayout
import com.aivn.meow.ui.common.LocalOpenSettings
import com.aivn.meow.ui.common.SettingsButton
import com.aivn.meow.ui.common.pageContent
import com.aivn.meow.ui.nav.AppDrawer
import com.aivn.meow.ui.nav.BottomTabBar
import com.aivn.meow.ui.pr.LocalMergeController
import com.aivn.meow.ui.pr.MergeController
import com.aivn.meow.ui.nav.AppScreen
import com.aivn.meow.ui.schedule.OUTLOOK_WEB_CALENDAR_URL
import com.aivn.meow.ui.schedule.OutlookOpenResult
import com.aivn.meow.ui.schedule.ScheduleScreen
import com.aivn.meow.ui.schedule.ScheduleViewModel
import com.aivn.meow.ui.sections.DashboardSections
import com.aivn.meow.ui.settings.SettingsScreen
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
    // 일정 화면의 "Outlook 일정 ↗". 플랫폼 구현이 없으면 웹 캘린더를 연다.
    onOpenOutlookCalendar: suspend () -> OutlookOpenResult = {
        onOpenUrl(OUTLOOK_WEB_CALENDAR_URL)
        OutlookOpenResult.Browser
    },
    // #165 아이폰: 드로워 대신 하단 탭 바 · 좁은 폭 레이아웃.
    compact: Boolean = false,
    // #165 GitHub 토큰이 없으면(아이폰 1단계) GitHub 화면에 연결 안내만 보여 주고 조회하지 않는다.
    githubConnected: Boolean = true,
    initialScreen: AppScreen = AppScreen.GITHUB,
    // #165 아이폰 설정 화면(GitHub 토큰 · Supabase 저장). 데스크톱은 null — 설정 화면 · ⚙︎ 가 없다.
    accountSettings: AccountSettings? = null,
    // #165 개발용: 설정 화면을 연 채로 시작한다 (MEOW_START_SCREEN=SETTINGS).
    initialShowSettings: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(config.token) {
        val client = githubClientFactory(config.token)
        val repository = PrRepository(client, DashboardSections)
        DashboardViewModel(repository, config.org, scope)
    }
    // #130 내 PR 카드의 머지 바. 머지에 성공하면 대시보드를 새로고침해 목록에서 뺀다.
    val mergeController = remember(viewModel) {
        MergeController(githubClientFactory(config.token), scope, onMerged = { viewModel.refresh() })
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
    // 구내 식당은 인증 없는 카카오 채널 공개 API 를 쓴다.
    val cafeteriaViewModel = remember {
        CafeteriaViewModel(CafeteriaRepository(KakaoChannelClient(msEngine)), scope)
    }
    val realtimeService = remember(config.supabase) {
        config.supabase?.let { RealtimeService(it) }
    }

    LaunchedEffect(viewModel) { if (githubConnected) viewModel.start() }
    LaunchedEffect(weeklyViewModel) { weeklyViewModel.start() }
    LaunchedEffect(cafeteriaViewModel) { cafeteriaViewModel.start() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { onNotices(it) }
    }

    val state by viewModel.state.collectAsState()
    val repoPrefs by viewModel.repoPrefs.collectAsState()
    val repoUniverse by viewModel.repoUniverse.collectAsState()
    val snapshot = (state as? DashboardUiState.Loaded)?.snapshot
    val viewerLogin = snapshot?.viewerLogin
    // 앱은 항상 GitHub 화면(아이폰은 [initialScreen])으로 시작한다 (선택은 저장하지 않음)
    var screen by remember { mutableStateOf(initialScreen) }
    // #165 아이폰 설정 화면(전체 화면 위에 덮는다).
    var showSettings by remember { mutableStateOf(initialShowSettings) }
    val openSettings: (() -> Unit)? = accountSettings?.let { { showSettings = true } }
    // 화면별 rememberSaveable 상태(탭 · 정렬 · 스크롤 등)를 화면 전환 후에도 유지
    val screenStateHolder = rememberSaveableStateHolder()

    // 대시보드를 새로 불러올 때마다(자동 새로고침 포함) 펼친 카드의 머지 상태도 다시 조회한다.
    LaunchedEffect(snapshot?.fetchedAtIso) { mergeController.onDashboardRefreshed() }

    LaunchedEffect(realtimeService, viewerLogin) {
        if (!githubConnected) return@LaunchedEffect
        val service = realtimeService ?: return@LaunchedEffect
        val login = viewerLogin ?: return@LaunchedEffect
        service.subscribeNotices(login).collect { notice ->
            viewModel.onRealtimeNotice(notice)
        }
    }

    val onSelectScreen = { selected: AppScreen ->
        screen = selected
        // 드로워 · 탭 바로 들어올 때마다(같은 화면 다시 눌러도) 그 화면의 동기화 버튼과 같이 새로 불러온다.
        when (selected) {
            AppScreen.GITHUB -> if (githubConnected) viewModel.syncIfIdle()
            AppScreen.WEEKLY_REPORT -> weeklyViewModel.syncIfIdle()
            AppScreen.SCHEDULE -> scheduleViewModel.syncIfIdle()
            AppScreen.CAFETERIA -> cafeteriaViewModel.syncIfIdle()
        }
    }
    val screenContent = @Composable {
        screenStateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                AppScreen.GITHUB -> if (!githubConnected) {
                    GithubConnectLater()
                } else {
                    CompositionLocalProvider(LocalMergeController provides mergeController) {
                        Dashboard(
                            state = state,
                            onRefresh = { viewModel.refresh() },
                            onOpenPr = { pr: PullRequest -> onOpenUrl(pr.url) },
                            onOpenUrl = onOpenUrl,
                            repoPrefs = repoPrefs,
                            repoUniverse = repoUniverse,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleSidebarRepo = viewModel::toggleSidebarRepo,
                            discussions = viewModel.discussions,
                        )
                    }
                }
                AppScreen.WEEKLY_REPORT -> WeeklyReportScreen(viewModel = weeklyViewModel, onOpenUrl = onOpenUrl)
                AppScreen.SCHEDULE -> ScheduleScreen(viewModel = scheduleViewModel, onOpenOutlookCalendar = onOpenOutlookCalendar)
                AppScreen.CAFETERIA -> CafeteriaScreen(viewModel = cafeteriaViewModel, onOpenUrl = onOpenUrl)
            }
        }
    }

    MeowTheme {
        CompositionLocalProvider(LocalCompactLayout provides compact, LocalOpenSettings provides openSettings) {
            if (compact) {
                // #165 아이폰: 위 · 좌우 safe area 안에 화면, 아래에 하단 탭 바(홈 인디케이터 여백 포함).
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().background(MeowColors.Background)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                        ) {
                            screenContent()
                        }
                        BottomTabBar(selected = screen, onSelect = onSelectScreen)
                    }
                    if (showSettings && accountSettings != null) {
                        val msAuth by weeklyViewModel.auth.state.collectAsState()
                        SettingsScreen(
                            settings = accountSettings,
                            githubLogin = viewerLogin,
                            validateGithubToken = { token ->
                                val client = githubClientFactory(token)
                                try {
                                    client.fetchViewerLogin()
                                } finally {
                                    client.close()
                                }
                            },
                            msAuth = msAuth,
                            onMsConnect = weeklyViewModel::signIn,
                            onMsSignOut = weeklyViewModel::signOut,
                            onOpenUrl = onOpenUrl,
                            onClose = { showSettings = false },
                            modifier = Modifier
                                .background(MeowColors.Background)
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    AppDrawer(
                        selected = screen,
                        onSelect = onSelectScreen,
                        viewerLogin = viewerLogin,
                        viewerInitials = snapshot?.viewerInitials,
                        avatarUrl = snapshot?.avatarUrl,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MeowColors.Background)) {
                        screenContent()
                    }
                }
            }
        }
    }
}

/** #165 GitHub 토큰 없이 연 아이폰 앱의 GitHub 화면. 설정 화면에서 토큰을 넣으면 대시보드로 바뀐다. */
@Composable
private fun GithubConnectLater() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.pageContent(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "GitHub",
                    modifier = Modifier.weight(1f),
                    color = MeowColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                LocalOpenSettings.current?.let { SettingsButton(onClick = it) }
            }
            ConnectLaterCard(
                emoji = "🐙",
                title = "GitHub 계정이 연결되지 않았어요",
                hint = "Personal Access Token 을 넣으면 리뷰 요청 · 내 PR 을 볼 수 있어요",
                actionLabel = "GitHub 연결하기",
            )
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
