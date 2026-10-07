package com.aivn.meow.ui.schedule

import com.aivn.meow.ms.GraphApiException
import com.aivn.meow.ms.MsAuth
import com.aivn.meow.ms.MsAuthState
import com.aivn.meow.ms.MsNotConnectedException
import com.aivn.meow.schedule.CalendarNotFoundException
import com.aivn.meow.schedule.ScheduleRepository
import com.aivn.meow.schedule.ScheduleWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** 한 주의 불러오기 결과. */
sealed interface ScheduleContent {
    data object Loading : ScheduleContent

    /** TeamAIVN 캘린더가 내 캘린더 목록에 없음. */
    data object CalendarNotFound : ScheduleContent

    data class Failed(val message: String) : ScheduleContent

    data class Loaded(val week: ScheduleWeek) : ScheduleContent
}

data class ScheduleUiState(
    /** 보고 있는 주의 월요일. */
    val weekStart: LocalDate,
    val today: LocalDate,
    /** 이번 세션에 불러온 주(월요일 → 결과). 없으면 아직 안 불러옴. */
    val weeks: Map<LocalDate, ScheduleContent> = emptyMap(),
    val syncing: Boolean = false,
    val lastSyncAt: Instant? = null,
    val lastSyncFailed: Boolean = false,
) {
    val content: ScheduleContent? get() = weeks[weekStart]
}

/**
 * 일정 화면 상태. App 수준에서 만들어 화면 전환에도 불러온 주를 유지한다.
 * 화면이 열리고 Microsoft 계정이 연결돼 있으면 보고 있는 주를 (아직 없을 때만) 불러온다.
 */
class ScheduleViewModel(
    val auth: MsAuth,
    private val repository: ScheduleRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val timeZone = TimeZone.of("Asia/Seoul")
    private val _state = MutableStateFlow(today().let { ScheduleUiState(weekStart = it.mondayOfWeek(), today = it) })
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        // 로그아웃 · 연결 끊김이면 불러온 주와 캘린더 id 를 버린다(다른 계정으로 다시 연결될 수 있음).
        scope.launch {
            auth.state.collect { auth ->
                if (auth is MsAuthState.NotConnected) {
                    loadJob?.cancel()
                    repository.reset()
                    _state.update { it.copy(weeks = emptyMap(), syncing = false, lastSyncAt = null, lastSyncFailed = false) }
                }
            }
        }
    }

    fun signIn() {
        if (auth.state.value is MsAuthState.Connecting) return
        scope.launch { auth.signIn() }
    }

    /** 보고 있는 주를 아직 안 불러왔으면 불러온다. */
    fun ensureLoaded() {
        val s = _state.value
        if (s.weeks[s.weekStart] == null || s.weeks[s.weekStart] is ScheduleContent.Failed) load(s.weekStart)
    }

    fun previousWeek() = showWeek(_state.value.weekStart.minus(7, DateTimeUnit.DAY))

    fun nextWeek() = showWeek(_state.value.weekStart.plus(7, DateTimeUnit.DAY))

    fun thisWeek() {
        val today = today()
        _state.update { it.copy(today = today) }
        showWeek(today.mondayOfWeek())
    }

    /** 보고 있는 주를 다시 불러온다. */
    fun sync() = load(_state.value.weekStart, force = true)

    /** 드로워로 화면에 들어올 때: 불러오는 중이거나 연결 안 됐으면 건너뛴다. */
    fun syncIfIdle() {
        if (_state.value.syncing) return
        sync()
    }

    private fun showWeek(monday: LocalDate) {
        _state.update { it.copy(weekStart = monday) }
        if (_state.value.weeks[monday].let { it == null || it is ScheduleContent.Failed }) load(monday)
    }

    private fun load(monday: LocalDate, force: Boolean = false) {
        if (auth.state.value !is MsAuthState.Connected) return
        loadJob?.cancel()
        _state.update {
            val keep = it.weeks[monday]
            val placeholder = if (force && keep is ScheduleContent.Loaded) keep else ScheduleContent.Loading
            it.copy(weeks = it.weeks + (monday to placeholder), syncing = true, today = today())
        }
        loadJob = scope.launch {
            val content = try {
                ScheduleContent.Loaded(withContext(Dispatchers.Default) { repository.loadWeek(monday) })
            } catch (e: CancellationException) {
                // 다른 주로 넘어가며 취소됨: 로딩 자리표시는 지워 다음에 다시 불러오게 한다.
                _state.update { s ->
                    if (s.weeks[monday] is ScheduleContent.Loading) s.copy(weeks = s.weeks - monday) else s
                }
                throw e
            } catch (e: CalendarNotFoundException) {
                ScheduleContent.CalendarNotFound
            } catch (e: Exception) {
                ScheduleContent.Failed(e.toUserMessage())
            }
            _state.update {
                it.copy(
                    weeks = it.weeks + (monday to content),
                    syncing = false,
                    lastSyncAt = clock.now(),
                    lastSyncFailed = content is ScheduleContent.Failed,
                )
            }
        }
    }

    private fun today(): LocalDate = clock.todayIn(timeZone)
}

/** 그 주의 월요일. */
internal fun LocalDate.mondayOfWeek(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** ISO 8601 주차(그 주 목요일이 속한 해 기준). */
internal fun LocalDate.isoWeekNumber(): Int = (mondayOfWeek().plus(3, DateTimeUnit.DAY).dayOfYear - 1) / 7 + 1

private fun Throwable.toUserMessage(): String = when (this) {
    is MsNotConnectedException -> message ?: "Microsoft 계정을 다시 연결해 주세요"
    is GraphApiException -> if (status == 403) {
        "캘린더를 읽을 권한이 없어요. Microsoft 계정에 캘린더 읽기 권한(Calendars.Read)이 필요해요"
    } else {
        message ?: "Graph 오류 ($status)"
    }
    else -> message ?: this::class.simpleName ?: "알 수 없는 오류"
}
