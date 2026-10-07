package com.aivn.meow.ui.cafeteria

import com.aivn.meow.cafeteria.CafeteriaRepository
import com.aivn.meow.cafeteria.CafeteriaWeek
import com.aivn.meow.cafeteria.KST
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class CafeteriaUiState(
    val today: LocalDate,
    val selectedMonday: LocalDate,
    /** 세션 동안 받아 둔 주. */
    val weeks: Map<LocalDate, CafeteriaWeek> = emptyMap(),
    val loading: Set<LocalDate> = emptySet(),
    val errors: Map<LocalDate, String> = emptyMap(),
    val lastSync: Instant? = null,
    val lastSyncFailed: Boolean = false,
) {
    val currentMonday: LocalDate get() = today.weekMonday()
    val selectedWeek: CafeteriaWeek? get() = weeks[selectedMonday]
    val selectedLoading: Boolean get() = selectedMonday in loading
}

fun LocalDate.weekMonday(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/**
 * 구내식당 화면 상태. App 수준에서 만들어 화면 전환에도 받아 둔 주를 유지한다.
 * 평일 11:00–13:30(KST)에는 이번 주를 5분마다 다시 받아 그날 중식 게시물을 자동으로 띄운다.
 */
class CafeteriaViewModel(
    private val repository: CafeteriaRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val _state = MutableStateFlow(today().let { CafeteriaUiState(today = it, selectedMonday = it.weekMonday()) })
    val state: StateFlow<CafeteriaUiState> = _state.asStateFlow()

    private val fetchedAt = mutableMapOf<LocalDate, Instant>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                rollToday()
                if (isLunchWindow()) load(_state.value.currentMonday, force = false, maxAge = REFRESH_INTERVAL.inWholeMilliseconds)
            }
        }
    }

    /** 화면에 들어올 때: 처음이거나 5분이 지났으면 다시 받는다. */
    fun onShown() {
        rollToday()
        load(_state.value.selectedMonday, force = false, maxAge = REFRESH_INTERVAL.inWholeMilliseconds)
    }

    fun sync() = load(_state.value.selectedMonday, force = true)

    /** 드로워로 화면에 들어올 때: 보고 있는 주를 받는 중이면 건너뛴다. */
    fun syncIfIdle() {
        rollToday()
        sync()
    }

    fun previousWeek() = select(_state.value.selectedMonday.minus(7, DateTimeUnit.DAY))

    fun nextWeek() = select(_state.value.selectedMonday.plus(7, DateTimeUnit.DAY))

    fun thisWeek() {
        rollToday()
        select(_state.value.currentMonday)
    }

    private fun select(monday: LocalDate) {
        _state.update { it.copy(selectedMonday = monday) }
        // 이번 주는 5분 지나면 다시, 다른 주는 받아 둔 게 있으면 그대로 쓴다.
        val maxAge = if (monday == _state.value.currentMonday) REFRESH_INTERVAL.inWholeMilliseconds else Long.MAX_VALUE
        load(monday, force = false, maxAge = maxAge)
    }

    private fun load(monday: LocalDate, force: Boolean, maxAge: Long = Long.MAX_VALUE) {
        val s = _state.value
        if (monday in s.loading) return
        val at = fetchedAt[monday]
        if (!force && at != null && monday in s.weeks && (clock.now() - at).inWholeMilliseconds < maxAge) return
        _state.update { it.copy(loading = it.loading + monday) }
        scope.launch {
            try {
                val week = withContext(Dispatchers.Default) { repository.loadWeek(monday) }
                fetchedAt[monday] = clock.now()
                _state.update {
                    it.copy(
                        weeks = it.weeks + (monday to week),
                        loading = it.loading - monday,
                        errors = it.errors - monday,
                        lastSync = clock.now(),
                        lastSyncFailed = false,
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(loading = it.loading - monday) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = it.loading - monday,
                        errors = it.errors + (monday to (e.message ?: "불러오지 못했어요")),
                        lastSync = clock.now(),
                        lastSyncFailed = true,
                    )
                }
            }
        }
    }

    private fun rollToday() {
        val today = today()
        if (today != _state.value.today) _state.update { it.copy(today = today) }
    }

    private fun isLunchWindow(): Boolean {
        val now = clock.now().toLocalDateTime(KST)
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false
        val minutes = now.hour * 60 + now.minute
        return minutes in (11 * 60)..(13 * 60 + 30)
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(KST).date

    companion object {
        val REFRESH_INTERVAL = 5.minutes
    }
}
