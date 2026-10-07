package com.aivn.meow.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildWeekTest {
    private val monday = LocalDate(2026, 9, 28)

    private fun allDay(subject: String, start: String, endExclusive: String) = CalendarEvent(
        subject = subject,
        start = LocalDateTime.parse("${start}T00:00:00"),
        end = LocalDateTime.parse("${endExclusive}T00:00:00"),
        isAllDay = true,
    )

    @Test
    fun allDayEndIsExclusive() {
        val week = buildWeek(monday, listOf(allDay("휴가(김다혜)", "2026-09-29", "2026-10-01")))
        val days = week.days.filterValues { it.isNotEmpty() }.keys
        assertEquals(setOf(LocalDate(2026, 9, 29), LocalDate(2026, 9, 30)), days)
    }

    @Test
    fun multiDaySpanningWeekendIsClippedToWeekdays() {
        val week = buildWeek(monday, listOf(allDay("출장(황상환)", "2026-09-24", "2026-10-06")))
        assertEquals(5, week.days.count { it.value.isNotEmpty() })
    }

    @Test
    fun timedMultiDayExpands() {
        val event = CalendarEvent(
            subject = "교육(정진기)",
            start = LocalDateTime.parse("2026-10-01T09:00:00"),
            end = LocalDateTime.parse("2026-10-02T18:00:00"),
            isAllDay = false,
        )
        val days = buildWeek(monday, listOf(event)).days.filterValues { it.isNotEmpty() }.keys
        assertEquals(setOf(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2)), days)
    }

    @Test
    fun dedupesAndSortsByKindThenName() {
        val week = buildWeek(
            monday,
            listOf(
                allDay("출장(황상환)", "2026-09-28", "2026-09-29"),
                allDay("휴가(이창윤)", "2026-09-28", "2026-09-29"),
                allDay("휴가(김다혜)", "2026-09-28", "2026-09-29"),
                allDay("휴가(김다혜)", "2026-09-28", "2026-09-29"),
            ),
        )
        val entries = week.days.getValue(monday)
        assertEquals(listOf("김다혜", "이창윤", "황상환"), entries.map { it.name })
    }
}
