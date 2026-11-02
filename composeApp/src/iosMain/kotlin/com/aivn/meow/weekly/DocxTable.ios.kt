package com.aivn.meow.weekly

/** #165 docx(zip · XML) 편집은 아직 데스크톱 전용. 주간 보고는 Microsoft 로그인(2단계) 뒤에야 쓰인다. */
actual fun readDocxFirstTable(docx: ByteArray): DocxTableSnapshot =
    throw WeeklyDocFormatException("아이폰에서는 아직 주간 보고 문서를 읽을 수 없어요 (2단계 예정)")

actual fun replaceDocxTableCells(docx: ByteArray, rowIndex: Int, cellTexts: Map<Int, List<String>>): ByteArray =
    throw WeeklyDocFormatException("아이폰에서는 아직 주간 보고 문서를 고칠 수 없어요 (2단계 예정)")
