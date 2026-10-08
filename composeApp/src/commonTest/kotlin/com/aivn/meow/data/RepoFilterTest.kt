package com.aivn.meow.data

import kotlin.test.Test
import kotlin.test.assertEquals

class RepoFilterTest {
    private data class Item(val id: Int, val repo: String)

    private val items = listOf(
        Item(1, "Team-AIVN/ChatSea-Android"),
        Item(2, "hyunjine/meow"),
        Item(3, "Team-AIVN/mms-agent-kmp"),
        Item(4, "hyunjine/meow"),
    )

    @Test
    fun `체크한 레포의 항목만 순서를 지켜 남긴다`() {
        val visible = items.onlyCheckedRepos(listOf("hyunjine/meow", "Team-AIVN/ChatSea-Android")) { it.repo }

        assertEquals(listOf(1, 2, 4), visible.map { it.id })
    }

    @Test
    fun `체크가 없으면 아무것도 보이지 않는다`() {
        assertEquals(emptyList(), items.onlyCheckedRepos(emptyList()) { it.repo })
    }

    @Test
    fun `owner 까지 같아야 한다 - 이름만 같은 다른 owner 레포는 제외`() {
        val visible = items.onlyCheckedRepos(listOf("Team-AIVN/meow")) { it.repo }

        assertEquals(emptyList(), visible)
    }

    @Test
    fun `대소문자는 구분하지 않는다`() {
        val visible = items.onlyCheckedRepos(listOf("team-aivn/chatsea-android")) { it.repo }

        assertEquals(listOf(1), visible.map { it.id })
    }

    @Test
    fun `owner 와 이름을 나눈다`() {
        assertEquals("meow", repoShortName("hyunjine/meow"))
        assertEquals("hyunjine", repoOwner("hyunjine/meow"))
        assertEquals("meow", repoShortName("meow"))
        assertEquals("", repoOwner("meow"))
    }
}
