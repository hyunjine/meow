package com.aivn.meow.weekly

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val MC = "http://schemas.openxmlformats.org/markup-compatibility/2006"
private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
private const val DOCUMENT_XML = "word/document.xml"

actual fun readDocxFirstTable(docx: ByteArray): DocxTableSnapshot {
    val xml = readZipEntry(docx, DOCUMENT_XML) ?: throw WeeklyDocFormatException("word/document.xml 이 없어요")
    val doc = parseXml(xml)
    val body = bodyOf(doc)
    val table = firstTable(body)
    val title = childElements(body)
        .takeWhile { it !== table }
        .filter { it.isW("p") }
        .map { paragraphText(it).trim() }
        .firstOrNull { it.isNotEmpty() }
    val rows = childElements(table).filter { it.isW("tr") }.map { tr ->
        childElements(tr).filter { it.isW("tc") }.map { tc -> cellParagraphs(tc) }
    }
    return DocxTableSnapshot(title = title, rows = rows)
}

actual fun replaceDocxTableCells(docx: ByteArray, rowIndex: Int, cellTexts: Map<Int, List<String>>): ByteArray {
    val xml = readZipEntry(docx, DOCUMENT_XML) ?: throw WeeklyDocFormatException("word/document.xml 이 없어요")
    val doc = parseXml(xml)
    val table = firstTable(bodyOf(doc))
    val row = childElements(table).filter { it.isW("tr") }.getOrNull(rowIndex)
        ?: throw WeeklyDocFormatException("표에 ${rowIndex}번째 행이 없어요")
    val cells = childElements(row).filter { it.isW("tc") }
    for ((col, lines) in cellTexts) {
        val tc = cells.getOrNull(col) ?: throw WeeklyDocFormatException("행에 ${col}번째 셀이 없어요")
        replaceCell(doc, tc, lines)
    }
    return rewriteZip(docx, DOCUMENT_XML, serialize(doc))
}

// ---- 셀 교체 ----

private fun replaceCell(doc: Document, tc: Element, lines: List<String>) {
    val template = childElements(tc).firstOrNull { it.isW("p") }
    val pPr = template?.let { p -> childElements(p).firstOrNull { it.isW("pPr") } }
    // 글자 서식: 첫 run 의 rPr, 없으면 문단 기호 서식(pPr/rPr).
    val rPr = template?.let { p -> firstDescendant(p, "r")?.let { r -> childElements(r).firstOrNull { it.isW("rPr") } } }
        ?: pPr?.let { childElements(it).firstOrNull { c -> c.isW("rPr") } }

    // tcPr 만 남기고 내용(문단 · 중첩 표 등)을 지운다.
    childNodes(tc).filterNot { it is Element && it.isW("tcPr") }.forEach { tc.removeChild(it) }

    // 셀은 문단이 최소 하나 있어야 한다.
    val effective = lines.ifEmpty { listOf("") }
    for (line in effective) {
        val p = doc.createElementNS(W, "w:p")
        pPr?.let { p.appendChild(it.cloneNode(true)) }
        if (line.isNotEmpty()) {
            val r = doc.createElementNS(W, "w:r")
            rPr?.let { r.appendChild(stripChangeTracking(it.cloneNode(true) as Element)) }
            val t = doc.createElementNS(W, "w:t")
            t.setAttributeNS(XML_NS, "xml:space", "preserve")
            t.textContent = line // DOM 직렬화가 XML 이스케이프를 맡는다.
            r.appendChild(t)
            p.appendChild(r)
        }
        tc.appendChild(p)
    }
}

/** pPr/rPr 와 run 의 rPr 는 같은 요소지만, 변경 추적용 하위 요소(w:ins · w:del 등)는 run 에 의미가 없어 뺀다. */
private fun stripChangeTracking(rPr: Element): Element {
    childElements(rPr).filter { it.isW("ins") || it.isW("del") || it.isW("moveFrom") || it.isW("moveTo") || it.isW("rPrChange") }
        .forEach { rPr.removeChild(it) }
    return rPr
}

// ---- 텍스트 추출 ----

private fun cellParagraphs(tc: Element): List<String> {
    val out = mutableListOf<String>()
    fun walk(node: Element) {
        for (child in childElements(node)) {
            when {
                child.isW("p") -> out += paragraphText(child)
                child.isW("tcPr") -> Unit
                else -> walk(child) // 중첩 표 · sdt 등
            }
        }
    }
    walk(tc)
    return out
}

private fun paragraphText(p: Element): String {
    val sb = StringBuilder()
    fun walk(node: Element) {
        for (child in childElements(node)) {
            when {
                child.namespaceURI == MC && child.localName == "Fallback" -> Unit // AlternateContent 중복 방지
                child.isW("del") || child.isW("delText") || child.isW("pPr") || child.isW("rPr") -> Unit
                child.isW("t") -> sb.append(child.textContent)
                child.isW("tab") -> sb.append('\t')
                child.isW("br") || child.isW("cr") -> sb.append('\n')
                else -> walk(child)
            }
        }
    }
    walk(p)
    return sb.toString()
}

// ---- DOM 헬퍼 ----

private fun Element.isW(name: String) = namespaceURI == W && localName == name

private fun childNodes(node: Node): List<Node> = (0 until node.childNodes.length).map { node.childNodes.item(it) }

private fun childElements(node: Node): List<Element> = childNodes(node).filterIsInstance<Element>()

private fun firstDescendant(node: Element, wName: String): Element? =
    node.getElementsByTagNameNS(W, wName).let { if (it.length > 0) it.item(0) as Element else null }

private fun bodyOf(doc: Document): Element =
    childElements(doc.documentElement).firstOrNull { it.isW("body") }
        ?: throw WeeklyDocFormatException("문서 본문(w:body)이 없어요")

private fun firstTable(body: Element): Element =
    childElements(body).firstOrNull { it.isW("tbl") } ?: throw WeeklyDocFormatException("문서에 표가 없어요")

private fun parseXml(bytes: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}

private fun serialize(doc: Document): ByteArray {
    doc.xmlStandalone = true
    val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty(OutputKeys.STANDALONE, "yes") // 원본 선언의 standalone="yes" 유지
        setOutputProperty(OutputKeys.INDENT, "no")
    }
    val out = ByteArrayOutputStream()
    transformer.transform(DOMSource(doc), StreamResult(out))
    return out.toByteArray()
}

// ---- zip ----

private fun readZipEntry(zip: ByteArray, name: String): ByteArray? {
    ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: return null
            if (entry.name == name) return zis.readBytes()
        }
    }
}

/** [name] 항목만 [content] 로 바꾸고 나머지 항목은 순서 · 내용 · 압축 방식 그대로 다시 묶는다. */
private fun rewriteZip(zip: ByteArray, name: String, content: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(zip.size + content.size)
    ZipOutputStream(out).use { zos ->
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val data = if (entry.name == name) content else zis.readBytes()
                val copy = ZipEntry(entry.name).apply {
                    method = if (entry.method == ZipEntry.STORED) ZipEntry.STORED else ZipEntry.DEFLATED
                    if (entry.time != -1L) time = entry.time
                    if (method == ZipEntry.STORED) {
                        size = data.size.toLong()
                        compressedSize = data.size.toLong()
                        crc = CRC32().also { it.update(data) }.value
                    }
                }
                zos.putNextEntry(copy)
                zos.write(data)
                zos.closeEntry()
            }
        }
    }
    return out.toByteArray()
}
