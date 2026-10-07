package com.aivn.meow.weekly

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** 주간회의 자료 폴더 위치. */
data class WeeklyFolder(val driveId: String, val itemId: String, val name: String? = null)

/** 폴더 안의 주차 문서 하나. 파일명 `... Week40-41 ...` → [week]=40, [nextWeek]=41. */
@Serializable
data class WeekDoc(
    val week: Int,
    val nextWeek: Int,
    val driveId: String,
    val itemId: String,
    val name: String,
    val createdAt: Instant?,
    val modifiedAt: Instant?,
    val webUrl: String?,
)

/** 이번 주 문서를 어디서 찾았는지. */
@Serializable
enum class WeekDocSource { Folder, Mail }

@Serializable
data class ThisWeekDoc(
    val doc: WeekDoc,
    val source: WeekDocSource,
    /** [WeekDocSource.Mail] 일 때 그 메일의 수신 시각. */
    val mailReceivedAt: Instant? = null,
)

/** 날짜 구간(양 끝 포함). */
@Serializable
data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

/** 표 헤더의 한 열(실적 또는 계획) 라벨. 예: `실적(Week 40, 9.28~10.2)`. */
@Serializable
data class WeeklyColumn(
    val label: String,
    val week: Int?,
    /** 라벨의 기간 문자열 그대로(예 `9.28~10.2`). */
    val periodText: String?,
    /** 연도를 보정한 기간. 파싱 실패면 null. */
    val period: DateRange?,
)

@Serializable
data class WeeklyHeader(
    /** 표 위 제목 문단(예 `기술연구소_주간업무내용 공유 (Week 40-41)`). */
    val title: String?,
    val result: WeeklyColumn,
    val plan: WeeklyColumn,
)

/**
 * 문서에서 읽은 내 행. [found] 가 false 면 표에 내 이름 행이 없다(이때 [results]/[plans] 는 빈 목록).
 * [eTag] 는 읽은 시점의 버전 — 저장 시 그대로 넘기면 그 사이 변경을 감지한다.
 */
@Serializable
data class MyWeeklyRow(
    val doc: WeekDoc,
    val header: WeeklyHeader,
    val myName: String,
    val found: Boolean,
    /** 셀의 문단별 텍스트(예 `• ChatSea 3.0.0 관리`, `- 세부`). */
    val results: List<String>,
    val plans: List<String>,
    val eTag: String,
)

/** 문서 구조가 예상과 다름(표 없음 · 헤더 열 없음 · 내 행 없음 등). */
class WeeklyDocFormatException(message: String) : Exception(message)
