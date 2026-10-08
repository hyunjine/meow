package com.aivn.meow.weekly

import com.aivn.meow.github.GithubClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** 초안에 들어간 PR 하나. */
data class DraftPr(
    val repo: String,
    val number: Int,
    val title: String,
    /** 접두사 · `(#n)` 을 뗀 문서용 제목. */
    val cleanTitle: String,
    val url: String,
    val mergedAt: String,
)

/** 계획 초안에 들어가는 열린 항목 하나(나에게 할당된 열린 이슈, 또는 내 진행 중 PR). */
data class DraftPlanItem(
    val repo: String,
    val number: Int,
    val title: String,
    /** 접두사 · `(#n)` 을 뗀 문서용 제목. */
    val cleanTitle: String,
    val url: String,
    /** ISO-8601 갱신 시각(정렬용). */
    val updatedAt: String,
    val labels: List<String> = emptyList(),
    val milestone: String? = null,
    val isPullRequest: Boolean = false,
)

/**
 * 계획 초안. [lines] 는 실적과 같은 `• {repo}` / `- {계획}` 형식(`-` 줄 최대 [MAX_DRAFT_ITEMS] 개).
 * [summarized] 가 false 면 요약에 실패해 최근 항목 제목으로 채웠다(항목이 없을 때도 false).
 */
data class WeeklyPlanDraft(
    val lines: List<String>,
    val items: List<DraftPlanItem>,
    val summarized: Boolean = false,
)

/**
 * 실적 초안. [lines] 는 `• {repo}` / `- {요약}` 형식의 셀 문단 목록(`-` 줄 최대 [MAX_DRAFT_ITEMS] 개). 계획 초안은 [WeeklyPlanDraft].
 * [summarized] 가 false 면 요약에 실패해 최근 PR 제목으로 채웠다(PR 이 없을 때도 false).
 */
data class WeeklyDraft(
    val period: DateRange,
    val lines: List<String>,
    val pullRequests: List<DraftPr>,
    val summarized: Boolean = false,
)

/**
 * 실적 기간 동안 [org] 에서 내가 머지한 PR 을 레포별로 묶어 주간 보고 실적 초안을 만든다.
 * 이슈는 이 팀에서 PR 과 1:1(`Closes #n`)이라 중복이 돼 넣지 않는다.
 */
