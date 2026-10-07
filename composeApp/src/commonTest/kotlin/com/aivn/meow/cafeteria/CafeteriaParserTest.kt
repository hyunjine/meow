package com.aivn.meow.cafeteria

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CafeteriaParserTest {
    private fun kst(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime(y, m, d, h, min).toInstant(KST).toEpochMilliseconds()

    private fun post(id: Long, title: String, publishedAt: Long, text: String = "", images: Int = 0) = KakaoPost(
        id = id,
        title = title,
        publishedAt = publishedAt,
        contents = listOf(KakaoContent("text", JsonPrimitive(text))),
        media = List(images) { KakaoMedia(type = "image", mediumUrl = "m$it", largeUrl = "l$it", xlargeUrl = "x$it") },
    )

    @Test
    fun parsesLunchTitle() {
        val published = kst(2026, 10, 6, 11, 20)
        assertEquals(LocalDate(2026, 10, 6), parseLunchDate("10월 6일 화요일 중식입니다💕", published))
        assertEquals(LocalDate(2026, 9, 30), parseLunchDate("9월 30일 수요일중식입니다❣️", kst(2026, 9, 30)))
        assertNull(parseLunchDate("10월 2째주 주간메뉴표(대덕점)", published))
    }

    @Test
    fun lunchYearCrossesNewYear() {
        assertEquals(LocalDate(2026, 12, 31), parseLunchDate("12월 31일 목요일 중식입니다", kst(2027, 1, 1, 0, 5)))
        assertEquals(LocalDate(2027, 1, 2), parseLunchDate("1월 2일 중식", kst(2026, 12, 31)))
    }

    @Test
    fun recognizesWeeklyMenuTitles() {
        assertTrue(isWeeklyMenuTitle("10월 2째주 주간메뉴표(대덕점)"))
        assertTrue(isWeeklyMenuTitle("9-5주 10-1주차 주간메뉴표(대덕점)"))
        assertFalse(isWeeklyMenuTitle("10월 6일 화요일 중식입니다💕"))
        assertEquals("10월 2째주", weeklyMenuLabel("10월 2째주 주간메뉴표(대덕점)"))
        assertEquals("9월 3째주", weeklyMenuLabel("9월 3째주 주간메뉴표(대덕점)🌟"))
        assertEquals("9-5주 10-1주차", weeklyMenuLabel("9-5주 10-1주차 주간메뉴표(대덕점)"))
    }

    @Test
    fun picksWeeklyPostForWeek() {
        val posts = listOf(
            post(1, "9월 4째주 주간메뉴표(대덕점)", kst(2026, 9, 18, 16, 7)),
            post(2, "9-5주 10-1주차 주간메뉴표(대덕점)", kst(2026, 9, 23, 13, 0)),
            post(3, "10월 2째주 주간메뉴표(대덕점)", kst(2026, 10, 2, 16, 59)),
            post(4, "10월 2일 금요일 중식입니다💫", kst(2026, 10, 2, 11, 20)),
        )
        assertEquals(1, pickWeeklyMenuPost(posts, LocalDate(2026, 9, 21))?.id)
        assertEquals(2, pickWeeklyMenuPost(posts, LocalDate(2026, 9, 28))?.id)
        assertEquals(3, pickWeeklyMenuPost(posts, LocalDate(2026, 10, 5))?.id)
        assertNull(pickWeeklyMenuPost(posts, LocalDate(2026, 10, 19)))
        // 앞 주에 안 올라왔으면 그 주 중에 올라온 것을 쓴다.
        val late = listOf(post(5, "10월 4째주 주간메뉴표", kst(2026, 10, 20, 9, 0)))
        assertEquals(5, pickWeeklyMenuPost(late, LocalDate(2026, 10, 19))?.id)
    }

    @Test
    fun parsesHolidaysWithNames() {
        val text = "안녕하세요! \nkt대덕2연구센터 구내식당 영양사입니다〰️☘️\n10-2주차 주간메뉴표 등재합니다🤤\n" +
            "다음주는 10월5일 9일은 각각 개천절,한글날 휴일로인해\n구내식당운영이 없음을 알려드려요🥲\n화수목은 정상운영 됩니다🫡"
        val holidays = parseHolidays(text, LocalDate(2026, 10, 5))
        assertEquals(mapOf(LocalDate(2026, 10, 5) to "개천절", LocalDate(2026, 10, 9) to "한글날"), holidays)
    }

    @Test
    fun parsesHolidayRangeAndExcludesNormalDays() {
        val text = "차주 23일 수요일 석식부터 25일 금요일까지는\n추석연휴로 인해 식당운영이 없음을 알려드립니다🌟\n" +
            "23일 수요일 중식은 정상운영 합니다♥️"
        val holidays = parseHolidays(text, LocalDate(2026, 9, 21))
        assertEquals(mapOf(LocalDate(2026, 9, 24) to "추석연휴", LocalDate(2026, 9, 25) to "추석연휴"), holidays)
    }

    @Test
    fun noHolidaysInOrdinaryText() {
        val text = "즐거운 금요일과 9월 3주차 주간메뉴표 등재합니다🤤\n다음주도 구내식당에서 즐겁게 만나요🌺"
        assertTrue(parseHolidays(text, LocalDate(2026, 9, 14)).isEmpty())
    }

    @Test
    fun condensesMenuText() {
        val text = "오늘의 메뉴는 청양풍뼈있는 간장찜닭🌿\n얼큰한 오징어고추장찌개입니다💫\n영양만점 수제칠리버섯탕수🤤\n" +
            "챔기름솔솔간장비빔국수입니다🩷\n휴일 다음날음 구내식당이 국룰❣️\n한주도 구내식당에서 맛있게 보내세요💚"
        assertEquals(
            "청양풍뼈있는 간장찜닭 · 얼큰한 오징어고추장찌개 · 영양만점 수제칠리버섯탕수 · 챔기름솔솔간장비빔국수",
            condenseMenuText(text),
        )
        val text2 = "오늘의 메뉴는 \n감칠맛듬뿍 씨푸드알리오올리오❣️\n콘크림소스에 꼭 찍어먹는 수제함바그🤤입니다\n" +
            "오늘도 맛있는 구내식당에서 즐거운 하루보내세요😆"
        assertEquals("감칠맛듬뿍 씨푸드알리오올리오 · 콘크림소스에 꼭 찍어먹는 수제함바그", condenseMenuText(text2))
        val text3 = "오늘의 메뉴는\n등촌샤브칼국수*달걀미나리볶음밥 조화🥰\n(오늘 등촌칼국수 육수와 너무 비슷하게 맛있어요🌟)\n" +
            "또한 도톰야채멘치카츠❣️\n오늘의 치트키!!!\n감칠맛 듬뿍 수제냉소바만두입니다😆"
        assertEquals("등촌샤브칼국수*달걀미나리볶음밥 조화 · 도톰야채멘치카츠 · 감칠맛 듬뿍 수제냉소바만두", condenseMenuText(text3))
    }

    @Test
    fun condenseKeepsMenuWordsAndDropsChatter() {
        val text = "오늘의 메뉴는\n고추장소스로볶은~제육볶음❣️\n영양사픽수제당면달걀만두😍\n양배추쌈*우렁강된장입니다\n" +
            "월요일은 구내식당밥이 국룰🌿\n밖에 날씨 폭염으로 장난 아니예요\n무더운 여름 날리러 구내식당으로 와주세요"
        assertEquals("고추장소스로볶은~제육볶음 · 영양사픽수제당면달걀만두 · 양배추쌈*우렁강된장", condenseMenuText(text))
    }

    @Test
    fun condenseFallsBackToFirstLines() {
        assertEquals("오늘도 좋은 하루 보내세요", condenseMenuText("오늘도 좋은 하루\n보내세요🌿\n구내식당이 국룰"))
    }

    @Test
    fun buildsWeek() {
        val monday = LocalDate(2026, 10, 5)
        val posts = listOf(
            post(
                1, "10월 2째주 주간메뉴표(대덕점)", kst(2026, 10, 2, 16, 59),
                text = "다음주는 10월5일 9일은 각각 개천절,한글날 휴일로인해\n구내식당운영이 없음을 알려드려요", images = 1,
            ),
            post(2, "10월 6일 화요일 중식입니다💕", kst(2026, 10, 6, 11, 20), text = "오늘의 메뉴는 찜닭", images = 3),
        )
        val week = buildWeek(posts, monday)
        assertEquals("10월 2째주", week.weeklyMenu?.label)
        assertEquals(listOf(true, false, false, false, true), week.days.map { it.holiday })
        assertEquals("개천절", week.days[0].holidayName)
        assertEquals(3, week.days[1].lunch?.photos?.size)
        assertEquals("찜닭", week.days[1].lunch?.menu)
        assertNull(week.days[2].lunch)
    }
}
