package com.aivn.meow.ui.cafeteria

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDayIndexTest {
    private val monday = LocalDate(2026, 10, 5)

    @Test
    fun todayInShownWeekIsSelected() {
        assertEquals(0, defaultDayIndex(monday, LocalDate(2026, 10, 5)))
        assertEquals(2, defaultDayIndex(monday, LocalDate(2026, 10, 7)))
        assertEquals(4, defaultDayIndex(monday, LocalDate(2026, 10, 9)))
    }

    @Test
    fun otherWeeksOrWeekendFallBackToMonday() {
        assertEquals(0, defaultDayIndex(monday, LocalDate(2026, 10, 10)))
        assertEquals(0, defaultDayIndex(monday, LocalDate(2026, 10, 12)))
        assertEquals(0, defaultDayIndex(monday, LocalDate(2026, 10, 2)))
    }
}
