package com.aivn.meow.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val KST = TimeZone.of("Asia/Seoul")

fun relativeTime(fromIso: String, now: Instant = Clock.System.now()): String {
    val past = runCatching { Instant.parse(fromIso) }.getOrNull() ?: return ""
    val secs = (now.epochSeconds - past.epochSeconds).coerceAtLeast(0)
    return when {
        secs < 60 -> "방금 업데이트"
        secs < 3600 -> "${secs / 60}분 전 업데이트"
        secs < 86_400 -> "${secs / 3600}시간 전 업데이트"
        secs < 30L * 86_400 -> "${secs / 86_400}일 전 업데이트"
        else -> "${secs / (30L * 86_400)}달 전 업데이트"
    }
}

/** #131 댓글 · 리뷰 작성 시각용 짧은 상대 시각 (`방금` · `3분 전` · `2일 전` …). */
fun relativeAgo(fromIso: String, now: Instant = Clock.System.now()): String {
    val past = runCatching { Instant.parse(fromIso) }.getOrNull() ?: return ""
    val secs = (now.epochSeconds - past.epochSeconds).coerceAtLeast(0)
    return when {
        secs < 60 -> "방금"
        secs < 3600 -> "${secs / 60}분 전"
        secs < 86_400 -> "${secs / 3600}시간 전"
        secs < 30L * 86_400 -> "${secs / 86_400}일 전"
        secs < 365L * 86_400 -> "${secs / (30L * 86_400)}달 전"
        else -> "${secs / (365L * 86_400)}년 전"
    }
}

fun formatSyncLabel(iso: String, now: Instant = Clock.System.now()): String {
    val past = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val secs = (now.epochSeconds - past.epochSeconds).coerceAtLeast(0)
    val relative = when {
        secs < 60 -> "방금 전"
        secs < 3600 -> "${secs / 60}분 전"
        secs < 86_400 -> "${secs / 3600}시간 전"
        else -> "${secs / 86_400}일 전"
    }
    return "${formatKst(past)} · $relative"
}

/** `yyyy-MM-dd HH:mm` (KST). */
fun formatKst(instant: Instant): String {
    val local = instant.toLocalDateTime(KST)
    val date = "${local.year}-${local.month.number.pad2()}-${local.day.pad2()}"
    val time = "${local.hour.pad2()}:${local.minute.pad2()}"
    return "$date $time"
}

private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
