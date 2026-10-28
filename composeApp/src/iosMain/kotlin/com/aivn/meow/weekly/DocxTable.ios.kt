package com.aivn.meow.weekly

import com.aivn.meow.platform.ZipArchive
import com.aivn.meow.platform.ZipFormatException

private const val DOCUMENT_XML = "word/document.xml"

/**
 * #165 iOS docx 읽기 · 고치기. zip 은 시스템 zlib([ZipArchive]), XML 은 공통 순수 Kotlin 구현([readFirstTableFromDocumentXml])을 쓴다.
 * 데스크톱(JDK DOM)과 같은 결과를 낸다 — desktopTest 의 DocxXmlTest 가 두 구현을 비교한다.
 */
actual fun readDocxFirstTable(docx: ByteArray): DocxTableSnapshot =
    readFirstTableFromDocumentXml(documentXml(docx).first)

actual fun replaceDocxTableCells(docx: ByteArray, rowIndex: Int, cellTexts: Map<Int, List<String>>): ByteArray {
    val (xml, zip) = documentXml(docx)
    val updated = replaceTableCellsInDocumentXml(xml, rowIndex, cellTexts)
    return zip.rewrite(DOCUMENT_XML, updated.encodeToByteArray())
}

private fun documentXml(docx: ByteArray): Pair<String, ZipArchive> {
    val zip = try {
        ZipArchive.open(docx)
    } catch (e: ZipFormatException) {
        throw WeeklyDocFormatException(e.message ?: "docx 를 열지 못했어요")
    }
    val bytes = try {
        zip.read(DOCUMENT_XML)
    } catch (e: ZipFormatException) {
        throw WeeklyDocFormatException(e.message ?: "word/document.xml 을 읽지 못했어요")
    } ?: throw WeeklyDocFormatException("word/document.xml 이 없어요")
    return bytes.decodeToString() to zip
}
