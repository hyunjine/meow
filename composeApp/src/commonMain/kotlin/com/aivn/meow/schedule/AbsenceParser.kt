package com.aivn.meow.schedule

/** 부재 종류. 선언 순서가 화면의 정렬 순서다. */
enum class AbsenceKind(val label: String) {
    Vacation("휴가"),
    HalfDay("반차"),
    Sick("병가"),
    Family("경조사"),
    Checkup("건강검진"),
    Education("교육"),
    Trip("출장"),
    Field("외근"),
    Other("기타"),
}

/**
 * 일정 제목 하나에서 뽑은 부재 한 건. [name] 은 사람 이름(기타는 null),
 * [title] 은 기타일 때 보여 줄 원래 제목, [note] 는 오전/오후 · 시간 · 메모.
 */
data class ParsedAbsence(
    val kind: AbsenceKind,
    val name: String?,
    val note: String? = null,
    val title: String? = null,
)

/**
 * TeamAIVN 캘린더 일정 제목(`종류(이름...) [시간/메모]`)을 부재 목록으로 바꾼다.
 * - `휴가(김다혜)`, `오후반차(황상환)`, `출장(황상환, 이창윤 래블업 AI컨퍼런스)`
 * - `재택(김연우) 07:30-11:30, 오후반차 13:00-17:00` → 재택은 버리고 같은 사람의 오후 반차만
 * - 사무실 · 재택은 부재가 아니라서 버린다.
 * - `종류(…)` 꼴이 아니거나 모르는 종류면 기타(원래 제목 그대로, 사람 없음).
 */
object AbsenceParser {
    fun parse(subject: String): List<ParsedAbsence> {
        val raw = subject.trim()
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<ParsedAbsence>()
        var names: List<String> = emptyList()
        var matchedAny = false
        for (segment in splitTopLevel(raw)) {
            val withParens = KIND_WITH_PARENS.matchEntire(segment)
            if (withParens != null) {
                val (word, inner, trailing) = withParens.destructured
                val kind = classify(word)
                if (kind == null) continue
                matchedAny = true
                val (parsedNames, innerNote) = splitNamesAndNote(inner)
                names = parsedNames
                if (kind == Classified.Excluded) continue
                kind as Classified.Kind
                val note = joinNote(kind.half, innerNote, normalizeNote(trailing))
                if (names.isEmpty()) out += ParsedAbsence(AbsenceKind.Other, name = null, title = raw)
                names.forEach { out += ParsedAbsence(kind.kind, it, note) }
                continue
            }
            // 괄호 없는 이어진 조각: `오후반차 13:00-17:00` 은 앞 조각의 사람에게 붙인다.
            val word = segment.substringBefore(' ').trim()
            val rest = segment.substringAfter(' ', "").trim()
            val kind = classify(word)
            if (kind != null && names.isNotEmpty()) {
                matchedAny = true
                if (kind is Classified.Kind) {
                    val note = joinNote(kind.half, normalizeNote(rest))
                    names.forEach { out += ParsedAbsence(kind.kind, it, note) }
                }
            }
        }
        if (!matchedAny) {
            return listOf(ParsedAbsence(AbsenceKind.Other, name = null, title = raw))
        }
        return out.distinctBy { Triple(it.kind, it.name, it.note) }
    }

    private sealed interface Classified {
        data object Excluded : Classified
        data class Kind(val kind: AbsenceKind, val half: String? = null) : Classified
    }

    private fun classify(word: String): Classified? {
        val w = word.replace(" ", "")
        return when {
            w.isEmpty() -> null
            w.contains("사무실") || w.contains("재택") -> Classified.Excluded
            w.contains("반차") -> Classified.Kind(AbsenceKind.HalfDay, halfLabel(w))
            w.contains("휴가") || w.contains("연차") -> Classified.Kind(AbsenceKind.Vacation)
            w.contains("병가") -> Classified.Kind(AbsenceKind.Sick)
            w.contains("경조") -> Classified.Kind(AbsenceKind.Family)
            w.contains("검진") -> Classified.Kind(AbsenceKind.Checkup)
            w.contains("출장") -> Classified.Kind(AbsenceKind.Trip)
            w.contains("교육") -> Classified.Kind(AbsenceKind.Education)
            w.contains("외근") -> Classified.Kind(AbsenceKind.Field)
            else -> null
        }
    }

    /** `오후반차` → 오후, `오전반반차` → 오전 반반차, `반차` → null. */
    private fun halfLabel(word: String): String? {
        val ampm = when {
            word.contains("오전") -> "오전"
            word.contains("오후") -> "오후"
            else -> null
        }
        val quarter = if (word.contains("반반차")) "반반차" else null
        return listOfNotNull(ampm, quarter).joinToString(" ").ifEmpty { null }
    }

    /**
     * 괄호 안 → (이름들, 메모). 쉼표로 나눈 각 조각에서 앞쪽의 한글 2~4자 토큰을 이름으로 본다.
     * 첫 조각의 첫 토큰은 늘 이름, 그 뒤 토큰은 흔한 성으로 시작할 때만 이름이다(`래블업` 같은 단어는 메모).
     */
    private fun splitNamesAndNote(inner: String): Pair<List<String>, String?> {
        val names = mutableListOf<String>()
        val noteWords = mutableListOf<String>()
        inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { partIndex, part ->
            val tokens = part.split(WHITESPACE).filter { it.isNotEmpty() }
            var inNames = true
            tokens.forEachIndexed { i, token ->
                val looksLikeName = HANGUL_NAME.matches(token) &&
                    ((partIndex == 0 && i == 0) || token.first() in SURNAMES)
                if (inNames && noteWords.isEmpty() && looksLikeName) {
                    names += token
                } else {
                    inNames = false
                    noteWords += token
                }
            }
        }
        return names.distinct() to noteWords.joinToString(" ").ifEmpty { null }
    }

    /** 괄호 밖(깊이 0)의 쉼표로만 나눈다. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (c in text) {
            when {
                c == '(' -> { depth++; current.append(c) }
                c == ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(c) }
                c == ',' && depth == 0 -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** `13:00-17:00` → `13:00–17:00`. */
    private fun normalizeNote(text: String): String? =
        text.trim().replace(TIME_RANGE) { "${it.groupValues[1]}–${it.groupValues[2]}" }.ifEmpty { null }

    private fun joinNote(vararg parts: String?): String? =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }

    private val KIND_WITH_PARENS = Regex("""^([^(]+?)\s*\(([^)]*)\)\s*(.*)$""")
    private val HANGUL_NAME = Regex("""^[가-힣]{2,4}$""")
    private val WHITESPACE = Regex("""\s+""")
    private val TIME_RANGE = Regex("""(\d{1,2}:\d{2})\s*[-~–]\s*(\d{1,2}:\d{2})""")
    private val SURNAMES = (
        "김이박최정강조윤장임한오서신권황안송류유전홍고문양손배백허남심노하곽성차주우구민진나지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예경봉사부가복태목형피두감음빈동온호범좌"
        ).toSet()
}
