package com.aivn.meow.config

/**
 * #127 레포 즐겨찾기 · 사이드바 체크 상태. 레포는 모두 `owner/name`.
 * [favorites] 는 즐겨찾기한 순서 (사이드바 표시 순서), [checked] 는 그중 사이드바에서 체크한 레포.
 */
data class RepoPrefs(
    val favorites: List<String> = emptyList(),
    val checked: Set<String> = emptySet(),
) {
    /** 즐겨찾기 순서대로의 체크된 레포. 즐겨찾기에 없는 체크는 무시한다. */
    val checkedFavorites: List<String> get() = favorites.filter { it in checked }

    /** 즐겨찾기 추가는 맨 뒤에 (체크는 하지 않음), 해제는 체크도 함께 지운다. */
    fun toggleFavorite(repo: String): RepoPrefs =
        if (repo in favorites) {
            copy(favorites = favorites - repo, checked = checked - repo)
        } else {
            copy(favorites = favorites + repo)
        }

    /** 즐겨찾기 레포만 체크할 수 있다. */
    fun toggleChecked(repo: String): RepoPrefs = when {
        repo in checked -> copy(checked = checked - repo)
        repo in favorites -> copy(checked = checked + repo)
        else -> this
    }

    companion object {
        /** 즐겨찾기 파일이 없던 첫 실행: [repos] 를 즐겨찾기하고 모두 체크한다. */
        fun seeded(repos: List<String>): RepoPrefs = RepoPrefs(favorites = repos.distinct(), checked = repos.toSet())
    }
}

/** [restoreRepoPrefs] 결과. [prefs] 가 null 이면 즐겨찾기 파일이 아직 없어 첫 로딩 후 채워야 한다. */
data class RestoredRepoPrefs(val prefs: RepoPrefs?, val needsSave: Boolean)

/**
 * 저장 파일 내용으로 [RepoPrefs] 를 복원한다.
 * - [rawFavorites] 의 `/` 없는 예전 이름(#69)은 `[org]/이름` 으로 옮긴다.
 * - [rawChecked] 파일이 없으면(이번 버전 첫 실행) 기존 즐겨찾기를 모두 체크해 이전과 같은 내용을 보여준다.
 * - 즐겨찾기에 없는 체크는 버린다.
 * 옮기거나 채우거나 버린 게 있으면 [RestoredRepoPrefs.needsSave].
 */
fun restoreRepoPrefs(rawFavorites: List<String>?, rawChecked: List<String>?, org: String): RestoredRepoPrefs {
    if (rawFavorites == null) return RestoredRepoPrefs(prefs = null, needsSave = false)
    val favorites = rawFavorites.map { migrateRepoName(it, org) }.distinct()
    val checked = (rawChecked ?: favorites).map { migrateRepoName(it, org) }.filter { it in favorites }.distinct()
    val needsSave = favorites != rawFavorites || checked != rawChecked
    return RestoredRepoPrefs(RepoPrefs(favorites, checked.toSet()), needsSave)
}

/** `owner/name` 은 그대로, 예전 형식처럼 이름만 있으면 [org] 레포로 본다. */
fun migrateRepoName(name: String, org: String): String {
    val trimmed = name.trim()
    return if ('/' in trimmed) trimmed else "$org/$trimmed"
}