class WeeklyDraftBuilder(
    private val github: GithubClient,
    private val org: String = "Team-AIVN",
    private val summarizer: DraftSummarizer = defaultDraftSummarizer(),
) {
    /** (기간 + PR 목록) → 성공한 요약 줄. 동기화마다 요약을 다시 돌리지 않도록 메모리에 둔다. */
    private val summaryCache = mutableMapOf<String, List<String>>()
    private val cacheLock = Mutex()

    suspend fun buildResultDraft(period: DateRange): WeeklyDraft = summarize(period, fetchResultPrs(period))

    /** 실적 기간에 머지한 PR(머지 순). */
    suspend fun fetchResultPrs(period: DateRange): List<DraftPr> = fetchMergedPrs(period).sortedBy { it.mergedAt }

    /**
     * [prs] 를 한국어 실적 요약으로 만든다. 실패하면 최근 PR 제목 [MAX_DRAFT_ITEMS] 개로 채운다.
     * 같은 기간 · PR 목록이면 이전에 성공한 요약을 다시 쓴다([forceRefresh] 면 새로 만든다).
     */
    suspend fun summarize(period: DateRange, prs: List<DraftPr>, forceRefresh: Boolean = false): WeeklyDraft {
        if (prs.isEmpty()) return WeeklyDraft(period, emptyList(), prs)
        val key = summaryCacheKey(period, prs)
        if (!forceRefresh) {
            cacheLock.withLock { summaryCache[key] }?.let { return WeeklyDraft(period, it, prs, summarized = true) }
        }
        val raw = runCatching { summarizer.summarize(buildSummaryPrompt(prs)) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        val lines = parseSummaryLines(raw)
            ?: return WeeklyDraft(period, fallbackDraftLines(prs), prs, summarized = false)
        cacheLock.withLock { summaryCache[key] = lines }
        return WeeklyDraft(period, lines, prs, summarized = true)
    }

    /**
     * [items] 를 한국어 다음 주 계획 요약으로 만든다. 실패하면 최근 갱신 항목 제목 [MAX_DRAFT_ITEMS] 개로 채운다.
     * 같은 기간 · 항목 목록이면 이전에 성공한 요약을 다시 쓴다([forceRefresh] 면 새로 만든다).
     */
    suspend fun summarizePlan(period: DateRange, items: List<DraftPlanItem>, forceRefresh: Boolean = false): WeeklyPlanDraft {
        if (items.isEmpty()) return WeeklyPlanDraft(emptyList(), items)
        val key = "plan|" + planCacheKey(period, items)
        if (!forceRefresh) {
            cacheLock.withLock { summaryCache[key] }?.let { return WeeklyPlanDraft(it, items, summarized = true) }
        }
        val raw = runCatching { summarizer.summarize(buildPlanPrompt(items)) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        val lines = parseSummaryLines(raw)
            ?: return WeeklyPlanDraft(fallbackPlanLines(items), items, summarized = false)
        cacheLock.withLock { summaryCache[key] = lines }
        return WeeklyPlanDraft(lines, items, summarized = true)
    }

    /**
     * 계획 후보: [org] 에서 나에게 할당된 열린 이슈와 내가 올린 열린(초안 아님) PR, 각각 최근 갱신 순 최대 [PLAN_SEARCH_SIZE] 개.
     * 결과는 최근 갱신 순.
     */
    suspend fun fetchPlanItems(): List<DraftPlanItem> {
        val variables = mapOf(
            "issues" to "org:$org assignee:@me is:issue is:open sort:updated-desc",
            "prs" to "org:$org author:@me is:pr is:open draft:false sort:updated-desc",
        )
        val data = github.query(PLAN_QUERY, PlanSearchData.serializer(), variables)
        val issues = data.issues.nodes.mapNotNull { it.toPlanItem(isPullRequest = false) }
        val prs = data.prs.nodes.filter { it.isDraft != true }.mapNotNull { it.toPlanItem(isPullRequest = true) }
        return (issues + prs).sortedByDescending { it.updatedAt }
    }

    private fun PlanNode.toPlanItem(isPullRequest: Boolean): DraftPlanItem? {
        if (number == null || title == null) return null
        return DraftPlanItem(
            repo = repository?.name.orEmpty(),
            number = number,
            title = title,
            cleanTitle = cleanPrTitle(title),
            url = url.orEmpty(),
            updatedAt = updatedAt.orEmpty(),
            labels = labels?.nodes.orEmpty().mapNotNull { it.name },
            milestone = milestone?.title,
            isPullRequest = isPullRequest,
        )
    }

    private suspend fun fetchMergedPrs(period: DateRange): List<DraftPr> {
        // 날짜만 쓰면 UTC 기준이라 KST 하루 경계가 9시간 어긋난다 — 오프셋을 붙인다.
        val range = "${period.start}T00:00:00+09:00..${period.endInclusive}T23:59:59+09:00"
        val searchQuery = "org:$org author:@me is:pr is:merged merged:$range"
        val out = mutableListOf<DraftPr>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val variables = buildMap {
                put("q", searchQuery)
                cursor?.let { put("after", it) }
            }
            val search = github.query(QUERY, MergedSearchData.serializer(), variables).search
            search.nodes.forEach { node ->
                if (node.number == null || node.title == null) return@forEach
                out += DraftPr(
                    repo = node.repository?.name.orEmpty(),
                    number = node.number,
                    title = node.title,
                    cleanTitle = cleanPrTitle(node.title),
                    url = node.url.orEmpty(),
                    mergedAt = node.mergedAt.orEmpty(),
                )
            }
            if (!search.pageInfo.hasNextPage) return out
            cursor = search.pageInfo.endCursor
        }
        return out
    }

    @Serializable
    private data class MergedSearchData(val search: MergedSearch)

    @Serializable
    private data class MergedSearch(val pageInfo: PageInfo, val nodes: List<MergedPrNode>)

    @Serializable
    private data class PageInfo(val hasNextPage: Boolean, val endCursor: String? = null)

    @Serializable
    private data class MergedPrNode(
        val number: Int? = null,
        val title: String? = null,
        val url: String? = null,
        val mergedAt: String? = null,
        val repository: RepoName? = null,
    )

    @Serializable
    private data class RepoName(val name: String)

    @Serializable
    private data class PlanSearchData(val issues: PlanSearch, val prs: PlanSearch)

    @Serializable
    private data class PlanSearch(val nodes: List<PlanNode> = emptyList())

    @Serializable
    private data class PlanNode(
        val number: Int? = null,
        val title: String? = null,
        val url: String? = null,
        val updatedAt: String? = null,
        val isDraft: Boolean? = null,
        val repository: RepoName? = null,
        val labels: LabelNodes? = null,
        val milestone: Milestone? = null,
    )

    @Serializable
    private data class LabelNodes(val nodes: List<LabelName> = emptyList())

    @Serializable
    private data class LabelName(val name: String? = null)

    @Serializable
    private data class Milestone(val title: String? = null)

    companion object {
        private const val MAX_PAGES = 5
        private const val PLAN_SEARCH_SIZE = 30
        private val PLAN_QUERY = """
            query(${'$'}issues: String!, ${'$'}prs: String!) {
              issues: search(query: ${'$'}issues, type: ISSUE, first: $PLAN_SEARCH_SIZE) {
                nodes {
                  ... on Issue {
                    number title url updatedAt repository { name }
                    labels(first: 5) { nodes { name } }
                    milestone { title }
                  }
                }
              }
              prs: search(query: ${'$'}prs, type: ISSUE, first: $PLAN_SEARCH_SIZE) {
                nodes {
                  ... on PullRequest {
                    number title url updatedAt isDraft repository { name }
                    labels(first: 5) { nodes { name } }
                    milestone { title }
                  }
                }
              }
            }
        """.trimIndent()
        private val QUERY = """
            query(${'$'}q: String!, ${'$'}after: String) {
              search(query: ${'$'}q, type: ISSUE, first: 100, after: ${'$'}after) {
                pageInfo { hasNextPage endCursor }
                nodes {
                  ... on PullRequest { number title url mergedAt repository { name } }
                }
              }
            }
        """.trimIndent()
    }
}

private val LEADING_BRACKETS = Regex("""^\s*(\[[^\]]*]\s*)+""")
private val CONVENTIONAL_PREFIX = Regex(
    """^(feat|fix|chore|docs|refactor|style|perf|test|build|ci|setting|hotfix|release)(\([^)]*\))?!?\s*:\s*""",
    RegexOption.IGNORE_CASE,
)
private val ISSUE_REF = Regex("""\s*\(#\d+\)""")

/** `[Feat] 로그인 추가 (#12)` · `[#78] feat: 로그인 추가` → `로그인 추가`. */
fun cleanPrTitle(title: String): String {
    var t = title.replace(ISSUE_REF, "")
    t = t.replace(LEADING_BRACKETS, "")
    t = t.replace(CONVENTIONAL_PREFIX, "")
    t = t.replace(LEADING_BRACKETS, "")
    return t.trim().ifEmpty { title.trim() }
}

/** 요약 캐시 키: 기간 + PR(레포 · 번호 · 제목). */
internal fun summaryCacheKey(period: DateRange, prs: List<DraftPr>): String =
    "${period.start}..${period.endInclusive}|" + prs.map { "${it.repo}#${it.number}:${it.title}" }.sorted().joinToString("|")

/** 계획 요약 캐시 키: 기간 + 항목(종류 · 레포 · 번호 · 제목 · 라벨). */
internal fun planCacheKey(period: DateRange, items: List<DraftPlanItem>): String =
    "${period.start}..${period.endInclusive}|" + items.map {
        "${if (it.isPullRequest) "pr" else "issue"}:${it.repo}#${it.number}:${it.title}:${it.labels.sorted().joinToString(",")}"
    }.sorted().joinToString("|")
