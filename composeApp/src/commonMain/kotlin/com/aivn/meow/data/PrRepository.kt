package com.aivn.meow.data

import androidx.compose.ui.graphics.Color
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.PullRequestNode
import com.aivn.meow.github.fetchOrgRepoNames
import com.aivn.meow.github.fetchViewerRepos
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.Label
import com.aivn.meow.model.PrBranches
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
        DashboardSnapshot(
            viewerLogin = data.viewer.login,
            viewerInitials = initialsFrom(data.viewer.name, data.viewer.login),
            avatarUrl = data.viewer.avatarUrl,
            pullRequests = prs,
            fetchedAtIso = nowIsoUtc,
            sections = sectionJobs.awaitAll(),
        )
    }

    /** #131 펼친 카드의 댓글 · 리뷰. */
    suspend fun loadDiscussion(target: DiscussionTarget): CardDiscussion = client.loadCardDiscussion(target)

    /**
     * #127 즐겨찾기 관리 모달의 레포 목록 = 조직 레포 + 내 개인 레포. 대시보드 폴링과 별개로 시작 · 수동 새로고침 때만 부른다.
     * 한쪽이 실패하면 전체를 실패로 본다 (직전 목록 유지).
     */
    suspend fun loadRepoUniverse(org: String): RepoUniverse = coroutineScope {
        val orgRepos = async { client.fetchOrgRepoNames(org) }
        val viewer = client.fetchViewerRepos()
        RepoUniverse(org = org, orgRepos = orgRepos.await(), login = viewer.login, personalRepos = viewer.repos)
    }
}

/** #127 관리 모달에 보여줄 레포 전체. 모두 `owner/name`. */
data class RepoUniverse(
    val org: String,
    val orgRepos: List<String>,
    val login: String,
    val personalRepos: List<String>,
)

/** '작업 중' 레포(`owner/name`): 어느 탭에든 항목이 하나라도 있는 레포. */
fun DashboardSnapshot.workingRepos(): List<String> =
    (
        pullRequests.map { it.repoFullName } +
            sections.filter { it.section.seedsFavorites }.flatMap { result -> result.items.map { it.repoFullName } }
        ).distinct()

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
        body = body,
        repoFullName = repository.nameWithOwner,
        branches = PrBranches.of(headRefName, baseRefName, isCrossRepository, headRepositoryOwner?.login),
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
