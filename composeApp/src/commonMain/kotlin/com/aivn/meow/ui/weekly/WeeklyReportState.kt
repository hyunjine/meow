package com.aivn.meow.ui.weekly

import com.aivn.meow.ms.DocumentChangedException
import com.aivn.meow.ms.DocumentLockedException
import com.aivn.meow.ms.MsAuth
import com.aivn.meow.ms.MsAuthState
import com.aivn.meow.ms.MsNotConnectedException
import com.aivn.meow.weekly.DateRange
import com.aivn.meow.weekly.MyWeeklyRow
import com.aivn.meow.weekly.ThisWeekDoc
import com.aivn.meow.weekly.WeekDoc
import com.aivn.meow.weekly.WeeklyDraftBuilder
import com.aivn.meow.weekly.WeeklyReportRepository
import com.aivn.meow.weekly.isoWeekNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** 마지막 동기화 결과. [doc] 이 null 이면 이번 주 문서를 못 찾음. [failed] 면 동기화 자체가 실패. */
data class WeeklySyncInfo(val at: Instant, val doc: ThisWeekDoc?, val failed: Boolean = false)

sealed interface WeeklyContent {
    /** 앱 시작 후 아직 동기화하지 않음(자동 동기화는 하지 않는다). */
    data object Idle : WeeklyContent

    /** 이번 주 메일 · 문서를 아직 못 찾음. */
    data object NotFound : WeeklyContent

    data class Failed(val message: String) : WeeklyContent

    /**
     * 이번 주 문서의 내 행. [resultText] · [planText] 는 편집 중인 텍스트(줄 = 셀 문단).
     * [resultIsDraft] 면 실적 칸을 PR 초안으로 채운 상태. [eTag] 는 저장 시 넘길 읽은 시점 버전.
     */
    data class Found(
        val thisWeek: ThisWeekDoc,
        val row: MyWeeklyRow,
        val resultText: String,
        val planText: String,
        val resultIsDraft: Boolean,
        val draftError: String? = null,
        val drafting: Boolean = false,
        val eTag: String?,
        val saving: Boolean = false,
        val saveMessage: SaveMessage? = null,
    ) : WeeklyContent
}

data class SaveMessage(val text: String, val ok: Boolean)

/** 주차 목록의 한 문서 상태. null(아직 모름) 이면 '—'. */
enum class WeekRowStatus { Done, Empty }

data class WeeklyUiState(
    val reportName: String? = null,
    val syncing: Boolean = false,
    val lastSync: WeeklySyncInfo? = null,
    val content: WeeklyContent = WeeklyContent.Idle,
    /** 최근 주차 문서(주차 내림차순, 최대 [WeeklyReportViewModel.WEEK_LIST_SIZE]). */
    val weeks: List<WeekDoc> = emptyList(),
    /** itemId → 내 행 상태. 이번 주 문서는 [WeeklyContent.Found] 로 판단한다. */
    val weekStatus: Map<String, WeekRowStatus> = emptyMap(),
    val currentWeek: Int,
)

/**
 * 주간 보고 화면 상태. App 수준에서 만들어 화면 전환에도 상태(편집 중 텍스트 포함)를 유지한다.
 * 동기화는 사용자가 누를 때만 한다.
 */
