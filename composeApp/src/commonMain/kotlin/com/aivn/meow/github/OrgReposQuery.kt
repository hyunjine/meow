package com.aivn.meow.github

import kotlinx.serialization.Serializable

/** #69 조직의 보관되지 않은 레포 이름 전체 (이름순). 100개씩 페이지네이션한다. */
suspend fun GithubClient.fetchOrgRepoNames(org: String): List<String> {
    val names = mutableListOf<String>()
    var cursor: String? = null
    do {
        val variables = buildMap {
            put("org", org)
            cursor?.let { put("cursor", it) }
        }
        val page = query(ORG_REPOS_QUERY, OrgReposData.serializer(), variables).organization.repositories
        names += page.nodes.map { it.name }
        cursor = page.pageInfo.endCursor
    } while (page.pageInfo.hasNextPage && cursor != null)
    return names
}

@Serializable
data class OrgReposData(val organization: OrgRepoOwner)

@Serializable
data class OrgRepoOwner(val repositories: OrgRepoConnection)

@Serializable
data class OrgRepoConnection(val nodes: List<OrgRepoNode> = emptyList(), val pageInfo: OrgRepoPageInfo)

@Serializable
data class OrgRepoNode(val name: String)

@Serializable
data class OrgRepoPageInfo(val hasNextPage: Boolean, val endCursor: String? = null)

private val ORG_REPOS_QUERY = """
    query(${'$'}org: String!, ${'$'}cursor: String) {
      organization(login: ${'$'}org) {
        repositories(first: 100, after: ${'$'}cursor, isArchived: false, orderBy: {field: NAME, direction: ASC}) {
          nodes { name }
          pageInfo { hasNextPage endCursor }
        }
      }
    }
""".trimIndent()
