package com.aivn.meow.data

import androidx.compose.ui.graphics.Color
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.PullRequestNode
import com.aivn.meow.github.fetchOrgRepoNames
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.Label
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

data class DashboardSnapshot(
    val viewerLogin: String,
    val viewerInitials: String,
    val avatarUrl: String,
    val pullRequests: List<PullRequest>,
    val totalOpen: Int,
    val overdue48h: Int,
    val fetchedAtIso: String,
    /** 보조 섹션 결과. 섹션 실패는 여기 [SectionResult.errorMessage] 로만 드러난다. */
    val sections: List<SectionResult> = emptyList(),
)

class PrRepository(
    private val client: GithubClient,
    private val sections: List<DashboardSection> = emptyList(),
) {
    suspend fun load(org: String, nowIsoUtc: String): DashboardSnapshot = coroutineScope {
        // 섹션은 리뷰 요청 쿼리와 병렬로 받는다. loadResult 가 예외를 삼키므로 섹션 실패는 전파되지 않는다.
        val sectionJobs = sections.map { section -> async { section.loadResult(client, org) } }
        val data = client.fetchReviewRequests(org)
        val prs = data.search.nodes.map { it.toDomain() }
        val overdue = prs.count { isOverdue(nowIsoUtc, it.updatedAtIso) }
        DashboardSnapshot(
            viewerLogin = data.viewer.login,
            viewerInitials = initialsFrom(data.viewer.name, data.viewer.login),
            avatarUrl = data.viewer.avatarUrl,
            pullRequests = prs,
            totalOpen = data.search.issueCount,
            overdue48h = overdue,
            fetchedAtIso = nowIsoUtc,
            sections = sectionJobs.awaitAll(),
        )
    }

    /** 즐겨찾기 관리 모달의 '전체' 목록. 대시보드 폴링과 별개로 시작 · 수동 새로고침 때만 부른다. */
    suspend fun loadOrgRepos(org: String): List<String> = client.fetchOrgRepoNames(org)
}

/** '작업 중' 레포: 어느 탭에든 항목이 하나라도 있는 레포. */
fun DashboardSnapshot.workingRepos(): Set<String> =
    (pullRequests.map { it.repo } + sections.flatMap { result -> result.items.map { it.repo } }).toSet()

private fun PullRequestNode.toDomain(): PullRequest {
    val repoShort = repository.nameWithOwner.substringAfter('/', repository.nameWithOwner)
    val repoColor = colorForRepo(repoShort)
    val ci = when (commits.nodes.firstOrNull()?.commit?.statusCheckRollup?.state) {
        "SUCCESS" -> CiStatus.Pass
        "FAILURE", "ERROR" -> CiStatus.Fail
        // CI 체크 자체가 없는 PR: MyPrStatusSection 과 동일하게 칩을 숨긴다.
        null -> CiStatus.None
        else -> CiStatus.Pending
    }
    val labels = labels.nodes.take(4).map { Label(text = it.name, color = hexColorOrFallback(it.color, repoColor)) }
    val authorLogin = author?.login ?: "unknown"
    return PullRequest(
        repo = repoShort,
        repoColor = repoColor,
        number = number,
        title = title,
        author = authorLogin,
        authorInitials = initialsFrom(name = null, login = authorLogin),
        relativeTime = "", // filled by caller with local formatting; kept blank here
        updatedAtIso = updatedAt,
        isDraft = isDraft,
        ci = ci,
        labels = labels,
        url = url,
        body = bodyText,
    )
}

private val palette = listOf(
    MeowColors.Brand,
    MeowColors.Violet,
    MeowColors.Teal,
    MeowColors.Warning,
    MeowColors.Success,
    MeowColors.Grey,
    MeowColors.Error,
)

internal fun colorForRepo(repo: String): Color {
    val hash = repo.fold(0) { acc, c -> acc * 31 + c.code }
    return palette[abs(hash) % palette.size]
}

internal fun hexColorOrFallback(hex: String, fallback: Color): Color {
    val v = hex.trim().removePrefix("#")
    if (v.length != 6) return fallback
    val r = v.substring(0, 2).toIntOrNull(16) ?: return fallback
    val g = v.substring(2, 4).toIntOrNull(16) ?: return fallback
    val b = v.substring(4, 6).toIntOrNull(16) ?: return fallback
    return Color(r / 255f, g / 255f, b / 255f)
}

internal fun initialsFrom(name: String?, login: String): String {
    val source = (name?.takeIf { it.isNotBlank() } ?: login).trim()
    val parts = source.split(Regex("[\\s._-]+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        source.length >= 2 -> source.take(2).uppercase()
        source.isNotEmpty() -> source.take(1).uppercase()
        else -> "?"
    }
}

private fun isOverdue(nowIso: String, updatedIso: String): Boolean {
    val now = parseInstantSeconds(nowIso) ?: return false
    val updated = parseInstantSeconds(updatedIso) ?: return false
    return (now - updated) >= 48L * 60 * 60
}

private fun parseInstantSeconds(iso: String): Long? = runCatching {
    // Minimal ISO-8601 UTC parser: 2026-09-15T07:41:00Z
    val cleaned = iso.trimEnd('Z')
    val (date, time) = cleaned.split('T')
    val (y, mo, d) = date.split('-').map { it.toInt() }
    val (hh, mm, ss) = time.split('.').first().split(':').map { it.toInt() }
    epochSeconds(y, mo, d, hh, mm, ss)
}.getOrNull()

private fun epochSeconds(y: Int, mo: Int, d: Int, hh: Int, mm: Int, ss: Int): Long {
    // Gregorian → epoch seconds, treats input as UTC.
    val yy = if (mo <= 2) y - 1 else y
    val mm2 = if (mo <= 2) mo + 12 else mo
    val era = if (yy >= 0) yy / 400 else (yy - 399) / 400
    val yoe = (yy - era * 400).toLong()
    val doy = (153 * (mm2 - 3) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era.toLong() * 146097L + doe - 719468L
    return days * 86400L + hh * 3600L + mm * 60L + ss
}
