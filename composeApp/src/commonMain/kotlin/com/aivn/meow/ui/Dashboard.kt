package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.aivn.meow.data.SectionResult
import com.aivn.meow.data.colorForRepo
import com.aivn.meow.model.PullRequest
import com.aivn.meow.model.SectionItem
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.common.PageHeader
import com.aivn.meow.ui.common.PageHorizontalPadding
import com.aivn.meow.ui.common.PageMaxWidth
import com.aivn.meow.ui.common.PageVerticalPadding
import com.aivn.meow.ui.sections.SectionRow
import com.aivn.meow.util.formatKst
import com.aivn.meow.util.formatSyncLabel
import com.aivn.meow.util.relativeTime

@Composable
fun Dashboard(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    favoriteRepos: Set<String>,
    orgRepos: List<String>?,
    onToggleFavorite: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MeowColors.Background),
    ) {
        when (state) {
            is DashboardUiState.Loading -> CenteredLoading("PR 목록을 불러오는 중…")
            is DashboardUiState.Error -> CenteredError(state.failure, onRefresh)
            is DashboardUiState.Loaded -> DashboardContent(state, onRefresh, onOpenPr, onOpenUrl, favoriteRepos, orgRepos, onToggleFavorite)
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Loaded,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    favoriteRepos: Set<String>,
    orgRepos: List<String>?,
    onToggleFavorite: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val bodyWidth = minOf(maxWidth - PageHorizontalPadding * 2, PageMaxWidth)
        DashboardBody(
            state = state,
            // 본문이 좁으면 사이드바를 숨기고 메인만 보여준다
            showSidebar = bodyWidth >= SidebarMinBodyWidth,
            onRefresh = onRefresh,
            onOpenPr = onOpenPr,
            onOpenUrl = onOpenUrl,
            favoriteRepos = favoriteRepos,
            orgRepos = orgRepos,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

private val SidebarWidth = 240.dp
private val SidebarMinBodyWidth = 760.dp

@Composable
private fun DashboardBody(
    state: DashboardUiState.Loaded,
    showSidebar: Boolean,
    onRefresh: () -> Unit,
    onOpenPr: (PullRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    favoriteRepos: Set<String>,
    orgRepos: List<String>?,
    onToggleFavorite: (String) -> Unit,
) {
    var sortOption by rememberSaveable(stateSaver = SortOptionSaver) { mutableStateOf(PrSortOption.OLDEST) }
    // 0 = 리뷰 대기 PR, 1.. = 보조 섹션. 앱은 항상 리뷰 대기 PR 탭으로 시작한다.
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // ↑/↓ · j/k 로 이동하는 키보드 포커스 인덱스 (현재 탭 항목 기준)
    // 키보드 선택 인덱스. -1 = 선택 없음 (↑↓ · j/k 를 처음 누를 때 선택 시작)
    var selectedIndex by rememberSaveable { mutableStateOf(-1) }
    // 본문을 펼친 카드 url. 탭 전환 · 새로고침 후에도 같은 url 이면 펼침을 유지한다.
    var expandedUrls by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    // 사이드바에서 고른 레포. null = 전체. 앱은 항상 전체로 시작한다.
    var selectedRepo by rememberSaveable { mutableStateOf<String?>(null) }
    var showRepoManager by remember { mutableStateOf(false) }
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

    val sections = snapshot.sections
    // '모두 확인' 으로 비운 섹션 id. 다음 로딩 결과(fetchedAtIso 가 바뀜)가 오면 초기화된다.
    // 다른 화면에 있는 동안 새 결과가 와도 초기화되도록 어느 결과에서 비웠는지 함께 저장한다.
    var clearedSections by rememberSaveable(stateSaver = ClearedSectionsSaver) {
        mutableStateOf(snapshot.fetchedAtIso to emptySet<String>())
    }
    val clearedSectionIds = clearedSections.second.takeIf { clearedSections.first == snapshot.fetchedAtIso }.orEmpty()
    fun itemsOf(result: SectionResult) = if (result.section.id in clearedSectionIds) emptyList() else result.items

    // 사이드바 레포 목록: 즐겨찾기 레포를 이름순으로 (항목이 없어도 표시). 개수는 같은 url 을 한 번만 센다.
    val repoEntries = prs.map { it.repo to it.url } +
        sections.flatMap { result -> itemsOf(result).map { it.repo to it.url } }
    val repoCounts = repoEntries.groupBy({ it.first }, { it.second }).mapValues { (_, urls) -> urls.toSet().size }
    val repos = favoriteRepos
        .sortedBy { it.lowercase() }
        .map { name -> RepoInfo(name, colorForRepo(name), repoCounts[name] ?: 0) }
    // 관리 모달: '작업 중' 요약과 전체 레포 (조직 목록을 못 불러왔으면 작업 중 + 즐겨찾기만)
    val workingSummaries = repoSummaries(prs.map { it.repo }, sections.map { result -> result to itemsOf(result).map { it.repo } })
    val managerRepos = ((orgRepos ?: emptyList()) + workingSummaries.keys + favoriteRepos)
        .distinct()
        .sortedBy { it.lowercase() }
    // 사이드바를 숨겼거나 즐겨찾기에서 빠진 레포면 전체를 보여준다
    val repoFilter = selectedRepo?.takeIf { repo -> showSidebar && repo in favoriteRepos }
    val visiblePrs = if (repoFilter == null) prs else prs.filter { it.repo == repoFilter }
    fun visibleItemsOf(result: SectionResult) =
        if (repoFilter == null) itemsOf(result) else itemsOf(result).filter { it.repo == repoFilter }

    val tabKeys = listOf(REVIEW_TAB_KEY) + sections.map { it.section.id }
    val allTabUrls = listOf(prs.map { it.url }) + sections.map { result -> itemsOf(result).map { it.url } }
    val tabUrls = listOf(visiblePrs.map { it.url }) + sections.map { result -> visibleItemsOf(result).map { it.url } }
    val tabIndex = selectedTab.coerceIn(0, tabKeys.lastIndex)
    val currentSection = sections.getOrNull(tabIndex - 1)
    val currentUrls = tabUrls[tabIndex]

    // 탭별로 마지막으로 본 항목 url (메모리만). 탭의 첫 정상 결과와 선택 중인 탭은 본 것으로 기록한다.
    // 레포를 골라 보는 중이면 화면에 보인 항목만 기존 기록에 더한다.
    // 화면 전환 후에도 유지해서, 다른 화면에 있는 동안 새로 생긴 항목의 빨간 점이 남게 한다.
    var seenUrls by rememberSaveable(stateSaver = SeenUrlsSaver) { mutableStateOf(emptyMap<String, Set<String>>()) }
    LaunchedEffect(allTabUrls, tabUrls, tabIndex) {
        seenUrls = seenUrls + tabKeys.indices
            .filter { i ->
                val loaded = i == 0 || sections[i - 1].errorMessage == null
                i == tabIndex || (tabKeys[i] !in seenUrls && loaded)
            }
            .associate { i ->
                val seen = seenUrls[tabKeys[i]]
                val urls = if (i == tabIndex && repoFilter != null && seen != null) seen + tabUrls[i] else allTabUrls[i]
                tabKeys[i] to urls.toSet()
            }
    }
    val tabs = tabKeys.indices.map { i ->
        val seen = seenUrls[tabKeys[i]]
        TabChipInfo(
            label = if (i == 0) "리뷰 대기 PR" else sections[i - 1].section.tabLabel,
            count = when {
                i == 0 -> visiblePrs.size
                sections[i - 1].section.id in clearedSectionIds -> 0
                repoFilter != null -> visibleItemsOf(sections[i - 1]).size
                else -> sections[i - 1].totalCount
            },
            hasNew = i != tabIndex && seen != null && tabUrls[i].any { it !in seen },
        )
    }

    fun selectTab(index: Int) {
        selectedTab = index
        selectedIndex = -1
    }

    fun selectRepo(repo: String?) {
        selectedRepo = repo
        selectedIndex = -1
    }

    fun toggleExpanded(url: String) {
        expandedUrls = if (url in expandedUrls) expandedUrls - url else expandedUrls + url
    }

    fun openSelected() {
        if (tabIndex == 0) visiblePrs.getOrNull(selectedIndex)?.let(onOpenPr) else currentUrls.getOrNull(selectedIndex)?.let(onOpenUrl)
    }

    // 정렬 · 새로고침으로 목록이 줄어들면 선택 인덱스를 유효 범위로 맞춘다
    LaunchedEffect(currentUrls.size) {
        if (selectedIndex >= 0) selectedIndex = selectedIndex.coerceAtMost(currentUrls.lastIndex)
    }
    // 시작 시 · 관리 모달을 닫은 뒤 키보드 단축키가 다시 동작하도록 포커스를 가져온다
    LaunchedEffect(showRepoManager) { if (!showRepoManager) focusRequester.requestFocus() }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // ⌘1~⌘9 탭 전환
                    val shortcutTab = TabShortcutKeys.indexOf(keyEvent.key)
                    if (keyEvent.isMetaPressed && shortcutTab in tabKeys.indices) {
                        selectTab(shortcutTab)
                        return@onPreviewKeyEvent true
                    }
                    when (keyEvent.key) {
                        Key.DirectionDown, Key.J -> {
                            if (currentUrls.isNotEmpty()) selectedIndex = (selectedIndex + 1).coerceIn(0, currentUrls.lastIndex)
                            true
                        }
                        Key.DirectionUp, Key.K -> {
                            if (currentUrls.isNotEmpty()) selectedIndex = if (selectedIndex < 0) 0 else (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            openSelected()
                            true
                        }
                        Key.Spacebar -> {
                            currentUrls.getOrNull(selectedIndex)?.let(::toggleExpanded)
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
                    .padding(horizontal = PageHorizontalPadding, vertical = PageVerticalPadding)
                    .fillMaxWidth()
                    .widthIn(max = PageMaxWidth),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PageHeader(
                    title = "GitHub",
                    syncLabel = "마지막 동기화",
                    syncValue = formatSyncLabel(snapshot.fetchedAtIso),
                    onSync = onRefresh,
                    syncOk = state.refreshError == null,
                    syncing = state.refreshing,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatCards(stats = stats, modifier = Modifier.fillMaxWidth())
                state.refreshError?.let { failure ->
                    RefreshErrorBanner(failure = failure, onRetry = onRefresh, modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (showSidebar) {
                        RepoSidebar(
                            repos = repos,
                            totalCount = repoEntries.distinctBy { it.second }.size,
                            selectedRepo = repoFilter,
                            onSelect = ::selectRepo,
                            onManage = { showRepoManager = true },
                            modifier = Modifier.width(SidebarWidth),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        DashboardTabBar(
                            tabs = tabs,
                            selectedIndex = tabIndex,
                            onSelect = ::selectTab,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (tabIndex == 0) SortChip(sortOption = sortOption, onSortSelect = { sortOption = it })
                            currentSection?.section?.headerAction?.let { action ->
                                GlassChip(
                                    text = action.label,
                                    onClick = {
                                        action.perform()
                                        clearedSections = snapshot.fetchedAtIso to (clearedSectionIds + currentSection.section.id)
                                    },
                                )
                            }
                        }
                        if (currentSection == null) {
                            if (visiblePrs.isEmpty()) {
                                EmptyStateCard("리뷰 요청이 없습니다 🎉", "여유로운 하루 보내세요", Modifier.fillMaxWidth())
                            } else {
                                TwoColumnGrid(visiblePrs, Modifier.fillMaxWidth()) { index, pr, cellModifier ->
                                    PrRow(
                                        pr = pr,
                                        isSelected = index == selectedIndex,
                                        isExpanded = pr.url in expandedUrls,
                                        onToggleExpand = { selectedIndex = -1; toggleExpanded(pr.url) },
                                        onOpen = { onOpenPr(pr) },
                                        modifier = cellModifier,
                                    )
                                }
                            }
                        } else {
                            SectionTabContent(
                                result = currentSection,
                                items = visibleItemsOf(currentSection),
                                selectedIndex = selectedIndex,
                                expandedUrls = expandedUrls,
                                onToggleExpand = { url -> selectedIndex = -1; toggleExpanded(url) },
                                onOpenUrl = onOpenUrl,
                            )
                        }
                    }
                }
            }
        }
        if (showRepoManager) {
            RepoManagerDialog(
                allRepos = managerRepos,
                summaries = workingSummaries,
                favorites = favoriteRepos,
                onToggleFavorite = onToggleFavorite,
                onDismiss = { showRepoManager = false },
            )
        }
    }
}

// 드로워로 다른 화면에 다녀와도 GitHub 화면 상태를 유지하기 위한 Saver (App 의 SaveableStateHolder 에 저장)
private val SortOptionSaver = Saver<PrSortOption, String>(save = { it.name }, restore = { PrSortOption.valueOf(it) })
private val StringSetSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })
private val SeenUrlsSaver = Saver<Map<String, Set<String>>, HashMap<String, ArrayList<String>>>(
    save = { map -> HashMap(map.mapValues { ArrayList(it.value) }) },
    restore = { map -> map.mapValues { it.value.toSet() } },
)
private val ClearedSectionsSaver = Saver<Pair<String, Set<String>>, ArrayList<String>>(
    save = { (fetchedAt, ids) -> ArrayList(listOf(fetchedAt) + ids) },
    restore = { it.first() to it.drop(1).toSet() },
)

/** ⌘ 와 함께 눌러 탭을 고르는 숫자 키. 인덱스 = 탭 인덱스. */
private val TabShortcutKeys = listOf(
    Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)

/** 새 항목 점 추적에 쓰는 리뷰 대기 PR 탭 키. 섹션 탭은 섹션 id 를 쓴다. */
private const val REVIEW_TAB_KEY = "review-requests"

@Composable
private fun SectionTabContent(
    result: SectionResult,
    items: List<SectionItem>,
    selectedIndex: Int,
    expandedUrls: Set<String>,
    onToggleExpand: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val section = result.section
    when {
        // 첫 로딩부터 실패해 보여줄 목록이 없으면 오류만 카드로 표시
        items.isEmpty() && result.errorMessage != null -> EmptyStateCard(
            title = "불러오지 못했어요",
            hint = result.errorMessage,
            modifier = Modifier.fillMaxWidth(),
            titleColor = MeowColors.Error,
        )
        items.isEmpty() -> EmptyStateCard(section.emptyTitle, section.emptyHint, Modifier.fillMaxWidth())
        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            result.errorMessage?.let { message ->
                Text(
                    text = "불러오지 못했어요 · $message",
                    color = MeowColors.Error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TwoColumnGrid(items, Modifier.fillMaxWidth()) { index, item, cellModifier ->
                SectionRow(
                    item = item,
                    isSelected = index == selectedIndex,
                    isExpanded = item.url in expandedUrls,
                    onToggleExpand = { onToggleExpand(item.url) },
                    onOpen = { onOpenUrl(item.url) },
                    modifier = cellModifier,
                )
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
