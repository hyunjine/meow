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

    private fun item(
        repo: String,
        n: Int,
        title: String,
        updatedAt: String,
        labels: List<String> = emptyList(),
        milestone: String? = null,
        pr: Boolean = false,
    ) = DraftPlanItem(repo, n, title, cleanPrTitle(title), "https://example.com/$n", updatedAt, labels, milestone, pr)

    @Test
    fun planFallbackKeepsMostRecentlyUpdatedFive() {
        val items = listOf(
            item("a", 1, "[Feat] One", "2026-10-01T01:00:00Z"),
            item("b", 2, "Two", "2026-10-02T01:00:00Z"),
            item("a", 3, "Three", "2026-10-06T01:00:00Z"),
            item("c", 4, "Four", "2026-10-07T01:00:00Z"),
            item("b", 5, "Five", "2026-10-08T01:00:00Z", pr = true),
            item("a", 6, "fix: Six (#6)", "2026-10-08T02:00:00Z"),
            item("c", 7, "Seven", "2026-10-08T03:00:00Z"),
        )
        val lines = fallbackPlanLines(items.shuffled())
        assertEquals(listOf("• c", "- Seven", "- Four", "• a", "- Six", "- Three", "• b", "- Five"), lines)
        assertEquals(MAX_DRAFT_ITEMS, lines.count { it.startsWith("-") })
        // 같은 형식이라 실적과 같은 파서를 통과한다.
        assertEquals(lines, parseSummaryLines(lines.joinToString("\n")))
    }

    @Test
    fun planFallbackEmptyWhenNoItems() {
        assertTrue(fallbackPlanLines(emptyList()).isEmpty())
    }

    @Test
    fun planPromptGroupsByRecencyWithLabelsAndPrMarker() {
        val prompt = buildPlanPrompt(
            listOf(
                item("meow", 1, "[#1] feat: 주간 보고 계획 초안", "2026-10-07T01:00:00Z", labels = listOf("💻 Feat"), milestone = "v1.2.0"),
                item("chatsea-web", 2, "[Fix] 로그인 오류 (#2)", "2026-10-08T01:00:00Z", labels = listOf("🐞 Fix")),
                item("meow", 3, "드로워 정리", "2026-10-08T02:00:00Z", pr = true),
            ),
        )
        assertTrue("• meow\n- 드로워 정리 (진행 중 PR)\n- 주간 보고 계획 초안 [💻 Feat] (마일스톤: v1.2.0)" in prompt, prompt)
        assertTrue("• chatsea-web\n- 로그인 오류 [🐞 Fix]" in prompt, prompt)
        // 최근 갱신 레포가 먼저.
        assertTrue(prompt.indexOf("• meow") < prompt.indexOf("• chatsea-web"))
        assertTrue("최대 $MAX_DRAFT_ITEMS 줄" in prompt)
        assertTrue("다음 주" in prompt)
        assertTrue("머지한 PR" !in prompt)
    }

    @Test
    fun planPromptCapsInputItems() {
        val items = (1..40).map { item("r", it, "T$it", "2026-10-08T00:00:" + it.toString().padStart(3, '0')) }
        val prompt = buildPlanPrompt(items)
        assertEquals(MAX_PLAN_INPUT_ITEMS, prompt.lines().count { it.startsWith("- T") })
    }

    @Test
    fun planCacheKeyDependsOnItems() {
        val items = listOf(item("a", 1, "One", "x"), item("b", 2, "Two", "y", pr = true))
        assertEquals(planCacheKey(period, items), planCacheKey(period, items.reversed()))
        assertNotEquals(planCacheKey(period, items), planCacheKey(period, items.take(1)))
        assertNotEquals(planCacheKey(period, items), planCacheKey(period, listOf(items[0].copy(labels = listOf("Fix")), items[1])))
    }
}
