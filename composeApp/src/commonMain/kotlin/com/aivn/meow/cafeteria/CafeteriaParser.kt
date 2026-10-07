package com.aivn.meow.cafeteria

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

val KST: TimeZone = TimeZone.of("Asia/Seoul")

private val LUNCH_TITLE = Regex("""(\d{1,2})월\s*(\d{1,2})일.*중식""")
private val WEEKLY_LABEL = Regex("""(\d{1,2})월\s*(\d)\s*째\s*주""")
private val DATE_TOKEN = Regex("""(?:(\d{1,2})월\s*)?(\d{1,2})일""")
private val HOLIDAY_NAME = Regex("""[가-힣]+?(?:절|날|연휴)""")
private val PARENTHESES = Regex("""\([^)]*\)""")
private val MENU_PREFIX = Regex("""^오늘의\s*메뉴는\s*""")
private val MENU_SUFFIX = Regex("""\s*입니다\s*$""")

/** 메뉴가 아닌 인사 · 홍보 줄. */
private val NON_MENU_LINE = Regex("""보내|국룰|이죠|드시러|주세요|오세요|하루|오늘은|오늘이|치트키|예요|어요|아요|안녕하세요|영양사입니다""")

fun Long.toKstDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(KST).date

fun isWeeklyMenuTitle(title: String): Boolean = "주간메뉴표" in title.replace(" ", "")

/** "10월 6일 화요일 중식입니다💕" → 2026-10-06. 연도는 게시 시각(KST)에서, 연말 · 연초 경계는 보정한다. */
fun parseLunchDate(title: String, publishedAt: Long): LocalDate? {
    val match = LUNCH_TITLE.find(title) ?: return null
    val month = match.groupValues[1].toInt()
    val day = match.groupValues[2].toInt()
    val published = publishedAt.toKstDate()
    val year = when {
        month - published.monthNumber > 6 -> published.year - 1
        published.monthNumber - month > 6 -> published.year + 1
        else -> published.year
    }
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

/** "10월 2째주 주간메뉴표(대덕점)" → "10월 2째주", 그 밖에는 '주간메뉴표' 앞부분 (없으면 null). */
fun weeklyMenuLabel(title: String): String? {
    WEEKLY_LABEL.find(title)?.let { return "${it.groupValues[1]}월 ${it.groupValues[2]}째주" }
    val idx = title.indexOf("주간메뉴표")
    if (idx <= 0) return null
    return stripEmoji(title.substring(0, idx)).trim().ifEmpty { null }
}

/**
 * 월요일 [monday] 인 주의 주간 메뉴표. 게시일이 [M-9일, M) 인 것 중 가장 최근을 고르고,
 * 없으면 그 주 [M, M+5일) 에 늦게 올라온 것 중 가장 이른 것을 쓴다
 * (주중에 다음 주 메뉴표가 미리 올라오는 경우가 있어, 주중 게시물을 앞 주 것보다 우선하지 않는다).
 */
fun pickWeeklyMenuPost(posts: List<KakaoPost>, monday: LocalDate): KakaoPost? {
    val from = monday.minus(9, DateTimeUnit.DAY)
    val until = monday.plus(5, DateTimeUnit.DAY)
    val candidates = posts.filter { isWeeklyMenuTitle(it.title) }
        .filter { val d = it.publishedAt.toKstDate(); d >= from && d < until }
    return candidates.filter { it.publishedAt.toKstDate() < monday }.maxByOrNull { it.publishedAt }
        ?: candidates.minByOrNull { it.publishedAt }
}

/**
 * 메뉴표 게시글에서 휴무일을 찾는다(최선 노력). '운영' 과 '없'/'휴무' 가 든 줄(날짜가 없으면 앞 줄과 합쳐)의
 * 날짜를 그 주 월~금에 맞추고, '정상운영' 줄에 나온 날짜는 뺀다. 값은 휴일 이름(모르면 null).
 */
fun parseHolidays(text: String, monday: LocalDate): Map<LocalDate, String?> {
    val week = (0..4).map { monday.plus(it, DateTimeUnit.DAY) }
    val lines = stripEmoji(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
    val result = linkedMapOf<LocalDate, String?>()
    lines.forEachIndexed { i, line ->
        if ("운영" !in line || ("없" !in line && "휴무" !in line)) return@forEachIndexed
        val candidate = if (DATE_TOKEN.containsMatchIn(line) || i == 0) line else "${lines[i - 1]} $line"
        val days = resolveDays(candidate, week)
        if (days.isEmpty()) return@forEachIndexed
        val names = HOLIDAY_NAME.findAll(candidate).map { it.value }.filter { it != "다음날" }.toList()
        days.forEachIndexed { idx, day ->
            result[day] = when {
                names.size == days.size -> names[idx]
                names.size == 1 -> names[0]
                else -> null
            }
        }
    }
    lines.filter { "정상" in it && "운영" in it }.forEach { line -> resolveDays(line, week).forEach(result::remove) }
    return result
}

/** 문장 속 `M월 D일` · `D일` 을 [week] 의 날짜로 맞춘다. '부터 … 까지' 면 두 날짜 사이를 채운다. */
private fun resolveDays(text: String, week: List<LocalDate>): List<LocalDate> {
    var month: Int? = null
    val days = DATE_TOKEN.findAll(text).mapNotNull { m ->
        m.groupValues[1].takeIf { it.isNotEmpty() }?.let { month = it.toInt() }
        val day = m.groupValues[2].toInt()
        week.firstOrNull { it.dayOfMonth == day && (month == null || it.monthNumber == month) }
    }.distinct().toList()
    if (days.size == 2 && "부터" in text && "까지" in text) {
        val (a, b) = days.sorted()
        return week.filter { it in a..b }
    }
    return days
}

/** 중식 게시글 본문 → "청양풍뼈있는 간장찜닭 · 얼큰한 오징어고추장찌개 · …". 남는 게 없으면 앞 두 줄. */
fun condenseMenuText(text: String): String {
    val lines = stripEmoji(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
    val menu = lines.mapNotNull { raw ->
        if (raw.startsWith("(") || NON_MENU_LINE.containsMatchIn(raw)) return@mapNotNull null
        raw.replace(MENU_PREFIX, "")
            .replace(PARENTHESES, "")
            .replace(MENU_SUFFIX, "")
            .removePrefix("또한 ")
            .removePrefix("그리고 ")
            .trim(' ', ',', '!', '~', '.')
            .replace(Regex("""\s+"""), " ")
            .takeIf { it.isNotEmpty() }
    }
    if (menu.isNotEmpty()) return menu.joinToString(" · ")
    return lines.take(2).joinToString(" ")
}

/** 이모지 · 장식 기호를 지운다. */
fun stripEmoji(text: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAtCompat(i)
        val len = if (cp > 0xFFFF) 2 else 1
        val skip = cp >= 0x1F000 ||
            cp in 0x2190..0x21FF ||
            cp in 0x2300..0x23FF ||
            cp in 0x2460..0x27BF ||
            cp in 0x2B00..0x2BFF ||
            cp == 0x3030 || cp == 0x303D ||
            cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3
        if (!skip) sb.append(text, i, i + len)
        i += len
    }
    return sb.toString()
}

private fun String.codePointAtCompat(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
    return high.code
}
