package com.aivn.meow.model

/**
 * #168 PR 이 어느 브랜치에서([head]) 어느 브랜치로([base]) 요청하는지.
 * 포크에서 온 PR 은 [head] 가 `owner:branch` 꼴이다 (GitHub 표기와 같다).
 */
data class PrBranches(val head: String, val base: String) {
    /** 전체 표기: `feat/#144-list-modal → dev/v1.5.0`. */
    val label: String get() = "$head$ARROW$base"

    companion object {
        const val ARROW = " → "
        /** 목록 행에서 head 를 [middleEllipsis] 로 줄이는 최대 글자 수. */
        const val DEFAULT_MAX_HEAD = 40

        /**
         * GraphQL 의 headRefName · baseRefName 으로 만든다. 둘 중 하나라도 비면 null (이슈 · 브랜치 정보 없는 PR).
         * [isCrossRepository] 면 head 앞에 `headOwner:` 를 붙인다.
         */
        fun of(headRefName: String?, baseRefName: String?, isCrossRepository: Boolean = false, headOwner: String? = null): PrBranches? {
            if (headRefName.isNullOrBlank() || baseRefName.isNullOrBlank()) return null
            val head = if (isCrossRepository && !headOwner.isNullOrBlank()) "$headOwner:$headRefName" else headRefName
            return PrBranches(head = head, base = baseRefName)
        }
    }
}

/**
 * [text] 가 [max] 자보다 길면 앞 · 뒤를 남기고 가운데를 `…` 하나로 줄인다 (`…` 포함 [max] 자).
 * 앞쪽(접두사 `feat/#144`)을 조금 더 남긴다.
 */
fun middleEllipsis(text: String, max: Int): String {
    if (max <= 0) return ""
    if (text.length <= max) return text
    if (max == 1) return "…"
    val keep = max - 1
    val front = (keep + 1) / 2
    val back = keep - front
    return text.take(front) + "…" + text.takeLast(back)
}
