package com.aivn.meow.ui

import com.aivn.meow.data.DashboardSnapshot
import com.aivn.meow.data.PrRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Loaded(val snapshot: DashboardSnapshot, val refreshing: Boolean = false) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    private val repository: PrRepository,
    private val org: String,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var currentJob: Job? = null

    fun start() {
        if (currentJob?.isActive == true) return
        refresh()
    }

    fun refresh() {
        currentJob?.cancel()
        val prior = _state.value
        if (prior is DashboardUiState.Loaded) {
            _state.value = prior.copy(refreshing = true)
        } else {
            _state.value = DashboardUiState.Loading
        }
        currentJob = scope.launch {
            runCatching { repository.load(org, Clock.System.now().toString()) }
                .onSuccess { _state.value = DashboardUiState.Loaded(it, refreshing = false) }
                .onFailure { _state.value = DashboardUiState.Error(it.message ?: it::class.simpleName ?: "unknown error") }
        }
    }
}
