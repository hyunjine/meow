package com.aivn.meow.ui.markdown

/**
 * #119 GitHub 본문(GFM)을 마크다운 렌더러가 그릴 수 있는 형태로 다듬는다.
 *
 * - HTML 주석(`<!-- ... -->`, PR 템플릿 안내문 등)을 지운다.
 * - `<img src>` 는 `![alt](src)`, `<a href>` 는 `[text](href)` 로 바꾼다.
 * - `<details>` / `<summary>` · `<p>` · `<h1~6>` · `<b>` · `<code>` · `<br>` 등 흔한 태그는 마크다운으로 옮기고,
 *   나머지 HTML 태그는 내용만 남기고 지운다.
 * - 펜스 코드 블록과 인라인 코드 안의 내용은 건드리지 않는다.
 * - 줄바꿈을 `\n` 으로 맞추고 연속된 빈 줄을 하나로 줄인다.
 */
fun preprocessGithubMarkdown(raw: String): String {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
    val out = StringBuilder()
    splitByCodeFence(normalized).forEach { segment ->
        out.append(if (segment.isCode) segment.text else transformProse(segment.text))
    }
    return out.toString().trim()
}

/** 알림 미리보기 등 한 줄 요약용 — 마크다운 기호를 걷어낸 평문. */
fun githubMarkdownToPlainText(raw: String): String =
    preprocessGithubMarkdown(raw)
        .lines()
        .filterNot { FENCE_OPEN.matches(it) }
        .joinToString("\n") { line ->
            line
                .replace(MD_IMAGE) { it.groupValues[1] }
                .replace(MD_LINK) { it.groupValues[1] }
                .replace(LINE_PREFIX, "")
                .replace(EMPHASIS, "")
        }
        .trim()

private data class Segment(val text: String, val isCode: Boolean)

/** 펜스 코드 블록(``` / ~~~)과 그 밖의 본문을 나눈다. 닫히지 않은 펜스는 끝까지 코드로 본다. */
private fun splitByCodeFence(text: String): List<Segment> {
    val segments = mutableListOf<Segment>()
    val prose = StringBuilder()
    val code = StringBuilder()
    var fence: String? = null
    text.split('\n').forEachIndexed { index, line ->
        val withNewline = if (index == 0) line else "\n$line"
        if (fence == null) {
            val open = FENCE_OPEN.matchEntire(line)
            if (open != null) {
                if (prose.isNotEmpty()) segments += Segment(prose.toString(), isCode = false)
                prose.clear()
                fence = open.groupValues[1]
                code.append(withNewline)
            } else {
                prose.append(withNewline)
            }
        } else {
            code.append(withNewline)
            val marker = fence!!
            val trimmed = line.trim()
            if (trimmed.length >= marker.length && trimmed.all { it == marker[0] }) {
                segments += Segment(code.toString(), isCode = true)
                code.clear()
                fence = null
            }
        }
    }
    if (code.isNotEmpty()) segments += Segment(code.toString(), isCode = true)
    if (prose.isNotEmpty()) segments += Segment(prose.toString(), isCode = false)
    return segments
}

