package com.aivn.meow.cafeteria

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 한 장의 사진. 썸네일 · 카드 · 라이트박스용 URL. */
data class CafeteriaPhoto(val medium: String, val large: String, val xlarge: String, val width: Int, val height: Int)

data class WeeklyMenuPost(
    val title: String,
    val label: String?,
    val publishedAt: Instant,
    val permalink: String,
    val photo: CafeteriaPhoto?,
)

data class LunchPost(
    val title: String,
    val publishedAt: Instant,
    val permalink: String,
    val photos: List<CafeteriaPhoto>,
    val menu: String,
)

data class CafeteriaDay(
    val date: LocalDate,
    val lunch: LunchPost?,
    val holiday: Boolean,
    val holidayName: String?,
)

/** 월요일 [monday] 주의 메뉴표 · 월~금 중식. */
data class CafeteriaWeek(val monday: LocalDate, val weeklyMenu: WeeklyMenuPost?, val days: List<CafeteriaDay>)

/** 카카오 채널 게시물을 받아 주 단위로 묶는다. 받은 게시물은 세션 동안 합쳐 둔다. */
class CafeteriaRepository(private val client: KakaoChannelClient) {
    private val posts = mutableMapOf<Long, KakaoPost>()
    private val mutex = Mutex()

    /** 첫 페이지부터 받아, 가장 오래된 게시물이 [monday] − 10일 보다 이전이 될 때까지 넘긴다. */
    suspend fun loadWeek(monday: LocalDate): CafeteriaWeek = mutex.withLock {
        val boundary = monday.minus(10, DateTimeUnit.DAY)
        val oldestCached = posts.values.minOfOrNull { it.publishedAt }?.toKstDate()
        var since: String? = null
        var pages = 0
        while (pages < MAX_PAGES) {
            val page = client.fetchPage(since)
            pages++
            val overlapsCache = page.items.any { it.id in posts }
            page.items.forEach { posts[it.id] = it }
            val oldest = page.items.minByOrNull { it.publishedAt } ?: break
            if (!page.hasNext || oldest.publishedAt.toKstDate() < boundary) break
            // 이미 받은 구간과 이어졌고 캐시가 경계를 넘으면 더 받을 필요 없다.
            if (overlapsCache && oldestCached != null && oldestCached < boundary) break
            since = oldest.sort ?: break
        }
        buildWeek(posts.values.toList(), monday)
    }

    companion object {
        private const val MAX_PAGES = 30
    }
}

fun buildWeek(posts: List<KakaoPost>, monday: LocalDate): CafeteriaWeek {
    val weekly = pickWeeklyMenuPost(posts, monday)
    val holidays = weekly?.let { parseHolidays(it.text, monday) }.orEmpty()
    val lunchByDate = posts.mapNotNull { post -> parseLunchDate(post.title, post.publishedAt)?.let { it to post } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> list.maxBy { it.publishedAt } }
    val days = (0..4).map { offset ->
        val date = monday.plus(offset, DateTimeUnit.DAY)
        val lunch = lunchByDate[date]
        CafeteriaDay(
            date = date,
            lunch = lunch?.toLunchPost(),
            holiday = lunch == null && date in holidays,
            holidayName = holidays[date],
        )
    }
    return CafeteriaWeek(
        monday = monday,
        weeklyMenu = weekly?.let {
            WeeklyMenuPost(
                title = it.title,
                label = weeklyMenuLabel(it.title),
                publishedAt = Instant.fromEpochMilliseconds(it.publishedAt),
                permalink = it.permalink,
                photo = it.photos().firstOrNull(),
            )
        },
        days = days,
    )
}

private fun KakaoPost.toLunchPost() = LunchPost(
    title = title,
    publishedAt = Instant.fromEpochMilliseconds(publishedAt),
    permalink = permalink,
    photos = photos(),
    menu = condenseMenuText(text),
)

private fun KakaoPost.photos(): List<CafeteriaPhoto> = media.filter { it.type == "image" }.mapNotNull { m ->
    val medium = m.medium ?: return@mapNotNull null
    CafeteriaPhoto(medium = medium, large = m.large ?: medium, xlarge = m.xlarge ?: medium, width = m.width, height = m.height)
}
