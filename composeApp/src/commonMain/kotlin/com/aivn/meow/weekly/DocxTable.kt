package com.aivn.meow.weekly

/** docx 의 본문 첫 표를 셀 단위 문단 텍스트로 읽은 결과. */
data class DocxTableSnapshot(
    /** 첫 표 앞의 첫 번째 비어 있지 않은 문단. */
    val title: String?,
    /** rows[r][c] = 그 셀의 문단 텍스트 목록. */
    val rows: List<List<List<String>>>,
)

/** 본문 첫 표를 읽는다. 표가 없으면 [WeeklyDocFormatException]. */
expect fun readDocxFirstTable(docx: ByteArray): DocxTableSnapshot

/**
 * 본문 첫 표의 [rowIndex] 행에서 [cellTexts] 의 열(키) 셀 내용만 문단 목록(값)으로 바꾼 docx 를 돌려준다.
 * 셀 속성(`w:tcPr`)은 유지하고, 기존 첫 문단의 문단 · 글자 서식을 줄마다 복제한다.
 * `word/document.xml` 외의 zip 항목은 바이트 그대로 둔다.
 */
expect fun replaceDocxTableCells(docx: ByteArray, rowIndex: Int, cellTexts: Map<Int, List<String>>): ByteArray
