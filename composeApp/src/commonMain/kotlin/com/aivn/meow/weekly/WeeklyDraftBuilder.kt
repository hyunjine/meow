package com.aivn.meow.weekly

import com.aivn.meow.github.GithubClient
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

/** 실적 초안. [lines] 는 `• {repo}` / `- {제목}` 형식의 셀 문단 목록, 계획은 비워 둔다. */
data class WeeklyDraft(
    val period: DateRange,
    val lines: List<String>,
    val pullRequests: List<DraftPr>,
)

/**
 * 실적 기간 동안 [org] 에서 내가 머지한 PR 을 레포별로 묶어 주간 보고 실적 초안을 만든다.
 * 이슈는 이 팀에서 PR 과 1:1(`Closes #n`)이라 중복이 돼 넣지 않는다.
 */
class WeeklyDraftBuilder(
    private val github: GithubClient,
    private val org: String = "Team-AIVN",
) {
    suspend fun buildResultDraft(period: DateRange): WeeklyDraft {
        val prs = fetchMergedPrs(period)
            .sortedBy { it.mergedAt }
        // 레포 순서: 그 레포의 첫 머지 시각 순.
        val lines = prs.groupBy { it.repo }.flatMap { (repo, items) ->
            listOf("• $repo") + items.map { "- ${it.cleanTitle}" }
        }
        return WeeklyDraft(period = period, lines = lines, pullRequests = prs)
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

    companion object {
        private const val MAX_PAGES = 5
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
