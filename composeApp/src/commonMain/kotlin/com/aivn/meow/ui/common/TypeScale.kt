package com.aivn.meow.ui.common

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * #125 GitHub · 주간 보고 화면에서 쓰는 글자 크기 · 줄 간격 단계.
 * 색은 쓰는 곳에서 `color =` 로 덧입힌다(명시한 인자가 style 보다 우선).
 */
object MeowType {
    /** 카드 · 항목 제목. */
    val Title = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

    /** 카드 안 소제목(실적 / 계획 라벨, 빈 상태 제목 등). */
    val SectionHeading = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)

    /** 본문(마크다운 본문, 실적 · 계획 칸과 입력란, 댓글 미리보기). */
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)

    /** 보조 · 메타(레포 · #번호 · 시각, 라벨, 안내 문구). */
    val Meta = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)

    /** 작은 배지 · 칩 · 개수. 칩 높이가 커지지 않도록 줄 간격은 글자 크기에 맞춘다. */
    val Badge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)

    /** 인라인 · 펜스 코드. */
    val Code = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
}
