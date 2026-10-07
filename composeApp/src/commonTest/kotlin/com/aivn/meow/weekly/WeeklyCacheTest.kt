package com.aivn.meow.weekly

import com.aivn.meow.ui.weekly.WeekRowStatus
import com.aivn.meow.ui.weekly.WeeklyCache
import com.aivn.meow.ui.weekly.WeeklyContent
import com.aivn.meow.ui.weekly.WeeklySyncInfo
import com.aivn.meow.ui.weekly.WeeklyUiState
import com.aivn.meow.ui.weekly.decodeWeeklyCache
import com.aivn.meow.ui.weekly.encodeWeeklyCache
import com.aivn.meow.ui.weekly.mergeUnsavedEdits
import com.aivn.meow.ui.weekly.restoredFrom
import com.aivn.meow.ui.weekly.toCache
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyCacheTest {
    private val doc = WeekDoc(
        week = 41, nextWeek = 42, driveId = "d", itemId = "item-41", name = "Week41-42.docx",
        createdAt = Instant.parse("2026-10-05T00:00:00Z"), modifiedAt = null, webUrl = "https://example.com/41",
    )
    private val pastDoc = doc.copy(week = 40, nextWeek = 41, itemId = "item-40", name = "Week40-41.docx")
    private val header = WeeklyHeader(
        title = "주간업무 (Week 41-42)",
        result = WeeklyColumn("실적(Week 41)", 41, "10.5~10.9", DateRange(LocalDate(2026, 10, 5), LocalDate(2026, 10, 9))),
        plan = WeeklyColumn("계획(Week 42)", 42, null, null),
    )
    private val thisWeek = ThisWeekDoc(doc, WeekDocSource.Mail, Instant.parse("2026-10-05T01:00:00Z"))

    private fun row(results: List<String> = emptyList(), eTag: String = "v1", d: WeekDoc = doc) =
        MyWeeklyRow(d, header, "양현진", found = true, results = results, plans = listOf("• 계획"), eTag = eTag)

    private fun found(
        row: MyWeeklyRow = row(),
        resultText: String = "• 초안",
        edited: Boolean = false,
    ) = WeeklyContent.Found(
        thisWeek = thisWeek,
        row = row,
        resultText = resultText,
        planText = row.plans.joinToString("\n"),
        resultIsDraft = row.results.isEmpty(),
        draftLines = listOf("• 초안"),
        eTag = row.eTag,
        edited = edited,
    )

    private fun state(content: WeeklyContent = found(resultText = "• 내가 고침", edited = true)) = WeeklyUiState(
        reportName = "양현진",
        lastSync = WeeklySyncInfo(Instant.parse("2026-10-07T02:30:00Z"), thisWeek),
        content = content,
        weeks = listOf(doc, pastDoc),
        weekStatus = mapOf(pastDoc.itemId to WeekRowStatus.Done),
        currentWeek = 41,
        pastRows = mapOf(pastDoc.itemId to row(results = listOf("• 지난 실적"), d = pastDoc)),
    )

    @Test
    fun roundTripRestoresState() {
        val original = state()
        val cache = original.toCache()!!
        val decoded = decodeWeeklyCache(encodeWeeklyCache(cache))
        assertEquals(cache, decoded)
        assertTrue(decoded!!.found!!.edited)
        assertTrue(decoded.found!!.differsFromDoc)

        val restored = WeeklyUiState(reportName = "양현진", currentWeek = 42).restoredFrom(decoded)
        assertEquals(original.lastSync, restored.lastSync)
        assertEquals(original.content, restored.content)
        assertEquals(original.weeks, restored.weeks)
        assertEquals(original.weekStatus, restored.weekStatus)
        assertEquals(original.pastRows, restored.pastRows)
        assertEquals(41, restored.currentWeek)
    }

    @Test
    fun transientFlagsAreNotRestored() {
        val busy = (state().content as WeeklyContent.Found).copy(saving = true, drafting = true)
        val restored = WeeklyUiState(reportName = "양현진", currentWeek = 41).restoredFrom(state(busy).toCache())
        val content = restored.content as WeeklyContent.Found
        assertFalse(content.saving)
        assertFalse(content.drafting)
        assertFalse(restored.syncing)
    }

    @Test
    fun notFoundRoundTrip() {
        val s = state(WeeklyContent.NotFound).copy(lastSync = WeeklySyncInfo(Instant.parse("2026-10-07T02:30:00Z"), null))
        val restored = WeeklyUiState(reportName = "양현진", currentWeek = 41)
            .restoredFrom(decodeWeeklyCache(encodeWeeklyCache(s.toCache()!!)))
        assertEquals(WeeklyContent.NotFound, restored.content)
        assertNull(restored.lastSync!!.doc)
    }

    @Test
    fun nothingToCacheBeforeFirstSync() {
        assertNull(WeeklyUiState(reportName = "양현진", currentWeek = 41).toCache())
        assertNull(state().copy(reportName = null).toCache())
    }

    @Test
    fun ignoresCorruptOrIncompatibleCache() {
        assertNull(decodeWeeklyCache(null))
        assertNull(decodeWeeklyCache(""))
        assertNull(decodeWeeklyCache("{not json"))
        assertNull(decodeWeeklyCache("""{"version":1}"""))
        val json = encodeWeeklyCache(state().toCache()!!)
        assertNull(decodeWeeklyCache(json.replace("\"version\":${WeeklyCache.VERSION}", "\"version\":999")))
    }

    @Test
    fun ignoresCacheForDifferentName() {
        val base = WeeklyUiState(reportName = "다른 사람", currentWeek = 41)
        assertEquals(base, base.restoredFrom(state().toCache()))
    }

    @Test
    fun keepsEditsWhenDocVersionIsSame() {
        val previous = found(resultText = "• 내가 고침", edited = true)
        val fresh = found()
        val merged = mergeUnsavedEdits(previous, fresh)
        assertEquals("• 내가 고침", merged.resultText)
        assertTrue(merged.edited)
    }

    @Test
    fun keepsEditsWhenOnlyOtherRowsChanged() {
        val previous = found(resultText = "• 내가 고침", edited = true)
        val fresh = found(row = row(eTag = "v2"))
        val merged = mergeUnsavedEdits(previous, fresh)
        assertEquals("• 내가 고침", merged.resultText)
        assertEquals("v2", merged.eTag)
    }

    @Test
    fun replacesEditsWhenMyCellsChangedInNewVersion() {
        val previous = found(resultText = "• 내가 고침", edited = true)
        val fresh = found(row = row(results = listOf("• 다른 곳에서 씀"), eTag = "v2"), resultText = "• 다른 곳에서 씀")
        assertEquals(fresh, mergeUnsavedEdits(previous, fresh))
    }

    @Test
    fun replacesWhenDifferentDocOrNotEdited() {
        val fresh = found()
        val otherDoc = found(resultText = "• 지난주 편집", edited = true).copy(thisWeek = thisWeek.copy(doc = pastDoc))
        assertEquals(fresh, mergeUnsavedEdits(otherDoc, fresh))
        assertEquals(fresh, mergeUnsavedEdits(found(resultText = "• 손대지 않음"), fresh))
        assertEquals(fresh, mergeUnsavedEdits(WeeklyContent.NotFound, fresh))
        assertEquals(fresh, mergeUnsavedEdits(null, fresh))
    }
}
