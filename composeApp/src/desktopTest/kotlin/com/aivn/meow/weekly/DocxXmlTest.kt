package com.aivn.meow.weekly

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #165 아이폰용 순수 Kotlin docx 표 읽기 · 고치기(DocxXml.kt)가 데스크톱 JDK DOM 구현과 같은 결과를 내는지 비교한다.
 */
class DocxXmlTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:wps="urn:x-wps"><w:body>
<w:p><w:r><w:t xml:space="preserve">  </w:t></w:r></w:p>
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>Week43-44 주간 업무 보고 &amp; 계획</w:t></w:r></w:p>
<w:tbl><w:tblPr/>
<w:tr><w:tc><w:tcPr><w:tcW w:w="1000"/></w:tcPr><w:p><w:r><w:t>이름</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>실적</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>계획</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:tcPr><w:tcW w:w='1000'/></w:tcPr><w:p><w:r><w:t>양현진</w:t></w:r></w:p></w:tc>
<w:tc><w:tcPr><w:vAlign w:val="top"/></w:tcPr><w:p><w:pPr><w:ind w:left="200"/><w:rPr><w:ins w:id="1" w:author="a"/><w:sz w:val="18"/></w:rPr></w:pPr><w:r><w:rPr><w:b/><w:rPrChange w:id="2" w:author="b"><w:rPr/></w:rPrChange></w:rPr><w:t>예전</w:t><w:tab/><w:t>실적</w:t></w:r><w:del w:id="3"><w:r><w:delText>지운 글</w:delText></w:r></w:del></w:p><w:p><w:r><w:t>둘째</w:t><w:br/><w:t>줄</w:t></w:r></w:p></w:tc>
<w:tc><w:p><w:pPr><w:rPr><w:i/></w:rPr></w:pPr></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>중첩</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><mc:AlternateContent><mc:Choice Requires="wps"><w:r><w:t>선택</w:t></w:r></mc:Choice><mc:Fallback><w:r><w:t>대체</w:t></w:r></mc:Fallback></mc:AlternateContent><w:r><w:t>&lt;끝&gt; &#xAC00;&#44032;</w:t></w:r></w:p></w:tc></w:tr>
</w:tbl><w:sectPr/></w:body></w:document>"""

    private val docx = zip(mapOf("[Content_Types].xml" to "<Types/>", "word/document.xml" to xml, "word/styles.xml" to "<w:styles/>"))

    @Test
    fun readMatchesDesktopImplementation() {
        val common = readFirstTableFromDocumentXml(xml)
        assertEquals(readDocxFirstTable(docx), common)
        assertEquals("Week43-44 주간 업무 보고 & 계획", common.title)
        assertEquals(listOf("예전\t실적", "둘째\n줄"), common.rows[1][1])
        assertEquals(listOf("", "중첩", "선택<끝> 가가"), common.rows[1][2])
    }

    @Test
    fun replaceMatchesDesktopImplementation() {
        val cells = mapOf(1 to listOf("PR #1 머지 & 배포", "", "<태그> 확인"), 2 to listOf("다음 주 계획"))
        val commonXml = replaceTableCellsInDocumentXml(xml, rowIndex = 1, cellTexts = cells)
        val desktopDocx = replaceDocxTableCells(docx, rowIndex = 1, cellTexts = cells)

        val viaCommon = readDocxFirstTable(zip(mapOf("word/document.xml" to commonXml)))
        assertEquals(readDocxFirstTable(desktopDocx), viaCommon)
        assertEquals(listOf("PR #1 머지 & 배포", "", "<태그> 확인"), viaCommon.rows[1][1])
        assertEquals(listOf("다음 주 계획"), viaCommon.rows[1][2])
        // 다른 행 · 셀은 그대로.
        assertEquals(readFirstTableFromDocumentXml(xml).rows[0], viaCommon.rows[0])
        assertEquals(readFirstTableFromDocumentXml(xml).rows[1][0], viaCommon.rows[1][0])
        // 공통 구현 결과도 표준 XML 파서로 읽힌다.
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(commonXml.toByteArray()))
    }

    @Test
    fun replaceKeepsFormattingAndRestOfDocument() {
        val out = replaceTableCellsInDocumentXml(xml, rowIndex = 1, cellTexts = mapOf(1 to listOf("새 실적")))
        // 셀 속성 · 문단 서식 유지, run 서식은 첫 run 의 rPr 에서 변경 추적만 뺀 것.
        assertTrue("<w:tcPr><w:vAlign w:val=\"top\"/></w:tcPr>" in out)
        assertTrue(
            "<w:p><w:pPr><w:ind w:left=\"200\"/><w:rPr><w:ins w:id=\"1\" w:author=\"a\"/><w:sz w:val=\"18\"/></w:rPr></w:pPr>" +
                "<w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">새 실적</w:t></w:r></w:p></w:tc>" in out,
        )
        assertFalse("예전" in out)
        // 바꾼 셀 밖은 원문 그대로.
        val tcStart = xml.indexOf("<w:tc><w:tcPr><w:vAlign")
        assertEquals(xml.substring(0, tcStart), out.substring(0, tcStart))
        val after = xml.substring(xml.indexOf("</w:tc>", tcStart) + "</w:tc>".length)
        assertTrue(out.endsWith(after))
    }

    @Test
    fun emptyCellRunFormattingFallsBackToParagraphMark() {
        val out = replaceTableCellsInDocumentXml(xml, rowIndex = 1, cellTexts = mapOf(2 to listOf("계획")))
        assertTrue("<w:r><w:rPr><w:i/></w:rPr><w:t xml:space=\"preserve\">계획</w:t></w:r>" in out)
        assertEquals(readDocxFirstTable(replaceDocxTableCells(docx, 1, mapOf(2 to listOf("계획")))), readFirstTableFromDocumentXml(out))
    }

    @Test
    fun rejectsMissingTableRowAndDoctype() {
        assertFailsWith<WeeklyDocFormatException> { replaceTableCellsInDocumentXml(xml, rowIndex = 9, cellTexts = mapOf(1 to listOf("x"))) }
        assertFailsWith<WeeklyDocFormatException> {
            readFirstTableFromDocumentXml(
                """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p/></w:body></w:document>""",
            )
        }
        assertFailsWith<WeeklyDocFormatException> { readFirstTableFromDocumentXml("""<!DOCTYPE x [<!ENTITY a "b">]><x/>""") }
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, text) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray().also { bytes ->
            // 만든 zip 이 읽히는지 확인(테스트 도우미 자체 검증).
            ZipInputStream(ByteArrayInputStream(bytes)).use { assertTrue(it.nextEntry != null) }
        }
    }
}
