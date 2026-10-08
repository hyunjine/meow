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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/** 마지막 동기화 결과. [doc] 이 null 이면 이번 주 문서를 못 찾음. [failed] 면 동기화 자체가 실패. */
data class WeeklySyncInfo(val at: Instant, val doc: ThisWeekDoc?, val failed: Boolean = false)

sealed interface WeeklyContent {
    /** 아직 동기화 결과가 없음(보관한 결과도 없음). */
    data object Idle : WeeklyContent

    /** 이번 주 메일 · 문서를 아직 못 찾음. */
    data object NotFound : WeeklyContent

    data class Failed(val message: String) : WeeklyContent

    /**
     * 이번 주 문서의 내 행. [resultText] · [planText] 는 편집 중인 텍스트(줄 = 셀 문단).
     * [resultIsDraft] 면 실적 칸을 PR 초안으로 채운 상태. [eTag] 는 저장 시 넘길 읽은 시점 버전.
     * [draftLines] 는 동기화 때 만든 PR 초안. [edited] 면 사용자가 텍스트를 바꾼 뒤 아직 반영하지 않았다.
     */
    data class Found(
        val thisWeek: ThisWeekDoc,
        val row: MyWeeklyRow,
        val resultText: String,
        val planText: String,
        val resultIsDraft: Boolean,
        val draftLines: List<String>? = null,
        val edited: Boolean = false,
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
    /** '이름 바꾸기' 로 이름 입력 중. 새 이름을 저장하기 전까지 [reportName] 은 그대로 둔다(취소하면 돌아간다). */
    val editingName: Boolean = false,
    val syncing: Boolean = false,
    val lastSync: WeeklySyncInfo? = null,
    val content: WeeklyContent = WeeklyContent.Idle,
    /** 최근 주차 문서(주차 내림차순, 최대 [WeeklyReportViewModel.WEEK_LIST_SIZE]). */
    val weeks: List<WeekDoc> = emptyList(),
    /** itemId → 내 행 상태. 이번 주 문서는 [WeeklyContent.Found] 로 판단한다. */
    val weekStatus: Map<String, WeekRowStatus> = emptyMap(),
    val currentWeek: Int,
    /** itemId → 지난 주차 문서에서 읽은 내 행(상태 확인 · 선택 시 읽은 것). 읽기 전용 보기에 쓴다. */
    val pastRows: Map<String, MyWeeklyRow> = emptyMap(),
    /** 목록에서 고른 지난 주차. null 이면 이번 주 화면. */
    val selectedPast: PastWeekSelection? = null,
)

/** 고른 지난 주차. [row] 를 아직 못 읽었으면 [loading] 중이거나 [error] 가 있다. */
data class PastWeekSelection(
    val doc: WeekDoc,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * 주간 보고 화면 상태. App 수준에서 만들어 화면 전환에도 상태(편집 중 텍스트 포함)를 유지한다.
 * 마지막 동기화 결과는 [cacheStore] 에 보관해 다시 켰을 때 바로 보여 주고, 연결되면 한 번 자동으로 동기화한다(읽기만).
 * 문서에 반영하기는 사용자가 확인한 뒤에만 한다.
 */
@OptIn(FlowPreview::class)
class WeeklyReportViewModel(
    val auth: MsAuth,
    private val repository: WeeklyReportRepository,
    private val draftBuilder: WeeklyDraftBuilder,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val cacheStore: WeeklyCacheStore = WeeklyCacheStore.File,
) {
    private val timeZone = TimeZone.of("Asia/Seoul")
    private val _state = MutableStateFlow(
        WeeklyUiState(reportName = WeeklyReportRepository.loadReportName(), currentWeek = today().isoWeekNumber())
            .restoredFrom(decodeWeeklyCache(runCatching { cacheStore.load() }.getOrNull())),
    )
    val state: StateFlow<WeeklyUiState> = _state.asStateFlow()

    init {
        // 결과 · 편집이 바뀌면 1초 뒤 보관한다(동기화 직후에는 바로 저장한다).
        scope.launch {
            _state.map { it.toCache() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(CACHE_DEBOUNCE_MS)
                .collect { persist(it) }
        }
    }

    private var syncJob: Job? = null
    private var statusJob: Job? = null
    private var pastJob: Job? = null

    /** 앱 시작 시 저장된 계정으로 조용히 다시 연결하고, 연결되고 이름이 있으면 한 번 동기화한다(문서에 쓰지는 않음). */
    fun start() {
        scope.launch {
            if (auth.restore() is MsAuthState.Connected) sync()
        }
    }

    /** 드로워로 화면에 들어올 때: 동기화 중 · 반영 중 · 연결 안 됨 · 이름 없음이면 건너뛴다. */
    fun syncIfIdle() {
        if ((_state.value.content as? WeeklyContent.Found)?.saving == true) return
        sync()
    }

    fun signIn() {
        if (auth.state.value is MsAuthState.Connecting) return
        scope.launch { auth.signIn() }
    }

    fun signOut() {
        syncJob?.cancel()
        statusJob?.cancel()
        pastJob?.cancel()
        scope.launch {
            auth.signOut()
            _state.update { WeeklyUiState(reportName = it.reportName, currentWeek = it.currentWeek) }
        }
    }

    fun saveReportName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (trimmed == _state.value.reportName) {
            _state.update { it.copy(editingName = false) }
            return
        }
        WeeklyReportRepository.saveReportName(trimmed)
        // 이름이 바뀌면 이전 이름으로 읽은 행은 버린다.
        _state.update { WeeklyUiState(reportName = trimmed, currentWeek = it.currentWeek) }
    }

    /** 이름 입력 화면으로 간다. 저장된 이름은 새 이름을 저장할 때까지 지우지 않는다. */
    fun startEditingName() {
        _state.update { it.copy(editingName = true) }
    }

    /** 이름 입력을 그만두고 저장돼 있던 이름으로 돌아간다. 저장된 이름이 없으면 그대로 입력 화면에 둔다. */
    fun cancelEditingName() {
        _state.update { if (it.reportName != null) it.copy(editingName = false) else it }
    }

    fun sync() {
        val name = _state.value.reportName ?: return
        if (_state.value.syncing || auth.state.value !is MsAuthState.Connected) return
        statusJob?.cancel()
        pastJob?.cancel()
        _state.update { it.copy(syncing = true, currentWeek = today().isoWeekNumber(), selectedPast = null) }
        syncJob = scope.launch {
            try {
                val today = today()
                val thisWeek = io { repository.findThisWeekDoc(today) }
                val weeks = io { runCatching { repository.listWeekDocs() }.getOrDefault(emptyList()) }
                    .take(WEEK_LIST_SIZE)
                val loaded = if (thisWeek == null) WeeklyContent.NotFound else loadFound(thisWeek, name)
                _state.update {
                    // 같은 문서 버전에 대해 사용자가 고쳐 둔 텍스트는 덮어쓰지 않는다.
                    val content = if (loaded is WeeklyContent.Found) mergeUnsavedEdits(it.content, loaded) else loaded
                    val ids = weeks.map { w -> w.itemId }.toSet()
                    it.copy(
                        syncing = false,
                        lastSync = WeeklySyncInfo(clock.now(), thisWeek),
                        content = content,
                        weeks = weeks,
                        // 다시 확인할 때까지 이전 상태를 보여 준다.
                        weekStatus = it.weekStatus.filterKeys { id -> id in ids },
                        pastRows = it.pastRows.filterKeys { id -> id in ids },
                        selectedPast = null,
                    )
                }
                persist(_state.value.toCache())
                loadPastStatuses(weeks, thisWeek, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    val previous = it.content
                    it.copy(
                        syncing = false,
                        lastSync = WeeklySyncInfo(clock.now(), it.lastSync?.doc, failed = true),
                        // 보이던 내 행(편집 중 텍스트 포함)은 그대로 두고 실패만 알린다.
                        content = if (previous is WeeklyContent.Found) {
                            previous.copy(saveMessage = SaveMessage("동기화하지 못했어요: ${e.toUserMessage()}", ok = false))
                        } else {
                            WeeklyContent.Failed(e.toUserMessage())
                        },
                    )
                }
            }
        }
    }

    /**
     * 지난 주차를 골라 오른쪽에 내 행을 읽기 전용으로 보여 준다. 상태 확인 때 읽어 둔 행이 있으면 그대로 쓰고,
     * 없으면(아직 확인 전 · 실패) 그 문서만 읽는다. 문서에는 쓰지 않는다.
     */
    fun selectPastWeek(doc: WeekDoc) {
        if (_state.value.selectedPast?.doc?.itemId == doc.itemId && _state.value.selectedPast?.error == null) return
        pastJob?.cancel()
        if (doc.itemId in _state.value.pastRows) {
            _state.update { it.copy(selectedPast = PastWeekSelection(doc)) }
            return
        }
        loadPastRow(doc)
    }

    /** 고른 지난 주차 읽기를 다시 시도한다. */
    fun retryPastWeek() {
        val selected = _state.value.selectedPast ?: return
        if (selected.loading) return
        loadPastRow(selected.doc)
    }

    /** 이번 주 화면으로 돌아간다. */
    fun clearPastWeek() {
        pastJob?.cancel()
        _state.update { it.copy(selectedPast = null) }
    }

    private fun loadPastRow(doc: WeekDoc) {
        val name = _state.value.reportName ?: return
        _state.update { it.copy(selectedPast = PastWeekSelection(doc, loading = true)) }
        pastJob = scope.launch {
            try {
                val row = io { repository.readMyRow(doc, name) }
                _state.update { s ->
                    val status = row.status()
                    s.copy(
                        pastRows = s.pastRows + (doc.itemId to row),
                        weekStatus = if (status != null) s.weekStatus + (doc.itemId to status) else s.weekStatus,
                        selectedPast = if (s.selectedPast?.doc?.itemId == doc.itemId) PastWeekSelection(doc) else s.selectedPast,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    if (s.selectedPast?.doc?.itemId != doc.itemId) return@update s
                    s.copy(selectedPast = PastWeekSelection(doc, error = "문서를 읽지 못했어요: ${e.toUserMessage()}"))
                }
            }
        }
    }

    fun editResult(text: String) = updateFound { it.copy(resultText = text, edited = true, saveMessage = null) }

    fun editPlan(text: String) = updateFound { it.copy(planText = text, edited = true, saveMessage = null) }

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
                            current.copy(
                                drafting = false,
                                resultText = lines.joinToString("\n"),
                                resultIsDraft = true,
                                draftLines = lines,
                                edited = true,
                            )
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
                        edited = false,
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
            draftLines = draftLines,
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
                val status = row.status()
                _state.update {
                    it.copy(
                        pastRows = it.pastRows + (doc.itemId to row),
                        weekStatus = if (status != null) it.weekStatus + (doc.itemId to status) else it.weekStatus,
                    )
                }
            }
        }
    }

    private suspend fun persist(cache: WeeklyCache?) {
        withContext(Dispatchers.Default) {
            runCatching { if (cache == null) cacheStore.clear() else cacheStore.save(encodeWeeklyCache(cache)) }
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
        private const val CACHE_DEBOUNCE_MS = 1_000L
    }
}

/** 내 행 상태. 표에 내 행이 없으면 null('—'). */
private fun MyWeeklyRow.status(): WeekRowStatus? = when {
    !found -> null
    results.isNotEmpty() -> WeekRowStatus.Done
    else -> WeekRowStatus.Empty
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
    return if (local.date == now.toLocalDateTime(kst).date) "오늘 $time" else "${local.month.number}/${local.day} $time"
}

private fun Throwable.toUserMessage(): String = when (this) {
    is MsNotConnectedException -> message ?: "Microsoft 계정을 다시 연결해 주세요"
    else -> message ?: this::class.simpleName ?: "알 수 없는 오류"
}
