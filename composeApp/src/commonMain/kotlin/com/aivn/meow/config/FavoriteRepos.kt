package com.aivn.meow.config

/** #69 · #127 즐겨찾기 레포 `owner/name` 목록 (즐겨찾기한 순서). */
const val FAVORITE_REPOS_FILE = "favorite_repos"

/** #127 사이드바에서 체크한 즐겨찾기 레포 `owner/name` 목록. */
const val SIDEBAR_REPOS_FILE = "sidebar_repos"

/** `~/.config/meow/<name>` 의 한 줄에 하나씩 적힌 레포 목록. 파일이 아예 없으면 null (빈 파일은 빈 목록). */
expect fun loadRepoList(name: String): List<String>?

/** `~/.config/meow/<name>` 에 [repos] 를 순서대로 한 줄에 하나씩 쓴다. 실패는 무시한다. */
expect fun saveRepoList(name: String, repos: List<String>)
