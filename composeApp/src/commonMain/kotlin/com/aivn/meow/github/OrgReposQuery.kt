package com.aivn.meow.github

import kotlinx.serialization.Serializable

/** #69 · #127 조직의 보관되지 않은 레포 `owner/name` 전체 (이름순). 100개씩 페이지네이션한다. */
suspend fun GithubClient.fetchOrgRepoNames(org: String): List<String> {
    val names = mutableListOf<String>()
    var cursor: String? = null
    do {
        val variables = buildMap {
            put("org", org)
            cursor?.let { put("cursor", it) }
        }
        val page = query(ORG_REPOS_QUERY, OrgReposData.serializer(), variables).organization.repositories
        names += page.nodes.map { it.nameWithOwner }
        cursor = page.pageInfo.endCursor
    } while (page.pageInfo.hasNextPage && cursor != null)
    return names
}

/** #127 로그인한 사용자 login 과, 그 사용자가 소유한 보관되지 않은 개인 레포 `owner/name` (최근 갱신순 최대 100개). */
suspend fun GithubClient.fetchViewerRepos(): ViewerRepos {
    val viewer = query(VIEWER_REPOS_QUERY, ViewerReposData.serializer()).viewer
    return ViewerRepos(login = viewer.login, repos = viewer.repositories.nodes.map { it.nameWithOwner })
}

data class ViewerRepos(val login: String, val repos: List<String>)

@Serializable
data class OrgReposData(val organization: OrgRepoOwner)

@Serializable
data class OrgRepoOwner(val repositories: OrgRepoConnection)

@Serializable
data class OrgRepoConnection(val nodes: List<OrgRepoNode> = emptyList(), val pageInfo: OrgRepoPageInfo)

@Serializable
data class OrgRepoNode(val nameWithOwner: String)

@Serializable
data class OrgRepoPageInfo(val hasNextPage: Boolean, val endCursor: String? = null)

@Serializable
data class ViewerReposData(val viewer: ViewerRepoOwner)

@Serializable
data class ViewerRepoOwner(val login: String, val repositories: ViewerRepoConnection)

@Serializable
data class ViewerRepoConnection(val nodes: List<OrgRepoNode> = emptyList())

private val ORG_REPOS_QUERY = """
    query(${'$'}org: String!, ${'$'}cursor: String) {
      organization(login: ${'$'}org) {
        repositories(first: 100, after: ${'$'}cursor, isArchived: false, orderBy: {field: NAME, direction: ASC}) {
          nodes { nameWithOwner }
          pageInfo { hasNextPage endCursor }
        }
      }
    }
""".trimIndent()

private val VIEWER_REPOS_QUERY = """
    query {
      viewer {
        login
        repositories(ownerAffiliations: OWNER, isArchived: false, first: 100, orderBy: {field: UPDATED_AT, direction: DESC}) {
          nodes { nameWithOwner }
        }
      }
    }
""".trimIndent()
