package com.aivn.meow.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbsenceParserTest {
    private fun parse(subject: String) = AbsenceParser.parse(subject)

    @Test
    fun vacation() {
        assertEquals(listOf(ParsedAbsence(AbsenceKind.Vacation, "김다혜")), parse("휴가(김다혜)"))
        assertEquals(listOf(ParsedAbsence(AbsenceKind.Vacation, "김다혜")), parse("연차(김다혜)"))
    }

    @Test
    fun afternoonHalfDay() {
        assertEquals(listOf(ParsedAbsence(AbsenceKind.HalfDay, "황상환", "오후")), parse("오후반차(황상환)"))
    }

    @Test
    fun remoteWithInlineHalfDay() {
        val result = parse("재택(김연우) 07:30-11:30, 오후반차 13:00-17:00")
        assertEquals(listOf(ParsedAbsence(AbsenceKind.HalfDay, "김연우", "오후 · 13:00–17:00")), result)
    }

    @Test
    fun tripWithTwoPeopleAndNote() {
        val result = parse("출장(황상환, 이창윤 래블업 AI컨퍼런스)")
        assertEquals(
            listOf(
                ParsedAbsence(AbsenceKind.Trip, "황상환", "래블업 AI컨퍼런스"),
                ParsedAbsence(AbsenceKind.Trip, "이창윤", "래블업 AI컨퍼런스"),
            ),
            result,
        )
    }

    @Test
    fun tripWithGluedNote() {
        assertEquals(listOf(ParsedAbsence(AbsenceKind.Trip, "정진기", "래블업AI컨퍼런스")), parse("출장(정진기, 래블업AI컨퍼런스)"))
    }

    @Test
    fun checkup() {
        assertEquals(listOf(ParsedAbsence(AbsenceKind.Checkup, "정진기")), parse("건강검진(정진기)"))
    }

    @Test
    fun excludedKinds() {
        assertTrue(parse("사무실(김연우)").isEmpty())
        assertTrue(parse("재택(김연우) 07:30-16:30").isEmpty())
    }

    @Test
    fun otherKindsKeepOwnLabel() {
        assertEquals(AbsenceKind.Sick, parse("병가(김다혜)").single().kind)
        assertEquals(AbsenceKind.Family, parse("경조사(김다혜)").single().kind)
        assertEquals(AbsenceKind.Education, parse("교육(김다혜)").single().kind)
        assertEquals(AbsenceKind.Field, parse("외근(김다혜)").single().kind)
    }

    @Test
    fun unmatchedSubjectIsOther() {
        assertEquals(listOf(ParsedAbsence(AbsenceKind.Other, null, title = "Anydesk 만료일")), parse("Anydesk 만료일"))
    }

    @Test
    fun trailingTimeBecomesNote() {
        assertEquals(listOf(ParsedAbsence(AbsenceKind.Field, "김다혜", "14:00–18:00")), parse("외근(김다혜) 14:00-18:00"))
    }
}
