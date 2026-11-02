package com.aivn.meow.platform

import com.aivn.meow.weekly.readDocxFirstTable
import com.aivn.meow.weekly.replaceDocxTableCells
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** #165 iOS zip(zlib) 읽기 · 다시 묶기와 그 위의 docx 표 읽기 · 고치기. */
class ZipArchiveTest {
    private val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
<w:p><w:r><w:t>Week43-44 보고</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>이름</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>실적</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>양현진</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>예전</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
</w:body></w:document>"""

    @Test
    fun deflateRoundTrip() {
        val text = "가나다 abc ".repeat(500).encodeToByteArray()
        val packed = deflateRaw(text)
        assertContentEquals(text, inflateRaw(packed, text.size))
        assertContentEquals(ByteArray(0), inflateRaw(deflateRaw(ByteArray(0)), 0))
    }

    @Test
    fun rewriteReplacesOnlyNamedEntry() {
        val styles = "<w:styles/>".encodeToByteArray()
        val zip = buildZip(listOf("word/document.xml" to documentXml.encodeToByteArray(), "word/styles.xml" to styles))
        val archive = ZipArchive.open(zip)
        assertEquals(listOf("word/document.xml", "word/styles.xml"), archive.entries.map { it.name })

        val rewritten = ZipArchive.open(archive.rewrite("word/document.xml", "<새/>".encodeToByteArray()))
        assertContentEquals("<새/>".encodeToByteArray(), rewritten.read("word/document.xml"))
        assertContentEquals(styles, rewritten.read("word/styles.xml"))
    }

    @Test
    fun docxTableReadAndReplace() {
        val docx = buildZip(listOf("word/document.xml" to documentXml.encodeToByteArray()))
        val before = readDocxFirstTable(docx)
        assertEquals("Week43-44 보고", before.title)
        assertEquals(listOf("예전"), before.rows[1][1])

        val after = readDocxFirstTable(replaceDocxTableCells(docx, rowIndex = 1, cellTexts = mapOf(1 to listOf("새 실적 & 확인", "둘째 줄"))))
        assertEquals(listOf("새 실적 & 확인", "둘째 줄"), after.rows[1][1])
        assertEquals(before.rows[0], after.rows[0])
    }

    /** 테스트용 zip: 모든 항목을 deflate 로 넣는다. */
    private fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ArrayList<Byte>()
        val central = ArrayList<Byte>()
        fun MutableList<Byte>.u16(v: Int) {
            add(v.toByte())
            add((v shr 8).toByte())
        }
        fun MutableList<Byte>.u32(v: Int) {
            u16(v and 0xFFFF)
            u16(v ushr 16)
        }
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val packed = deflateRaw(data)
            val crc = crc32Of(data)
            val offset = out.size
            out.u32(0x04034b50); out.u16(20); out.u16(0); out.u16(8); out.u16(0); out.u16(0)
            out.u32(crc); out.u32(packed.size); out.u32(data.size); out.u16(nameBytes.size); out.u16(0)
            out.addAll(nameBytes.toList()); out.addAll(packed.toList())
            central.u32(0x02014b50); central.u16(20); central.u16(20); central.u16(0); central.u16(8); central.u16(0); central.u16(0)
            central.u32(crc); central.u32(packed.size); central.u32(data.size); central.u16(nameBytes.size)
            central.u16(0); central.u16(0); central.u16(0); central.u16(0); central.u32(0); central.u32(offset)
            central.addAll(nameBytes.toList())
        }
        val cdOffset = out.size
        out.addAll(central)
        out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(entries.size); out.u16(entries.size)
        out.u32(central.size); out.u32(cdOffset); out.u16(0)
        return out.toByteArray()
    }
}
