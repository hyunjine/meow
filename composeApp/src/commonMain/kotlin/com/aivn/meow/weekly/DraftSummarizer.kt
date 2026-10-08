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

/** 실적 요약 요청 프롬프트. 입력은 레포 이름과 PR 제목뿐이다. */
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

/** 계획 프롬프트에 넣는 항목 최대 개수(최근 갱신 순). */
const val MAX_PLAN_INPUT_ITEMS = 30

/** 계획 프롬프트 항목 한 줄: `- 제목 [라벨, …] (마일스톤: …) (진행 중 PR)`. */
private fun DraftPlanItem.promptLine(): String = buildString {
    append("- ").append(cleanTitle)
    if (labels.isNotEmpty()) append(" [").append(labels.joinToString(", ")).append("]")
    milestone?.let { append(" (마일스톤: ").append(it).append(")") }
    if (isPullRequest) append(" (진행 중 PR)")
}

/**
 * 계획 요약 요청 프롬프트. 입력은 나에게 할당된 열린 이슈와 내 열린 PR 의 레포 · 제목 · 라벨 · 마일스톤뿐이다.
 * 레포 순서는 그 레포의 가장 최근 갱신 순, 레포 안도 최근 갱신 순.
 */
fun buildPlanPrompt(items: List<DraftPlanItem>): String {
    val recent = items.sortedByDescending { it.updatedAt }.take(MAX_PLAN_INPUT_ITEMS)
    val grouped = recent.groupBy { it.repo }.entries.joinToString("\n") { (repo, list) ->
        "• $repo\n" + list.joinToString("\n") { it.promptLine() }
    }
    return """
        |아래는 나에게 할당된 열린 이슈와 내가 올린 진행 중 PR 목록이에요(레포별로 묶음, 최근 갱신 순).
        |이걸 한국어 다음 주 업무 계획으로 정리해 주세요.
        |
        |규칙:
        |- 반드시 아래 형식만 출력: `• {레포 이름}` 줄 다음에 그 레포의 `- {한국어 계획}` 줄들.
        |- `-` 줄은 모든 레포를 합쳐 최대 $MAX_DRAFT_ITEMS 줄. 관련 이슈 · PR 은 한 줄로 합친다.
        |- 최근 갱신된 항목과 Fix · Feat 라벨 항목을 우선하고, 넘치면 오래되거나 사소한 항목은 뺀다.
        |- "(진행 중 PR)" 항목은 이미 작업 중이니 "~ 마무리" 처럼 쓴다.
        |- 다음 주 계획을 적는 한국어 주간 보고 말투의 간결한 명사형 종결(예: "결제 화면 리뉴얼 구현", "~ 대응", "~ 마무리").
        |- 레포 이름은 그대로 쓴다.
        |- 설명 · 머리말 · 맺음말 · 코드 블록 · 다른 마크다운 없이 위 형식의 줄만 출력.
        |
        |항목 목록:
        |$grouped
    """.trimMargin()
}

/**
 * 요약 없이 이슈 · PR 제목으로 만든 계획 초안. 가장 최근에 갱신한 항목 [maxItems] 개만 남기고,
 * 레포 순서는 그 레포의 가장 최근 갱신 순(레포 안도 최근 갱신 순)이다.
 */
fun fallbackPlanLines(items: List<DraftPlanItem>, maxItems: Int = MAX_DRAFT_ITEMS): List<String> {
    val recent = items.sortedByDescending { it.updatedAt }.take(maxItems)
    return recent.groupBy { it.repo }.flatMap { (repo, list) ->
        listOf("• $repo") + list.map { "- ${it.cleanTitle}" }
    }
}
