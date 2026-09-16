package com.aivn.meow.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

fun formatSyncLabel(iso: String, now: Instant = Clock.System.now()): String {
    val past = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val secs = (now.epochSeconds - past.epochSeconds).coerceAtLeast(0)
    val relative = when {
        secs < 60 -> "방금 전"
        secs < 3600 -> "${secs / 60}분 전"
        secs < 86_400 -> "${secs / 3600}시간 전"
        else -> "${secs / 86_400}일 전"
    }
    val local = past.toLocalDateTime(KST)
    val date = "${local.year}-${local.monthNumber.pad2()}-${local.dayOfMonth.pad2()}"
    val time = "${local.hour.pad2()}:${local.minute.pad2()}"
    return "$date $time · $relative"
}

private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