class WeeklyReportViewModel(
    val auth: MsAuth,
    private val repository: WeeklyReportRepository,
    private val draftBuilder: WeeklyDraftBuilder,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val timeZone = TimeZone.of("Asia/Seoul")
    private val _state = MutableStateFlow(
        WeeklyUiState(reportName = WeeklyReportRepository.loadReportName(), currentWeek = today().isoWeekNumber()),
    )
    val state: StateFlow<WeeklyUiState> = _state.asStateFlow()

    private var syncJob: Job? = null
    private var statusJob: Job? = null

    /** 앱 시작 시 저장된 계정으로 조용히 다시 연결한다(동기화는 하지 않음). */
    fun start() {
        scope.launch { auth.restore() }
    }

    fun signIn() {
        if (auth.state.value is MsAuthState.Connecting) return
        scope.launch { auth.signIn() }
    }

    fun signOut() {
        syncJob?.cancel()
        statusJob?.cancel()
        scope.launch {
            auth.signOut()
            _state.update { WeeklyUiState(reportName = it.reportName, currentWeek = it.currentWeek) }
        }
    }

    fun saveReportName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        WeeklyReportRepository.saveReportName(trimmed)
        // 이름이 바뀌면 이전 이름으로 읽은 행은 버린다.
        _state.update { WeeklyUiState(reportName = trimmed, currentWeek = it.currentWeek) }
    }

    fun clearReportName() {
        _state.update { it.copy(reportName = null) }
    }

    fun sync() {
        val name = _state.value.reportName ?: return
        if (_state.value.syncing || auth.state.value !is MsAuthState.Connected) return
        statusJob?.cancel()
        _state.update { it.copy(syncing = true, currentWeek = today().isoWeekNumber()) }
        syncJob = scope.launch {
            try {
                val today = today()
                val thisWeek = io { repository.findThisWeekDoc(today) }
                val weeks = io { runCatching { repository.listWeekDocs() }.getOrDefault(emptyList()) }
                    .take(WEEK_LIST_SIZE)
                val content = if (thisWeek == null) WeeklyContent.NotFound else loadFound(thisWeek, name)
                _state.update {
                    it.copy(
                        syncing = false,
                        lastSync = WeeklySyncInfo(clock.now(), thisWeek),
                        content = content,
                        weeks = weeks,
                        weekStatus = emptyMap(),
                    )
                }
                loadPastStatuses(weeks, thisWeek, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        syncing = false,
                        lastSync = WeeklySyncInfo(clock.now(), it.lastSync?.doc, failed = true),
                        content = WeeklyContent.Failed(e.toUserMessage()),
                    )
                }
            }
        }
    }

    fun editResult(text: String) = updateFound { it.copy(resultText = text, saveMessage = null) }

    fun editPlan(text: String) = updateFound { it.copy(planText = text, saveMessage = null) }

    /** 실적 칸을 PR 초안으로 다시 채운다(편집 중이던 실적 텍스트는 덮어쓴다). */
    fun regenerateDraft() {
        val found = _state.value.content as? WeeklyContent.Found ?: return
        if (found.drafting) return
        updateFound { it.copy(drafting = true, draftError = null, saveMessage = null) }
        scope.launch {
            val result = runCatching { io { draftBuilder.buildResultDraft(resultPeriod(found.row, found.thisWeek.doc)).lines } }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            updateFound { current ->
                result.fold(
                    onSuccess = { lines ->
                        if (lines.isEmpty()) {
                            current.copy(drafting = false, draftError = "실적 기간에 머지한 PR 이 없어요")
                        } else {
                            current.copy(drafting = false, resultText = lines.joinToString("\n"), resultIsDraft = true)
                        }
                    },
                    onFailure = { current.copy(drafting = false, draftError = "PR 초안을 만들지 못했어요: ${it.toUserMessage()}") },
                )
            }
        }
    }

    /** 확인 다이얼로그를 거친 뒤 호출. 내 행의 실적 · 계획 셀만 바꿔 올린다. */
    fun publish() {
        val found = _state.value.content as? WeeklyContent.Found ?: return
        val name = _state.value.reportName ?: return
        if (found.saving) return
        updateFound { it.copy(saving = true, saveMessage = null) }
        scope.launch {
            val results = found.resultText.toCellLines()
            val plans = found.planText.toCellLines()
            try {
                val newETag = io { repository.writeMyRow(found.thisWeek.doc, name, results, plans, found.eTag) }
                updateFound {
                    it.copy(
                        saving = false,
                        row = it.row.copy(results = results, plans = plans),
                        resultIsDraft = false,
                        eTag = newETag.ifEmpty { null },
                        saveMessage = SaveMessage("반영 완료 · ${formatTime(clock.now())}", ok = true),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is DocumentChangedException -> "그사이 문서가 바뀌었어요. 다시 동기화해 주세요"
                    is DocumentLockedException -> "다른 사람이 편집 중이라 지금은 저장할 수 없어요"
                    else -> "반영하지 못했어요: ${e.toUserMessage()}"
                }
                updateFound { it.copy(saving = false, saveMessage = SaveMessage(message, ok = false)) }
            }
        }
    }

    /** 내 행 읽기와 PR 초안을 함께 시작한다. 초안 기간은 우선 ISO 주차(월~금)로 잡고, 문서 헤더 기간이 다르면 다시 만든다. */
    private suspend fun loadFound(thisWeek: ThisWeekDoc, name: String): WeeklyContent.Found = withContext(Dispatchers.Default) {
        val guessed = weekdayRange(thisWeek.doc.week)
        val draftAsync = async { runCatching { draftBuilder.buildResultDraft(guessed).lines } }
        val row = repository.readMyRow(thisWeek.doc, name)
        val headerPeriod = row.header.result.period
        var draft = draftAsync.await()
        if (headerPeriod != null && headerPeriod != guessed) {
            draft = runCatching { draftBuilder.buildResultDraft(headerPeriod).lines }
        }
        draft.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val draftLines = draft.getOrNull()
        val fillDraft = row.results.isEmpty() && !draftLines.isNullOrEmpty()
        WeeklyContent.Found(
            thisWeek = thisWeek,
            row = row,
            resultText = if (fillDraft) draftLines.orEmpty().joinToString("\n") else row.results.joinToString("\n"),
            planText = row.plans.joinToString("\n"),
            resultIsDraft = fillDraft,
            draftError = draft.exceptionOrNull()?.let { "PR 초안을 만들지 못했어요: ${it.toUserMessage()}" },
            eTag = row.eTag.ifEmpty { null },
        )
    }

    /** 이번 주 외 주차의 내 행 상태를 뒤에서 하나씩 확인한다(실패하면 '—' 로 둔다). */
    private fun loadPastStatuses(weeks: List<WeekDoc>, thisWeek: ThisWeekDoc?, name: String) {
        statusJob = scope.launch {
            for (doc in weeks) {
                if (doc.itemId == thisWeek?.doc?.itemId) continue
                val row = runCatching { io { repository.readMyRow(doc, name) } }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: continue
                if (!row.found) continue
                val status = if (row.results.isNotEmpty()) WeekRowStatus.Done else WeekRowStatus.Empty
                _state.update { it.copy(weekStatus = it.weekStatus + (doc.itemId to status)) }
            }
        }
    }

    private fun resultPeriod(row: MyWeeklyRow, doc: WeekDoc): DateRange =
        row.header.result.period ?: weekdayRange(doc.week)

    /** 올해 ISO [week] 주차의 월~금. */
    private fun weekdayRange(week: Int): DateRange {
        val monday = isoWeekMonday(today().year, week)
        return DateRange(monday, monday.plus(4, DateTimeUnit.DAY))
    }

    private fun updateFound(transform: (WeeklyContent.Found) -> WeeklyContent.Found) {
        _state.update { s ->
            val found = s.content as? WeeklyContent.Found ?: return@update s
            s.copy(content = transform(found))
        }
    }

    private fun today(): LocalDate = clock.todayIn(timeZone)

    private fun formatTime(instant: Instant): String = formatSyncTime(instant, clock.now())

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        const val WEEK_LIST_SIZE = 6
    }
}

