package com.aivn.meow.weekly

/*
 * #165 플랫폼 API 없이 `word/document.xml` 의 첫 표를 읽고 고치는 순수 Kotlin 구현 (아이폰용).
 * 데스크톱은 JDK DOM 구현(DocxTable.desktop.kt)을 그대로 쓰고, 두 구현이 같은 결과를 내는지 desktopTest 가 비교한다.
 *
 * 고칠 때는 문서를 다시 직렬화하지 않고 바꿀 셀(`w:tc`)의 원문 구간만 새 문단으로 갈아 끼운다.
 * 그래서 나머지 XML(선언 · 네임스페이스 · 따옴표 · 공백)은 바이트 단위로 그대로 남는다.
 */

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006"

/** document.xml 본문 첫 표를 읽는다. 데스크톱 [readDocxFirstTable] 과 같은 규칙. */
internal fun readFirstTableFromDocumentXml(xml: String): DocxTableSnapshot {
    val root = MiniXml.parse(xml)
    val body = bodyOf(root)
    val table = firstTable(body)
    val title = body.elements()
        .takeWhile { it !== table }
        .filter { it.isW("p") }
        .map { paragraphText(it).trim() }
        .firstOrNull { it.isNotEmpty() }
    val rows = table.elements().filter { it.isW("tr") }.map { tr ->
        tr.elements().filter { it.isW("tc") }.map { cellParagraphs(it) }
    }
    return DocxTableSnapshot(title = title, rows = rows)
}

/** document.xml 본문 첫 표의 [rowIndex] 행에서 [cellTexts] 열 셀만 문단 목록으로 바꾼 XML. 데스크톱 [replaceDocxTableCells] 과 같은 규칙. */
internal fun replaceTableCellsInDocumentXml(xml: String, rowIndex: Int, cellTexts: Map<Int, List<String>>): String {
    val root = MiniXml.parse(xml)
    val table = firstTable(bodyOf(root))
    val row = table.elements().filter { it.isW("tr") }.getOrNull(rowIndex)
        ?: throw WeeklyDocFormatException("표에 ${rowIndex}번째 행이 없어요")
    val cells = row.elements().filter { it.isW("tc") }
    val edits = cellTexts.map { (col, lines) ->
        val tc = cells.getOrNull(col) ?: throw WeeklyDocFormatException("행에 ${col}번째 셀이 없어요")
        tc to cellReplacement(xml, tc, lines)
    }
    // 뒤쪽 셀부터 바꿔야 앞쪽 구간의 위치가 어긋나지 않는다.
    val out = StringBuilder(xml)
    edits.sortedByDescending { it.first.start }.forEach { (tc, replacement) -> out.setRange(tc.start, tc.end, replacement) }
    return out.toString()
}

// ---- 셀 교체 ----

private fun cellReplacement(src: String, tc: MiniXml.Element, lines: List<String>): String {
    val w = tc.prefix // 셀과 같은 접두사(보통 "w")로 새 요소를 쓴다.
    fun tag(local: String) = if (w.isEmpty()) local else "$w:$local"

    val template = tc.elements().firstOrNull { it.isW("p") }
    val pPr = template?.elements()?.firstOrNull { it.isW("pPr") }
    // 글자 서식: 첫 run 의 rPr, 없으면 문단 기호 서식(pPr/rPr).
    val rPr = template?.firstDescendant("r")?.elements()?.firstOrNull { it.isW("rPr") }
        ?: pPr?.elements()?.firstOrNull { it.isW("rPr") }
    val pPrXml = pPr?.let { src.substring(it.start, it.end) }
    val rPrXml = rPr?.let { stripChangeTracking(src, it) }

    val sb = StringBuilder()
    if (tc.selfClosing) {
        sb.append(src.substring(tc.start, tc.end).removeSuffix("/>").trimEnd()).append('>')
    } else {
        sb.append(src, tc.start, tc.contentStart)
    }
    // tcPr 만 남기고 내용(문단 · 중첩 표 등)은 버린다.
    tc.elements().filter { it.isW("tcPr") }.forEach { sb.append(src, it.start, it.end) }
    // 셀은 문단이 최소 하나 있어야 한다.
    for (line in lines.ifEmpty { listOf("") }) {
        sb.append('<').append(tag("p")).append('>')
        pPrXml?.let { sb.append(it) }
        if (line.isNotEmpty()) {
            sb.append('<').append(tag("r")).append('>')
            rPrXml?.let { sb.append(it) }
            sb.append('<').append(tag("t")).append(" xml:space=\"preserve\">")
            sb.append(escapeXmlText(line))
            sb.append("</").append(tag("t")).append('>')
            sb.append("</").append(tag("r")).append('>')
        }
        sb.append("</").append(tag("p")).append('>')
    }
    if (tc.selfClosing) sb.append("</").append(tc.qName).append('>') else sb.append(src, tc.contentEnd, tc.end)
    return sb.toString()
}

