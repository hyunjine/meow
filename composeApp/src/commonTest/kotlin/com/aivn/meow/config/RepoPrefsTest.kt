package com.aivn.meow.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoPrefsTest {
    private val org = "Team-AIVN"

    @Test
    fun `예전 이름만 있는 즐겨찾기는 org 레포로 옮기고 체크 파일이 없으면 모두 체크한다`() {
        val restored = restoreRepoPrefs(listOf("ChatSea-Android", "mms-agent-kmp"), rawChecked = null, org = org)

        val prefs = restored.prefs!!
        assertEquals(listOf("Team-AIVN/ChatSea-Android", "Team-AIVN/mms-agent-kmp"), prefs.favorites)
        assertEquals(setOf("Team-AIVN/ChatSea-Android", "Team-AIVN/mms-agent-kmp"), prefs.checked)
        assertTrue(restored.needsSave)
    }

    @Test
    fun `owner 가 있는 이름은 그대로 두고 섞여 있으면 예전 이름만 옮긴다`() {
        val restored = restoreRepoPrefs(listOf("hyunjine/meow", "ChatSea-Android", "Team-AIVN/ChatSea-Android"), null, org)

        assertEquals(listOf("hyunjine/meow", "Team-AIVN/ChatSea-Android"), restored.prefs!!.favorites)
    }

    @Test
    fun `이미 옮긴 파일은 그대로 복원하고 다시 저장하지 않는다`() {
        val restored = restoreRepoPrefs(
            rawFavorites = listOf("Team-AIVN/ChatSea-Android", "hyunjine/meow"),
            rawChecked = listOf("hyunjine/meow"),
            org = org,
        )

        assertEquals(RepoPrefs(listOf("Team-AIVN/ChatSea-Android", "hyunjine/meow"), setOf("hyunjine/meow")), restored.prefs)
        assertFalse(restored.needsSave)
    }

    @Test
    fun `즐겨찾기에 없는 체크는 버리고 저장이 필요하다`() {
        val restored = restoreRepoPrefs(listOf("hyunjine/meow"), listOf("hyunjine/meow", "hyunjine/linker"), org)

        assertEquals(setOf("hyunjine/meow"), restored.prefs!!.checked)
        assertTrue(restored.needsSave)
    }

    @Test
    fun `빈 체크 파일은 아무것도 체크하지 않은 상태로 복원한다`() {
        val restored = restoreRepoPrefs(listOf("hyunjine/meow"), emptyList(), org)

        assertEquals(emptySet(), restored.prefs!!.checked)
        assertFalse(restored.needsSave)
    }

    @Test
    fun `즐겨찾기 파일이 없으면 첫 로딩 후 채우도록 null`() {
        assertNull(restoreRepoPrefs(null, null, org).prefs)
    }

    @Test
    fun `즐겨찾기를 해제하면 체크도 함께 지운다`() {
        val prefs = RepoPrefs(listOf("Team-AIVN/ChatSea-Android", "hyunjine/meow"), setOf("Team-AIVN/ChatSea-Android", "hyunjine/meow"))

        val updated = prefs.toggleFavorite("hyunjine/meow")

        assertEquals(listOf("Team-AIVN/ChatSea-Android"), updated.favorites)
        assertEquals(setOf("Team-AIVN/ChatSea-Android"), updated.checked)
    }

    @Test
    fun `즐겨찾기를 추가하면 맨 뒤에 붙고 체크하지 않는다`() {
        val prefs = RepoPrefs(listOf("hyunjine/meow"), setOf("hyunjine/meow"))

        val updated = prefs.toggleFavorite("Team-AIVN/ChatSea-Android")

        assertEquals(listOf("hyunjine/meow", "Team-AIVN/ChatSea-Android"), updated.favorites)
        assertEquals(setOf("hyunjine/meow"), updated.checked)
    }

    @Test
    fun `해제 후 다시 즐겨찾기해도 체크는 돌아오지 않는다`() {
        val prefs = RepoPrefs(listOf("hyunjine/meow"), setOf("hyunjine/meow"))

        val updated = prefs.toggleFavorite("hyunjine/meow").toggleFavorite("hyunjine/meow")

        assertEquals(listOf("hyunjine/meow"), updated.favorites)
        assertEquals(emptyList(), updated.checkedFavorites)
    }

    @Test
    fun `체크 토글은 즐겨찾기 레포에만 적용된다`() {
        val prefs = RepoPrefs(listOf("hyunjine/meow"), emptySet())

        assertEquals(setOf("hyunjine/meow"), prefs.toggleChecked("hyunjine/meow").checked)
        assertEquals(emptySet(), prefs.toggleChecked("hyunjine/linker").checked)
        assertEquals(emptySet(), prefs.toggleChecked("hyunjine/meow").toggleChecked("hyunjine/meow").checked)
    }

    @Test
    fun `체크된 레포는 즐겨찾기 순서대로`() {
        val prefs = RepoPrefs(listOf("b/2", "a/1", "c/3"), setOf("c/3", "b/2"))

        assertEquals(listOf("b/2", "c/3"), prefs.checkedFavorites)
    }

    @Test
    fun `첫 실행 시드는 작업 중 레포를 즐겨찾기하고 모두 체크한다`() {
        val prefs = RepoPrefs.seeded(listOf("Team-AIVN/ChatSea-Android", "hyunjine/meow", "hyunjine/meow"))

        assertEquals(listOf("Team-AIVN/ChatSea-Android", "hyunjine/meow"), prefs.favorites)
        assertEquals(prefs.favorites, prefs.checkedFavorites)
    }
}
