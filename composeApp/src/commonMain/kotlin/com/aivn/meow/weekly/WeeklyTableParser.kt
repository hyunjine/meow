package com.aivn.meow.weekly

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** ISO-8601 주차(월요일 시작, 그해 첫 목요일이 든 주가 1주). */
fun LocalDate.isoWeekNumber(): Int {
    val thursday = this.plus(DatePeriod(days = 4 - dayOfWeek.isoDayNumber))
    return (thursday.dayOfYear - 1) / 7 + 1
}

/** 파일명의 `Week40-41`, `Week 40 - 41` 등. */
internal val WEEK_RANGE_REGEX = Regex("""Week\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)

private val WEEK_REGEX = Regex("""Week\s*(\d+)""", RegexOption.IGNORE_CASE)
private val PERIOD_REGEX = Regex("""(\d{1,2})\s*\.\s*(\d{1,2})\s*~\s*(?:(\d{1,2})\s*\.\s*)?(\d{1,2})""")
private val YEAR_REGEX = Regex("""(20\d{2})""")
private val WHITESPACE = Regex("""\s+""")

/** 표에서 내 행 위치와 헤더 해석 결과. */
internal data class WeeklyTableLayout(
    val header: WeeklyHeader,
    val nameCol: Int,
    val resultCol: Int,
    val planCol: Int,
)

internal fun normalizeName(name: String): String = name.replace(WHITESPACE, "")

/**
 * 첫 행을 헤더로 보고 `이름` · `실적` · `계획` 열을 찾는다.
 * 기간의 연도는 제목 · 파일명의 4자리 연도, 없으면 [fallbackYear]. 연말을 넘는 기간(12.28~1.1)은 끝을 다음 해로 본다.
 */
internal fun parseWeeklyLayout(table: DocxTableSnapshot, docName: String, fallbackYear: Int): WeeklyTableLayout {
    val headerRow = table.rows.firstOrNull() ?: throw WeeklyDocFormatException("표가 비어 있어요")
    val headerTexts = headerRow.map { paragraphs -> paragraphs.joinToString(" ").trim() }
    fun col(predicate: (String) -> Boolean, what: String) =
        headerTexts.indexOfFirst(predicate).takeIf { it >= 0 }
            ?: throw WeeklyDocFormatException("표 헤더에서 '$what' 열을 찾지 못했어요")
    val nameCol = col({ normalizeName(it) == "이름" || it.contains("이름") }, "이름")
    val resultCol = col({ it.contains("실적") }, "실적")
    val planCol = col({ it.contains("계획") }, "계획")

    val year = YEAR_REGEX.find(table.title.orEmpty())?.groupValues?.get(1)?.toInt()
        ?: YEAR_REGEX.find(docName)?.groupValues?.get(1)?.toInt()
        ?: fallbackYear
    val result = parseColumn(headerTexts[resultCol], year, previousStart = null)
    val plan = parseColumn(headerTexts[planCol], year, previousStart = result.period?.start)
    return WeeklyTableLayout(WeeklyHeader(table.title, result, plan), nameCol, resultCol, planCol)
}

private fun parseColumn(label: String, year: Int, previousStart: LocalDate?): WeeklyColumn {
    val week = WEEK_REGEX.find(label)?.groupValues?.get(1)?.toIntOrNull()
    val match = PERIOD_REGEX.find(label)
    val period = match?.let { m ->
        runCatching {
            val (sm, sd, em, ed) = m.destructured
            val startMonth = sm.toInt()
            val endMonth = em.toIntOrNull() ?: startMonth
            // 계획 열이 실적보다 앞선 달이면 해가 바뀐 것(12월 실적 → 1월 계획).
            val startYear = if (previousStart != null && startMonth < previousStart.month.number) year + 1 else year
            val start = LocalDate(startYear, startMonth, sd.toInt())
            val endYear = if (endMonth < startMonth) startYear + 1 else startYear
            DateRange(start, LocalDate(endYear, endMonth, ed.toInt()))
        }.getOrNull()
    }
    return WeeklyColumn(
        label = label,
        week = week,
        periodText = match?.value?.replace(WHITESPACE, ""),
        period = period,
    )
}

/** 표에서 이름 셀이 [myName] 인 행 번호(헤더 제외). 없으면 -1. */
internal fun findRowIndex(table: DocxTableSnapshot, nameCol: Int, myName: String): Int {
    val target = normalizeName(myName)
    return table.rows.withIndex().drop(1).firstOrNull { (_, cells) ->
        normalizeName(cells.getOrNull(nameCol).orEmpty().joinToString("")) == target
    }?.index ?: -1
}
