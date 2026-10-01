package com.aivn.meow.config

/** #69 사이드바에 고정할 즐겨찾기 레포 이름. 저장 파일이 아예 없으면 null (빈 파일은 빈 집합). */
expect fun loadFavoriteRepos(): Set<String>?

expect fun saveFavoriteRepos(repos: Set<String>)
