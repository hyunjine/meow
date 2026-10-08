package com.aivn.meow.weekly

/**
 * PR 목록 → 한국어 실적 요약 원문. 실패(도구 없음 · 시간 초과 · 오류)면 null.
 * 데스크톱은 로컬 Claude Code CLI(`claude -p --model haiku`)를 쓴다.
 */
fun interface DraftSummarizer {
    suspend fun summarize(prompt: String): String?
}

/** 플랫폼 기본 요약기. */
expect fun defaultDraftSummarizer(): DraftSummarizer

/** 실적 칸에 넣을 `-` 세부 줄 최대 개수. */
const val MAX_DRAFT_ITEMS = 5

/** 요약 요청 프롬프트. 입력은 레포 이름과 PR 제목뿐이다. */
fun buildSummaryPrompt(prs: List<DraftPr>): String {
    val grouped = prs.groupBy { it.repo }.entries.joinToString("\n") { (repo, items) ->
        "• $repo\n" + items.joinToString("\n") { "- ${it.cleanTitle}" }
    }
    return """
        |아래는 이번 주에 머지한 PR 목록이에요(레포별로 묶음).
        |이걸 한국어 주간 업무 실적으로 요약해 주세요.
        |
        |규칙:
        |- 반드시 아래 형식만 출력: `• {레포 이름}` 줄 다음에 그 레포의 `- {한국어 요약}` 줄들.
        |- `-` 줄은 모든 레포를 합쳐 최대 $MAX_DRAFT_ITEMS 줄. 관련 PR 은 한 줄로 합치고, 넘치면 의존성 업데이트 · CI 수정 같은 사소한 작업은 뺀다.
        |- 한국어 주간 보고 말투의 간결한 명사형 종결(예: "베타 로그인 이메일 선택 드롭다운 추가", "~ 개선", "~ 수정").
        |- 레포 이름은 그대로 쓴다.
        |- 설명 · 머리말 · 맺음말 · 코드 블록 · 다른 마크다운 없이 위 형식의 줄만 출력.
        |
        |PR 목록:
        |$grouped
    """.trimMargin()
}

/**
 * 요약 원문을 셀 줄로 정리한다. `•` · `-` 로 시작하는 줄만 남기고, `-` 줄은 최대 [maxItems] 개로 자르며
 * 세부 줄이 없는 레포 줄은 버린다. 형식이 맞지 않으면(세부 줄 0개 · 첫 세부 줄 앞에 레포 줄 없음) null.
 */
fun parseSummaryLines(raw: String?, maxItems: Int = MAX_DRAFT_ITEMS): List<String>? {
    if (raw.isNullOrBlank()) return null
    val groups = mutableListOf<Pair<String, MutableList<String>>>()
    var items = 0
    for (line in raw.lines()) {
        val t = line.trim()
        when {
            t.startsWith("•") -> {
                val repo = t.removePrefix("•").trim()
                if (repo.isNotEmpty()) groups += "• $repo" to mutableListOf()
            }
            t.startsWith("-") && !t.startsWith("--") -> {
                val text = t.removePrefix("-").trim()
                if (text.isEmpty()) continue
                val group = groups.lastOrNull() ?: return null
                if (items < maxItems) {
                    group.second += "- $text"
                    items++
                }
            }
        }
    }
    if (items == 0) return null
    return groups.filter { it.second.isNotEmpty() }.flatMap { listOf(it.first) + it.second }
}

/**
 * 요약 없이 PR 제목으로 만든 초안. 가장 최근에 머지한 PR [maxItems] 개만 남기고,
 * 레포 순서는 그 레포의 첫 머지 시각 순(PR 은 머지 순)이다.
 */
fun fallbackDraftLines(prs: List<DraftPr>, maxItems: Int = MAX_DRAFT_ITEMS): List<String> {
    val recent = prs.sortedBy { it.mergedAt }.takeLast(maxItems)
    return recent.groupBy { it.repo }.flatMap { (repo, items) ->
        listOf("• $repo") + items.map { "- ${it.cleanTitle}" }
    }
}
