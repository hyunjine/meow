package com.aivn.meow.ui

import com.aivn.meow.config.FAVORITE_REPOS_FILE
import com.aivn.meow.config.RepoPrefs
import com.aivn.meow.config.SIDEBAR_REPOS_FILE
import com.aivn.meow.config.loadRepoList
import com.aivn.meow.config.restoreRepoPrefs
import com.aivn.meow.config.saveRepoList
import com.aivn.meow.data.DashboardSnapshot
import com.aivn.meow.data.PrRepository
import com.aivn.meow.data.RepoUniverse
import com.aivn.meow.data.keepPreviousOnError
import com.aivn.meow.data.workingRepos
import com.aivn.meow.github.GithubApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    /** [refreshError] 가 있으면 마지막 성공 데이터를 보여주는 중 새로고침이 실패한 상태. */
    data class Loaded(
        val snapshot: DashboardSnapshot,
        val refreshing: Boolean = false,
        val refreshError: LoadFailure? = null,
    ) : DashboardUiState
    data class Error(val failure: LoadFailure) : DashboardUiState
}

/** 오류 UI 가 원인별로 다른 안내를 하기 위한 실패 분류. */
sealed interface LoadFailure {
    val message: String

    /** 토큰 만료 · 무효 · 권한 부족 → 토큰 교체 안내. */
    data class Auth(override val message: String) : LoadFailure

    /** Rate limit 초과 → 남은 쿼터 · 리셋 시각 안내. */
    data class RateLimited(
        override val message: String,
        val remaining: Int?,
        val limit: Int?,
        val resetAt: Instant?,
    ) : LoadFailure

    data class Other(override val message: String) : LoadFailure
}

private fun Throwable.toLoadFailure(): LoadFailure {
    val message = message ?: this::class.simpleName ?: "unknown error"
    return when (this) {
        is GithubApiException.Unauthorized -> LoadFailure.Auth(message)
        is GithubApiException.RateLimited -> LoadFailure.RateLimited(message, remaining, limit, resetAt)
        else -> LoadFailure.Other(message)
    }
}

/** PR 목록 정렬 옵션. [label] 은 정렬 칩/드롭다운에 표시되는 문구. */
enum class PrSortOption(val label: String) {
    OLDEST("오래된 순"),
    NEWEST("최신 순"),
    BY_REPO("리포지토리별"),
}

