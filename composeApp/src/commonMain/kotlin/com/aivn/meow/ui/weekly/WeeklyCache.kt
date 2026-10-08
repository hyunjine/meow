package com.aivn.meow.ui.weekly

import com.aivn.meow.config.deleteConfigFile
import com.aivn.meow.config.readConfigFile
import com.aivn.meow.config.writeConfigFile
import com.aivn.meow.weekly.MyWeeklyRow
import com.aivn.meow.weekly.ThisWeekDoc
import com.aivn.meow.weekly.WeekDoc
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 주간 보고 마지막 동기화 결과(`~/.config/meow/weekly_cache.json`). 다시 켰을 때 바로 보여 주고 뒤에서 새로 동기화한다.
 * 읽기 결과와 사용자가 아직 반영하지 않은 편집만 담는다. 형식이 바뀌면 [version] 을 올려 옛 파일은 버린다.
 */
@Serializable
data class WeeklyCache(
    val version: Int = VERSION,
    /** 이 결과를 읽을 때 쓴 표 이름. 지금 이름과 다르면 쓰지 않는다. */
    val reportName: String,
    val lastSyncAt: Instant,
    val lastSyncFailed: Boolean = false,
    /** 마지막으로 찾은 이번 주 문서(PageHeader 표시용). */
    val lastSyncDoc: ThisWeekDoc? = null,
    val currentWeek: Int,
    val weeks: List<WeekDoc> = emptyList(),
    /** 이번 주 문서를 못 찾았으면 true(이때 [found] 는 null). */
    val notFound: Boolean = false,
    val found: CachedFound? = null,
    val weekStatus: Map<String, WeekRowStatus> = emptyMap(),
    val pastRows: Map<String, MyWeeklyRow> = emptyMap(),
) {
    companion object {
        const val VERSION = 1
    }
}

/** 이번 주 문서의 내 행과 편집 중인 텍스트. [edited] 면 사용자가 손댄 뒤 아직 문서에 반영하지 않았다. */
@Serializable
data class CachedFound(
    val thisWeek: ThisWeekDoc,
    val row: MyWeeklyRow,
    val resultText: String,
    val planText: String,
    val resultIsDraft: Boolean,
    val eTag: String? = null,
    val draftLines: List<String>? = null,
    val edited: Boolean = false,
    /** 편집 텍스트가 문서의 내 칸과 다른지(참고용). */
    val differsFromDoc: Boolean = false,
    /** 계획 칸을 초안으로 채운 상태. 이 필드가 없던 옛 파일은 false 로 읽는다. */
    val planIsDraft: Boolean = false,
    /** 동기화 때 만든 계획 초안. 이 필드가 없던 옛 파일은 null 로 읽는다. */
    val planDraftLines: List<String>? = null,
)

internal val weeklyCacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun encodeWeeklyCache(cache: WeeklyCache): String = weeklyCacheJson.encodeToString(WeeklyCache.serializer(), cache)

/** 깨졌거나 버전이 다르면 null. */
internal fun decodeWeeklyCache(text: String?): WeeklyCache? {
    if (text.isNullOrBlank()) return null
    val cache = runCatching { weeklyCacheJson.decodeFromString(WeeklyCache.serializer(), text) }.getOrNull() ?: return null
    return cache.takeIf { it.version == WeeklyCache.VERSION }
}