/** ISO 주차 [week] 의 월요일. 1월 4일이 든 주가 1주차. */
internal fun isoWeekMonday(year: Int, week: Int): LocalDate {
    val jan4 = LocalDate(year, 1, 4)
    val week1Monday = jan4.minus(jan4.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal, DateTimeUnit.DAY)
    return week1Monday.plus((week - 1) * 7, DateTimeUnit.DAY)
}

/** 편집 텍스트 → 셀 문단. 줄 끝 공백을 자르고 빈 줄은 뺀다. */
internal fun String.toCellLines(): List<String> = lines().map { it.trimEnd() }.filter { it.isNotBlank() }

/** 같은 날이면 `오늘 HH:mm`, 아니면 `M/d HH:mm` (KST). */
internal fun formatSyncTime(instant: Instant, now: Instant): String {
    val kst = TimeZone.of("Asia/Seoul")
    val local = instant.toLocalDateTime(kst)
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return if (local.date == now.toLocalDateTime(kst).date) "오늘 $time" else "${local.monthNumber}/${local.dayOfMonth} $time"
}

private fun Throwable.toUserMessage(): String = when (this) {
    is MsNotConnectedException -> message ?: "Microsoft 계정을 다시 연결해 주세요"
    else -> message ?: this::class.simpleName ?: "알 수 없는 오류"
}
