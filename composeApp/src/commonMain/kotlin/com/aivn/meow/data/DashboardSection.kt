package com.aivn.meow.data

import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.SearchItemFields
import com.aivn.meow.model.ItemKind
import com.aivn.meow.model.Label
import com.aivn.meow.model.SectionItem
import kotlinx.coroutines.CancellationException

/**
 * 리뷰 대기 PR 아래에 붙는 보조 섹션 정의. 구현체는 `ui/sections/<Name>Section.kt` 에 두고
 * `ui/sections/DashboardSections.kt` 목록에 등록한다. 로딩 · 폴링 · 렌더링은 공통 흐름이 처리한다.
 */
interface DashboardSection {
    /** 섹션 식별자. 새로고침 간 결과를 이어 붙이는 키로 쓰인다. */
    val id: String
    val title: String
    val emptyTitle: String
    val emptyHint: String

    suspend fun load(client: GithubClient, org: String): SectionData
}

data class SectionData(
    val items: List<SectionItem>,
    val totalCount: Int = items.size,
)

/** 섹션 하나의 로딩 결과. 실패해도 대시보드 전체는 Loaded 로 유지되고 [errorMessage] 만 채워진다. */
data class SectionResult(
    val section: DashboardSection,
    val items: List<SectionItem> = emptyList(),
    val totalCount: Int = 0,
    val errorMessage: String? = null,
)

internal suspend fun DashboardSection.loadResult(client: GithubClient, org: String): SectionResult =
    try {
        val data = load(client, org)
        SectionResult(section = this, items = data.items, totalCount = data.totalCount)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SectionResult(section = this, errorMessage = e.message ?: e::class.simpleName ?: "unknown error")
    }

/** 새로고침에서 실패한 섹션은 직전 성공 목록을 유지하고 오류만 표시한다. */
internal fun List<SectionResult>.keepPreviousOnError(previous: List<SectionResult>): List<SectionResult> =
    map { result ->
        if (result.errorMessage == null) return@map result
        val prior = previous.firstOrNull { it.section.id == result.section.id && it.errorMessage == null }
            ?: return@map result
        prior.copy(errorMessage = result.errorMessage)
    }

/** 검색 노드의 공통 필드를 [SectionItem] 으로 매핑. 섹션 전용 정보는 [badges] / [detail] 로 덧붙인다. */
fun SearchItemFields.toSectionItem(
    badges: List<Label> = emptyList(),
    detail: String? = null,
    updatedAtIso: String = updatedAt,
): SectionItem {
    val repoShort = repository.nameWithOwner.substringAfter('/', repository.nameWithOwner)
    val repoColor = colorForRepo(repoShort)
    val authorLogin = author?.login ?: "unknown"
    return SectionItem(
        kind = if (typename == "PullRequest") ItemKind.PullRequest else ItemKind.Issue,
        repo = repoShort,
        repoColor = repoColor,
        number = number,
        title = title,
        author = authorLogin,
        authorInitials = initialsFrom(name = null, login = authorLogin),
        updatedAtIso = updatedAtIso,
        labels = labels.nodes.take(4).map { Label(text = it.name, color = hexColorOrFallback(it.color, repoColor)) },
        url = url,
        badges = badges,
        detail = detail,
    )
}