/** 코드 블록 밖의 본문을 변환한다. 인라인 코드는 자리표시자로 잠시 빼 두었다가 되돌린다. */
private fun transformProse(text: String): String {
    val codeSpans = mutableListOf<String>()
    var s = text.replace(INLINE_CODE) { match ->
        codeSpans += match.value
        "$PLACEHOLDER${codeSpans.lastIndex}$PLACEHOLDER"
    }

    s = s.replace(HTML_COMMENT, "")
    s = s.replace(IMG_TAG) { imageToMarkdown(it.value) }
    s = s.replace(A_TAG) { match ->
        val href = attr(match.groupValues[1], "href")
        val label = match.groupValues[2].replace(ANY_TAG, "").trim().ifEmpty { href.orEmpty() }
        if (href.isNullOrBlank()) label else "[$label](${destination(href)})"
    }
    s = s.replace(SUMMARY_TAG) { "\n\n**${it.groupValues[1].replace(ANY_TAG, "").trim()}**\n\n" }
    s = s.replace(HEADING_TAG) { match ->
        val level = match.groupValues[1].toInt()
        "\n\n${"#".repeat(level)} ${match.groupValues[2].replace(ANY_TAG, "").trim()}\n\n"
    }
    s = s.replace(BOLD_TAG, "**")
    s = s.replace(ITALIC_TAG, "*")
    s = s.replace(CODE_TAG, "`")
    s = s.replace(HR_TAG, "\n\n---\n\n")
    s = s.replace(BLOCK_TAG, "\n\n")
    s = s.split('\n').joinToString("\n") { line ->
        // 표 행 안의 <br> 은 줄을 끊으면 표가 깨지므로 공백으로.
        val replacement = if (line.trimStart().startsWith("|")) " " else "  \n"
        line.replace(BR_TAG, replacement)
    }
    s = s.replace(ANY_TAG, "")

    return s.replace(BLANK_LINES, "\n\n")
        .replace(PLACEHOLDER_REF) { codeSpans[it.groupValues[1].toInt()] }
}

private fun imageToMarkdown(tag: String): String {
    val src = attr(tag, "src")?.takeIf { it.isNotBlank() } ?: return ""
    val alt = attr(tag, "alt").orEmpty().replace("[", "").replace("]", "").trim()
    return "![$alt](${destination(src)})"
}

/** 공백 · 괄호가 들어간 주소는 `<...>` 로 감싸 마크다운 링크가 깨지지 않게 한다. */
private fun destination(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.any { it.isWhitespace() || it == '(' || it == ')' }) "<$trimmed>" else trimmed
}

/** HTML 태그 문자열에서 [name] 속성 값을 꺼낸다 (따옴표 유무 무관). */
private fun attr(tag: String, name: String): String? {
    val match = Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE).find(tag)
        ?: return null
    return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
}

private const val PLACEHOLDER = "\uE000"
private val PLACEHOLDER_REF = Regex("$PLACEHOLDER(\\d+)$PLACEHOLDER")

private val FENCE_OPEN = Regex("""^\s*(`{3,}|~{3,}).*$""")
private val INLINE_CODE = Regex("""(`+)(?:(?!\1).)+?\1""")
private val BLANK_LINES = Regex("""\n[ \t]*\n(?:[ \t]*\n)+""")

private val HTML_COMMENT = Regex("""<!--[\s\S]*?(?:-->|$)""")
private val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val A_TAG = Regex("""<a\b([^>]*)>([\s\S]*?)</a\s*>""", RegexOption.IGNORE_CASE)
private val SUMMARY_TAG = Regex("""<summary\b[^>]*>([\s\S]*?)</summary\s*>""", RegexOption.IGNORE_CASE)
private val HEADING_TAG = Regex("""<h([1-6])\b[^>]*>([\s\S]*?)</h\1\s*>""", RegexOption.IGNORE_CASE)
private val BOLD_TAG = Regex("""</?(?:b|strong)\s*>""", RegexOption.IGNORE_CASE)
private val ITALIC_TAG = Regex("""</?(?:i|em)\s*>""", RegexOption.IGNORE_CASE)
private val CODE_TAG = Regex("""</?code\s*>""", RegexOption.IGNORE_CASE)
private val HR_TAG = Regex("""<hr\b[^>]*/?>""", RegexOption.IGNORE_CASE)
private val BLOCK_TAG = Regex("""</?(?:p|div|details|blockquote)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""</?[a-zA-Z][a-zA-Z0-9-]*(?:\s[^<>]*)?/?>""")

private val MD_IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val MD_LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val LINE_PREFIX = Regex("""^\s*(?:#{1,6}\s+|>\s?|[-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+\.\s+)""")
private val EMPHASIS = Regex("""\*\*|__|`""")
