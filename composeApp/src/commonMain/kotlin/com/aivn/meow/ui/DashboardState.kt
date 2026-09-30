package com.aivn.meow.ui

import com.aivn.meow.data.DashboardSnapshot
import com.aivn.meow.data.PrRepository
import com.aivn.meow.model.PullRequest
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
import kotlinx.datetime.Clock

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    /** [refreshError] 가 있으면 마지막 성공 데이터를 보여주는 중 새로고침이 실패한 상태. */
    data class Loaded(
        val snapshot: DashboardSnapshot,
        val refreshing: Boolean = false,
        val refreshError: String? = null,
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    private val repository: PrRepository,
    private val org: String,
    private val scope: CoroutineScope,
    private val autoRefreshIntervalMs: Long = 60_000L,
) {
    private val _state = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _newRequests = MutableSharedFlow<List<PullRequest>>(extraBufferCapacity = 8)
    val newRequests: SharedFlow<List<PullRequest>> = _newRequests.asSharedFlow()

    private var pollJob: Job? = null
    private var manualJob: Job? = null

    private var seenIds: Set<String> = emptySet()
    private var seenInitialised: Boolean = false

    fun start() {
        if (pollJob?.isActive == true) return
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
    }

    /** Realtime push 로 도착한 신규 PR 하나를 내부 seen set 에 반영 + 알림 emit. */
    fun onRealtimeEvent(pr: PullRequest) {
        val alreadySeen = seenInitialised && pr.url in seenIds
        if (alreadySeen) return
        seenIds = seenIds + pr.url
        scope.launch { _newRequests.emit(listOf(pr)) }
    }

    private suspend fun fetchOnce(auto: Boolean) {
        val prior = _state.value
        if (prior is DashboardUiState.Loaded) {
            _state.value = prior.copy(refreshing = true)
        } else if (!auto) {
            _state.value = DashboardUiState.Loading
        }
        runCatching { repository.load(org, Clock.System.now().toString()) }
            .onSuccess { snapshot ->
                emitDiff(snapshot)
                _state.value = DashboardUiState.Loaded(snapshot, refreshing = false)
            }
            .onFailure { throwable ->
                val message = throwable.message ?: throwable::class.simpleName ?: "unknown error"
                // 이미 목록이 있으면 오류 화면으로 덮지 않고 마지막 성공 데이터를 유지.
                // 자동 새로고침 실패는 조용히 넘기고, 수동 새로고침 실패만 배너로 알린다.
                _state.value = when {
                    prior !is DashboardUiState.Loaded -> DashboardUiState.Error(message)
                    auto -> prior.copy(refreshing = false)
                    else -> prior.copy(refreshing = false, refreshError = message)
                }
            }
    }

    private suspend fun emitDiff(snapshot: DashboardSnapshot) {
        val currentIds = snapshot.pullRequests.map { it.url }.toSet()
        if (!seenInitialised) {
            seenIds = currentIds
            seenInitialised = true
            return
        }
        val fresh = snapshot.pullRequests.filter { it.url !in seenIds }
        seenIds = currentIds
        if (fresh.isNotEmpty()) _newRequests.emit(fresh)
    }
}
