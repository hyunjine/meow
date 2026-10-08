package com.aivn.meow.data

/**
 * #127 사이드바에서 체크한 레포(`owner/name`)의 항목만 남긴다. 대소문자는 구분하지 않는다.
 * 체크한 레포가 없으면 빈 목록.
 */
fun <T> List<T>.onlyCheckedRepos(checked: Collection<String>, repoOf: (T) -> String): List<T> {
    if (checked.isEmpty()) return emptyList()
    val keys = checked.mapTo(HashSet()) { it.lowercase() }
    return filter { repoOf(it).lowercase() in keys }
}

/** `owner/name` 의 이름 부분 (사이드바 · 카드 표시용). */
fun repoShortName(fullName: String): String = fullName.substringAfter('/', fullName)

/** `owner/name` 의 owner 부분. 없으면 빈 문자열. */
fun repoOwner(fullName: String): String = fullName.substringBefore('/', "")