/** rPr 원문에서 변경 추적용 하위 요소(w:ins · w:del · w:moveFrom · w:moveTo · w:rPrChange)를 뺀다. */
private fun stripChangeTracking(src: String, rPr: MiniXml.Element): String {
    val drop = rPr.elements().filter {
        it.isW("ins") || it.isW("del") || it.isW("moveFrom") || it.isW("moveTo") || it.isW("rPrChange")
    }
    if (drop.isEmpty()) return src.substring(rPr.start, rPr.end)
    val sb = StringBuilder()
    var pos = rPr.start
    for (child in drop) {
        sb.append(src, pos, child.start)
        pos = child.end
    }
    sb.append(src, pos, rPr.end)
    return sb.toString()
}

internal fun escapeXmlText(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '\r' -> append("&#13;")
            else -> append(c)
        }
    }
}

// ---- 텍스트 추출 ----

private fun cellParagraphs(tc: MiniXml.Element): List<String> {
    val out = mutableListOf<String>()
    fun walk(node: MiniXml.Element) {
        for (child in node.elements()) {
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

private fun paragraphText(p: MiniXml.Element): String {
    val sb = StringBuilder()
    fun walk(node: MiniXml.Element) {
        for (child in node.elements()) {
            when {
                child.ns == MC_NS && child.localName == "Fallback" -> Unit // AlternateContent 중복 방지
                child.isW("del") || child.isW("delText") || child.isW("pPr") || child.isW("rPr") -> Unit
                child.isW("t") -> sb.append(child.textContent())
                child.isW("tab") -> sb.append('\t')
                child.isW("br") || child.isW("cr") -> sb.append('\n')
                else -> walk(child)
            }
        }
    }
    walk(p)
    return sb.toString()
}

private fun MiniXml.Element.isW(name: String) = ns == W_NS && localName == name

private fun MiniXml.Element.firstDescendant(wName: String): MiniXml.Element? {
    for (child in elements()) {
        if (child.isW(wName)) return child
        child.firstDescendant(wName)?.let { return it }
    }
    return null
}

private fun bodyOf(root: MiniXml.Element): MiniXml.Element =
    root.elements().firstOrNull { it.isW("body") } ?: throw WeeklyDocFormatException("문서 본문(w:body)이 없어요")

private fun firstTable(body: MiniXml.Element): MiniXml.Element =
    body.elements().firstOrNull { it.isW("tbl") } ?: throw WeeklyDocFormatException("문서에 표가 없어요")

/**
 * docx 본문 정도를 읽을 수 있는 작은 네임스페이스 인식 XML 파서. 요소마다 원문 위치를 기억한다.
 * DTD(`<!DOCTYPE`)는 받지 않는다(데스크톱 구현의 disallow-doctype-decl 과 같음).
 */
internal object MiniXml {
    sealed interface Node

    class Text(val value: String) : Node

    class Element(
        val qName: String,
        val ns: String?,
        val start: Int,
        private val namespaces: Map<String, String>,
    ) : Node {
        val prefix: String get() = qName.substringBefore(':', "")
        val localName: String get() = qName.substringAfter(':')
        val children = mutableListOf<Node>()

        /** 시작 태그 바로 뒤 · 끝 태그 시작 · 요소 끝(끝 태그 다음) 위치. */
        var contentStart = -1
        var contentEnd = -1
        var end = -1
        var selfClosing = false

        fun elements(): List<Element> = children.filterIsInstance<Element>()

        fun textContent(): String = buildString {
            fun walk(e: Element) {
                for (c in e.children) {
                    when (c) {
                        is Text -> append(c.value)
                        is Element -> walk(c)
                    }
                }
            }
            walk(this@Element)
        }

        internal fun scope(): Map<String, String> = namespaces
    }

    fun parse(src: String): Element {
        val stack = ArrayList<Element>()
        var root: Element? = null
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c != '<') {
                val next = src.indexOf('<', i).let { if (it < 0) n else it }
                stack.lastOrNull()?.children?.add(Text(decodeEntities(src, i, next)))
                i = next
                continue
            }
            when {
                src.startsWith("<?", i) -> i = indexAfter(src, "?>", i)
                src.startsWith("<!--", i) -> i = indexAfter(src, "-->", i)
                src.startsWith("<![CDATA[", i) -> {
                    val close = src.indexOf("]]>", i + 9)
                    if (close < 0) throw WeeklyDocFormatException("문서 XML 이 잘렸어요 (CDATA)")
                    stack.lastOrNull()?.children?.add(Text(src.substring(i + 9, close)))
                    i = close + 3
                }
                src.startsWith("<!", i) -> throw WeeklyDocFormatException("문서 XML 에 DTD 가 있어 읽지 않았어요")
                src.startsWith("</", i) -> {
                    val gt = src.indexOf('>', i)
                    if (gt < 0) throw WeeklyDocFormatException("문서 XML 이 잘렸어요")
                    val name = src.substring(i + 2, gt).trim()
                    val open = stack.removeLastOrNull() ?: throw WeeklyDocFormatException("문서 XML 의 태그 짝이 맞지 않아요")
                    if (open.qName != name) throw WeeklyDocFormatException("문서 XML 의 태그 짝이 맞지 않아요 ($name)")
                    open.contentEnd = i
                    open.end = gt + 1
                    i = gt + 1
                }
                else -> {
                    val parent = stack.lastOrNull()
                    val element = parseStartTag(src, i, parent)
                    if (parent != null) parent.children.add(element) else if (root == null) root = element
                    if (element.selfClosing) {
                        element.contentStart = element.end
                        element.contentEnd = element.end
                    } else {
                        stack.add(element)
                    }
                    i = if (element.selfClosing) element.end else element.contentStart
                }
            }
        }
        if (stack.isNotEmpty()) throw WeeklyDocFormatException("문서 XML 이 잘렸어요")
        return root ?: throw WeeklyDocFormatException("문서 XML 이 비어 있어요")
    }

    private fun indexAfter(src: String, token: String, from: Int): Int {
        val at = src.indexOf(token, from)
        if (at < 0) throw WeeklyDocFormatException("문서 XML 이 잘렸어요")
        return at + token.length
    }

    private fun parseStartTag(src: String, start: Int, parent: Element?): Element {
        var i = start + 1
        val n = src.length
        while (i < n && !src[i].isWhitespace() && src[i] != '>' && src[i] != '/') i++
        val qName = src.substring(start + 1, i)
        val attrs = mutableListOf<Pair<String, String>>()
        while (true) {
            while (i < n && src[i].isWhitespace()) i++
            if (i >= n) throw WeeklyDocFormatException("문서 XML 이 잘렸어요")
            if (src[i] == '>') {
                i++
                break
            }
            if (src.startsWith("/>", i)) {
                i += 2
                return build(qName, start, attrs, parent).also {
                    it.selfClosing = true
                    it.end = i
                }
            }
            val eq = src.indexOf('=', i)
            if (eq < 0) throw WeeklyDocFormatException("문서 XML 의 속성을 읽지 못했어요")
            val name = src.substring(i, eq).trim()
            var q = eq + 1
            while (q < n && src[q].isWhitespace()) q++
            val quote = src.getOrNull(q)
            if (quote != '"' && quote != '\'') throw WeeklyDocFormatException("문서 XML 의 속성을 읽지 못했어요")
            val close = src.indexOf(quote, q + 1)
            if (close < 0) throw WeeklyDocFormatException("문서 XML 이 잘렸어요")
            attrs += name to decodeEntities(src, q + 1, close)
            i = close + 1
        }
        return build(qName, start, attrs, parent).also { it.contentStart = i }
    }

    private fun build(qName: String, start: Int, attrs: List<Pair<String, String>>, parent: Element?): Element {
        val inherited = parent?.scope() ?: emptyMap()
        val declared = attrs.mapNotNull { (name, value) ->
            when {
                name == "xmlns" -> "" to value
                name.startsWith("xmlns:") -> name.substringAfter(':') to value
                else -> null
            }
        }
        val scope = if (declared.isEmpty()) inherited else inherited + declared
        val prefix = qName.substringBefore(':', "")
        return Element(qName = qName, ns = scope[prefix], start = start, namespaces = scope)
    }

    private fun decodeEntities(src: String, from: Int, to: Int): String {
        val amp = src.indexOf('&', from)
        if (amp < 0 || amp >= to) return src.substring(from, to)
        val sb = StringBuilder(to - from)
        var i = from
        while (i < to) {
            val c = src[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val semi = src.indexOf(';', i)
            if (semi < 0 || semi >= to) throw WeeklyDocFormatException("문서 XML 의 문자 참조를 읽지 못했어요")
            val entity = src.substring(i + 1, semi)
            when {
                entity == "lt" -> sb.append('<')
                entity == "gt" -> sb.append('>')
                entity == "amp" -> sb.append('&')
                entity == "quot" -> sb.append('"')
                entity == "apos" -> sb.append('\'')
                entity.startsWith("#x") || entity.startsWith("#X") -> appendCodePoint(sb, entity.substring(2).toInt(16))
                entity.startsWith("#") -> appendCodePoint(sb, entity.substring(1).toInt())
                else -> throw WeeklyDocFormatException("문서 XML 에 모르는 엔티티(&$entity;)가 있어요")
            }
            i = semi + 1
        }
        return sb.toString()
    }

    private fun appendCodePoint(sb: StringBuilder, cp: Int) {
        if (cp < 0x10000) {
            sb.append(cp.toChar())
        } else {
            val v = cp - 0x10000
            sb.append((0xD800 + (v shr 10)).toChar())
            sb.append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }
}
