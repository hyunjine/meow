package com.aivn.meow.schedule

import com.aivn.meow.ms.GraphClient
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** 화면에 보일 부재 한 건(하루 단위). */
data class AbsenceEntry(
    val date: LocalDate,
    val kind: AbsenceKind,
    val name: String?,
    val note: String? = null,
    val title: String? = null,
)

/** 한 주(월~금)의 부재. [days] 는 월~금 5일 모두를 키로 가진다(빈 날은 빈 목록). */
data class ScheduleWeek(val monday: LocalDate, val days: Map<LocalDate, List<AbsenceEntry>>)

/** TeamAIVN 공용 캘린더를 못 찾음. */
class CalendarNotFoundException : Exception("'$CALENDAR_NAME' 캘린더를 찾지 못했어요")

const val CALENDAR_NAME = "TeamAIVN"

/** Outlook 의 TeamAIVN 공용 캘린더에서 주간 일정을 읽는다(읽기 전용). */
class ScheduleRepository(private val graph: GraphClient) {
    private var calendarId: String? = null

    /** 캘린더 id 를 다시 찾게 한다(로그아웃 · 계정 변경 시). */
    fun reset() {
        calendarId = null
    }

    /** [monday] 주의 월~금 부재. 캘린더가 없으면 [CalendarNotFoundException]. */
    suspend fun loadWeek(monday: LocalDate): ScheduleWeek {
        val id = findCalendarId() ?: throw CalendarNotFoundException()
        val saturday = monday.plus(5, DateTimeUnit.DAY)
        val start = "${monday}T00:00:00+09:00".encodeURLParameter()
        val end = "${saturday}T00:00:00+09:00".encodeURLParameter()
        val events = graph.getAll(
            "/me/calendars/${id.encodeURLParameter()}/calendarView" +
                "?startDateTime=$start&endDateTime=$end&\$select=subject,start,end,isAllDay&\$top=200",
            CalendarEventDto.serializer(),
            headers = mapOf("Prefer" to "outlook.timezone=\"Korea Standard Time\""),
        )
        return buildWeek(monday, events.map { it.toEvent() })
    }

    private suspend fun findCalendarId(): String? {
        calendarId?.let { return it }
        val calendars = graph.getAll("/me/calendars?\$select=id,name,owner&\$top=100", CalendarDto.serializer())
        val found = calendars.firstOrNull { it.name == CALENDAR_NAME }
            ?: calendars.firstOrNull { it.name?.trim().equals(CALENDAR_NAME, ignoreCase = true) }
        return found?.id?.also { calendarId = it }
    }
}

/** 캘린더 일정 하나. [start] · [end] 는 KST 로컬 시각, 종일 일정의 [end] 는 끝 날 다음 날 00:00(배타). */
data class CalendarEvent(
    val subject: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean,
)

/** 일정들을 [monday] 주 월~금의 날짜별 부재로 펼친다. 같은 (날짜, 종류, 이름) 은 한 번만. */
fun buildWeek(monday: LocalDate, events: List<CalendarEvent>): ScheduleWeek {
    val weekdays = (0 until 5).map { monday.plus(it, DateTimeUnit.DAY) }
    val byDay = weekdays.associateWith { mutableListOf<AbsenceEntry>() }
    for (event in events) {
        val parsed = AbsenceParser.parse(event.subject)
        if (parsed.isEmpty()) continue
        for (date in eventDates(event)) {
            val list = byDay[date] ?: continue
            parsed.forEach { list += AbsenceEntry(date, it.kind, it.name, it.note, it.title) }
        }
    }
    return ScheduleWeek(
        monday = monday,
        days = byDay.mapValues { (_, list) ->
            list.distinctBy { Triple(it.kind, it.name, if (it.name == null) it.title else null) }
                .sortedWith(compareBy<AbsenceEntry> { it.kind.ordinal }.thenBy { it.name ?: it.title.orEmpty() })
        },
    )
}

/**
 * 일정이 걸친 날짜들. 종일 일정은 끝 날짜가 배타, 시간 일정은 시작 날부터 끝 날까지
 * (끝이 자정 정각이면 그날은 빼고) 포함한다.
 */
internal fun eventDates(event: CalendarEvent): List<LocalDate> {
    val first = event.start.date
    val last = when {
        event.isAllDay -> event.end.date.plus(-1, DateTimeUnit.DAY)
        event.end.hour == 0 && event.end.minute == 0 && event.end.second == 0 && event.end.date > first ->
            event.end.date.plus(-1, DateTimeUnit.DAY)
        else -> event.end.date
    }
    if (last < first) return listOf(first)
    val out = mutableListOf<LocalDate>()
    var d = first
    while (d <= last && out.size < 62) {
        out += d
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return out
}

@Serializable
private data class CalendarDto(val id: String, val name: String? = null)

@Serializable
private data class CalendarEventDto(
    val subject: String? = null,
    val start: DateTimeTimeZoneDto? = null,
    val end: DateTimeTimeZoneDto? = null,
    val isAllDay: Boolean = false,
) {
    fun toEvent(): CalendarEvent {
        val s = start?.dateTime.toLocalDateTime()
        val e = end?.dateTime?.toLocalDateTime() ?: s
        return CalendarEvent(subject = subject.orEmpty(), start = s, end = e, isAllDay = isAllDay)
    }
}

@Serializable
private data class DateTimeTimeZoneDto(val dateTime: String? = null, val timeZone: String? = null)

/** Graph 의 `2026-09-28T00:00:00.0000000` (초 이하 7자리) → LocalDateTime. */
private fun String?.toLocalDateTime(): LocalDateTime =
    LocalDateTime.parse(orEmpty().take(19).ifEmpty { "1970-01-01T00:00:00" })