class DashboardViewModel(
    private val repository: PrRepository,
    private val org: String,
    private val scope: CoroutineScope,
    private val autoRefreshIntervalMs: Long = 60_000L,
) {
    private val _state = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    /** 한 번의 조회(또는 Realtime 이벤트)에서 생긴 알림 묶음. */
    private val _notices = MutableSharedFlow<List<MeowNotice>>(extraBufferCapacity = 8)
    val notices: SharedFlow<List<MeowNotice>> = _notices.asSharedFlow()

    /** #127 관리 모달의 레포 목록 (조직 + 개인). 아직 못 불러왔거나 실패했으면 null. */
    private val _repoUniverse = MutableStateFlow<RepoUniverse?>(null)
    val repoUniverse: StateFlow<RepoUniverse?> = _repoUniverse.asStateFlow()

    /**
     * #127 즐겨찾기 · 사이드바 체크. 즐겨찾기 파일이 없으면 첫 정상 로딩 후 '작업 중' 레포로 한 번 채우고 모두 체크한다.
     * 예전 형식(이름만) 즐겨찾기는 `org/이름` 으로 옮기고, 체크 파일이 없으면 즐겨찾기를 모두 체크해 저장한다.
     */
    private val restoredPrefs = restoreRepoPrefs(loadRepoList(FAVORITE_REPOS_FILE), loadRepoList(SIDEBAR_REPOS_FILE), org)
    private var favoritesSeeded = restoredPrefs.prefs != null
    private val _repoPrefs = MutableStateFlow(restoredPrefs.prefs ?: RepoPrefs())
    val repoPrefs: StateFlow<RepoPrefs> = _repoPrefs.asStateFlow()

    private var pollJob: Job? = null
    private var manualJob: Job? = null
    private var repoUniverseJob: Job? = null

    private var seenIds: Set<String> = emptySet()
    private var seenInitialised: Boolean = false
    private val sectionTracker = SectionNoticeTracker()

    init {
        if (restoredPrefs.needsSave) saveRepoPrefs()
    }

    fun start() {
        if (pollJob?.isActive == true) return
        loadRepoUniverse()
        pollJob = scope.launch {
            fetchOnce(auto = false)
            while (isActive) {
                delay(autoRefreshIntervalMs)
                fetchOnce(auto = true)
            }
        }
    }

    fun refresh() {
        manualJob?.cancel()
        manualJob = scope.launch { fetchOnce(auto = false) }
        loadRepoUniverse()
    }

    /** 드로워로 화면에 들어올 때: 이미 불러오는 중이면 건너뛰고, 아니면 동기화 버튼과 같이 새로 불러온다. */
    fun syncIfIdle() {
        val s = _state.value
        if (s is DashboardUiState.Loading || (s is DashboardUiState.Loaded && s.refreshing)) return
        if (manualJob?.isActive == true) return
        refresh()
    }

    /** 관리 모달의 ☆/★. 해제하면 사이드바 체크도 함께 지운다. 바로 저장한다. */
    fun toggleFavorite(repo: String) {
        _repoPrefs.value = _repoPrefs.value.toggleFavorite(repo)
        favoritesSeeded = true
        saveRepoPrefs()
    }

    /** 사이드바 체크 토글. 바로 저장한다. */
    fun toggleSidebarRepo(repo: String) {
        _repoPrefs.value = _repoPrefs.value.toggleChecked(repo)
        saveRepoPrefs()
    }

    private fun saveRepoPrefs() {
        val prefs = _repoPrefs.value
        saveRepoList(FAVORITE_REPOS_FILE, prefs.favorites)
        saveRepoList(SIDEBAR_REPOS_FILE, prefs.checkedFavorites)
    }

    /** 실패해도 대시보드에는 영향 없이 직전 목록(없으면 null)을 유지한다. */
    private fun loadRepoUniverse() {
        repoUniverseJob?.cancel()
        repoUniverseJob = scope.launch {
            runCatching { repository.loadRepoUniverse(org) }.onSuccess { _repoUniverse.value = it }
        }
    }

    /**
     * Realtime push 로 도착한 알림 하나를 조회 기준값(seen set)에 반영 + 알림 emit.
     * 같은 url · 종류를 조회가 이미 알렸으면 생략하고, 먼저 알렸으면 다음 조회 diff 에서 생략된다.
     */
    fun onRealtimeNotice(notice: MeowNotice) {
        if (notice is MeowNotice.ReviewRequested) {
            val pr = notice.pr
            if (seenInitialised && pr.url in seenIds) return
            seenIds = seenIds + pr.url
        } else if (!sectionTracker.acceptRealtime(notice)) {
            return
        }
        scope.launch { _notices.emit(listOf(notice)) }
    }

    private suspend fun fetchOnce(auto: Boolean) {
        val prior = _state.value
        if (prior is DashboardUiState.Loaded) {
            _state.value = prior.copy(refreshing = true)
        } else if (!auto) {
            _state.value = DashboardUiState.Loading
        }
        runCatching { repository.load(org, Clock.System.now().toString()) }
            .onSuccess { loaded ->
                val previousSections = (prior as? DashboardUiState.Loaded)?.snapshot?.sections.orEmpty()
                val snapshot = loaded.copy(sections = loaded.sections.keepPreviousOnError(previousSections))
                emitDiff(snapshot)
                if (!favoritesSeeded) {
                    favoritesSeeded = true
                    _repoPrefs.value = RepoPrefs.seeded(snapshot.workingRepos())
                    saveRepoPrefs()
                }
                _state.value = DashboardUiState.Loaded(snapshot, refreshing = false)
            }
            .onFailure { throwable ->
                val failure = throwable.toLoadFailure()
                // 이미 목록이 있으면 오류 화면으로 덮지 않고 마지막 성공 데이터를 유지.
                // 자동 새로고침 실패는 조용히 넘기고, 수동 새로고침 실패만 배너로 알린다.
                _state.value = when {
                    prior !is DashboardUiState.Loaded -> DashboardUiState.Error(failure)
                    auto -> prior.copy(refreshing = false)
                    else -> prior.copy(refreshing = false, refreshError = failure)
                }
            }
    }

    private suspend fun emitDiff(snapshot: DashboardSnapshot) {
        val notices = reviewRequestNotices(snapshot) + sectionTracker.diff(snapshot.sections)
        if (notices.isNotEmpty()) _notices.emit(notices)
    }

    private fun reviewRequestNotices(snapshot: DashboardSnapshot): List<MeowNotice> {
        val currentIds = snapshot.pullRequests.map { it.url }.toSet()
        if (!seenInitialised) {
            seenIds = currentIds
            seenInitialised = true
            return emptyList()
        }
        val fresh = snapshot.pullRequests.filter { it.url !in seenIds }
        seenIds = currentIds
        return fresh.map { MeowNotice.ReviewRequested(it) }
    }
}