/** 화면 상태 → 보관할 결과. 아직 동기화한 적이 없으면 null(보관할 것 없음). 진행 중 표시 · 메시지는 담지 않는다. */
internal fun WeeklyUiState.toCache(): WeeklyCache? {
    val name = reportName ?: return null
    val sync = lastSync ?: return null
    val found = content as? WeeklyContent.Found
    // 실패 화면이면 직전에 보이던 결과가 없으니 상태만 남긴다.
    return WeeklyCache(
        reportName = name,
        lastSyncAt = sync.at,
        lastSyncFailed = sync.failed,
        lastSyncDoc = sync.doc,
        currentWeek = currentWeek,
        weeks = weeks,
        notFound = content is WeeklyContent.NotFound,
        found = found?.let {
            CachedFound(
                thisWeek = it.thisWeek,
                row = it.row,
                resultText = it.resultText,
                planText = it.planText,
                resultIsDraft = it.resultIsDraft,
                eTag = it.eTag,
                draftLines = it.draftLines,
                edited = it.edited,
                differsFromDoc = it.differsFromDoc(),
                planIsDraft = it.planIsDraft,
                planDraftLines = it.planDraftLines,
            )
        },
        weekStatus = weekStatus,
        pastRows = pastRows,
    )
}

/** 보관한 결과로 시작 상태를 만든다. 없거나 이름이 다르면 그대로 둔다. */
internal fun WeeklyUiState.restoredFrom(cache: WeeklyCache?): WeeklyUiState {
    if (cache == null || cache.reportName != reportName) return this
    val content = when {
        cache.found != null -> cache.found.let {
            WeeklyContent.Found(
                thisWeek = it.thisWeek,
                row = it.row,
                resultText = it.resultText,
                planText = it.planText,
                resultIsDraft = it.resultIsDraft,
                draftLines = it.draftLines,
                eTag = it.eTag,
                edited = it.edited,
                planIsDraft = it.planIsDraft,
                planDraftLines = it.planDraftLines,
            )
        }
        cache.notFound -> WeeklyContent.NotFound
        else -> WeeklyContent.Idle
    }
    return copy(
        lastSync = WeeklySyncInfo(cache.lastSyncAt, cache.lastSyncDoc, failed = cache.lastSyncFailed),
        content = content,
        weeks = cache.weeks,
        weekStatus = cache.weekStatus,
        currentWeek = cache.currentWeek,
        pastRows = cache.pastRows,
    )
}

/** 편집 텍스트가 문서의 내 칸과 다른지. */
internal fun WeeklyContent.Found.differsFromDoc(): Boolean =
    resultText.toCellLines() != row.results || planText.toCellLines() != row.plans

/**
 * 새로 읽은 [fresh] 에 [previous] 의 아직 반영하지 않은 편집을 옮긴다. 같은 문서(itemId)이고 편집한 뒤 문서가
 * 바뀌지 않았을 때(같은 eTag, 또는 다른 사람 행만 바뀌어 내 칸이 그대로)만 옮긴다. 아니면 [fresh] 그대로.
 */
internal fun mergeUnsavedEdits(previous: WeeklyContent?, fresh: WeeklyContent.Found): WeeklyContent.Found {
    val prev = previous as? WeeklyContent.Found ?: return fresh
    if (!prev.edited) return fresh
    if (prev.thisWeek.doc.itemId != fresh.thisWeek.doc.itemId) return fresh
    val sameVersion = prev.eTag != null && prev.eTag == fresh.eTag
    val myCellsUnchanged = prev.row.found == fresh.row.found &&
        prev.row.results == fresh.row.results &&
        prev.row.plans == fresh.row.plans
    if (!sameVersion && !myCellsUnchanged) return fresh
    return fresh.copy(
        resultText = prev.resultText,
        planText = prev.planText,
        resultIsDraft = prev.resultIsDraft,
        planIsDraft = prev.planIsDraft,
        edited = true,
    )
}

/** 보관 파일 읽기 · 쓰기. 테스트에서 바꿔 끼울 수 있게 둔다. */
interface WeeklyCacheStore {
    fun load(): String?
    fun save(text: String)
    fun clear()

    /** `~/.config/meow/weekly_cache.json`. */
    object File : WeeklyCacheStore {
        private const val NAME = "weekly_cache.json"
        override fun load(): String? = readConfigFile(NAME)
        override fun save(text: String) = writeConfigFile(NAME, text)
        override fun clear() = deleteConfigFile(NAME)
    }
}
