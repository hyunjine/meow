package com.aivn.meow.weekly

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftSummaryTest {
    private fun pr(repo: String, n: Int, title: String, mergedAt: String) =
        DraftPr(repo, n, title, cleanPrTitle(title), "https://example.com/$n", mergedAt)

    private val period = DateRange(LocalDate(2026, 10, 5), LocalDate(2026, 10, 9))

    @Test
    fun parseKeepsOnlyBulletLines() {
        val raw = """
            요약입니다:
            • meow
            - 주간 보고 초안 한글 요약 추가
            * 무시
            • chatsea-web
            - 베타 로그인 이메일 선택 드롭다운 추가
            끝.
        """.trimIndent()
        assertEquals(
            listOf("• meow", "- 주간 보고 초안 한글 요약 추가", "• chatsea-web", "- 베타 로그인 이메일 선택 드롭다운 추가"),
            parseSummaryLines(raw),
        )
    }

    @Test
    fun parseCapsItemLinesAndDropsEmptyRepos() {
        val raw = buildString {
            appendLine("• a")
            (1..4).forEach { appendLine("- a$it") }
            appendLine("• b")
            (1..3).forEach { appendLine("- b$it") }
            appendLine("• c")
            appendLine("- c1")
        }
        val lines = parseSummaryLines(raw)!!
        assertEquals(listOf("• a", "- a1", "- a2", "- a3", "- a4", "• b", "- b1"), lines)
        assertEquals(MAX_DRAFT_ITEMS, lines.count { it.startsWith("-") })
    }

    @Test
    fun parseRejectsInvalidOutput() {
        assertNull(parseSummaryLines(null))
        assertNull(parseSummaryLines("  "))
        assertNull(parseSummaryLines("• meow\n설명만 있음"))
        // 레포 줄 없이 시작하는 세부 줄
        assertNull(parseSummaryLines("- 레포 없음\n• meow\n- 세부"))
    }

    @Test
    fun parseNormalizesSpacing() {
        assertEquals(listOf("• meow", "- 세부"), parseSummaryLines("  •meow  \n   -   세부  "))
    }

    @Test
    fun fallbackKeepsMostRecentFive() {
        val prs = listOf(
            pr("a", 1, "[Feat] One", "2026-10-05T01:00:00Z"),
            pr("b", 2, "Two", "2026-10-05T02:00:00Z"),
            pr("a", 3, "Three", "2026-10-06T01:00:00Z"),
            pr("c", 4, "Four", "2026-10-07T01:00:00Z"),
            pr("b", 5, "Five", "2026-10-08T01:00:00Z"),
            pr("a", 6, "fix: Six (#6)", "2026-10-09T01:00:00Z"),
            pr("c", 7, "Seven", "2026-10-09T02:00:00Z"),
        )
        val lines = fallbackDraftLines(prs.shuffled())
        assertEquals(listOf("• a", "- Three", "- Six", "• c", "- Four", "- Seven", "• b", "- Five"), lines)
        assertEquals(5, lines.count { it.startsWith("-") })
    }

    @Test
    fun promptContainsGroupedTitles() {
        val prompt = buildSummaryPrompt(listOf(pr("meow", 1, "[Feat] Add dropdown", "2026-10-05T01:00:00Z")))
        assertTrue("• meow\n- Add dropdown" in prompt)
        assertTrue("최대 $MAX_DRAFT_ITEMS 줄" in prompt)
    }

    @Test
    fun cacheKeyDependsOnPeriodAndPrs() {
        val prs = listOf(pr("a", 1, "One", "x"), pr("b", 2, "Two", "y"))
        assertEquals(summaryCacheKey(period, prs), summaryCacheKey(period, prs.reversed()))
        assertNotEquals(summaryCacheKey(period, prs), summaryCacheKey(period, prs.take(1)))
        assertNotEquals(
            summaryCacheKey(period, prs),
            summaryCacheKey(period.copy(endInclusive = LocalDate(2026, 10, 10)), prs),
        )
    }
}
