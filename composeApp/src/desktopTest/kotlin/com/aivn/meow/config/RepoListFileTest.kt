package com.aivn.meow.config

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 실제 ~/.config/meow 대신 임시 디렉터리에서 즐겨찾기 파일 마이그레이션을 확인한다. */
class RepoListFileTest {
    private val dir = Files.createTempDirectory("meow-repo-list")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `예전 favorite_repos 를 읽어 owner-name 으로 옮겨 다시 쓴다`() {
        val favoritesFile = dir.resolve(FAVORITE_REPOS_FILE)
        val sidebarFile = dir.resolve(SIDEBAR_REPOS_FILE)
        Files.writeString(favoritesFile, "ChatSea-Android\n\n  mms-agent-kmp  \n")

        val restored = restoreRepoPrefs(readRepoListFile(favoritesFile), readRepoListFile(sidebarFile), "Team-AIVN")
        val prefs = restored.prefs!!
        writeRepoListFile(favoritesFile, prefs.favorites)
        writeRepoListFile(sidebarFile, prefs.checkedFavorites)

        assertEquals("Team-AIVN/ChatSea-Android\nTeam-AIVN/mms-agent-kmp\n", Files.readString(favoritesFile))
        assertEquals("Team-AIVN/ChatSea-Android\nTeam-AIVN/mms-agent-kmp\n", Files.readString(sidebarFile))
    }

    @Test
    fun `파일이 없으면 null, 빈 파일은 빈 목록`() {
        val file = dir.resolve(SIDEBAR_REPOS_FILE)
        assertNull(readRepoListFile(file))

        writeRepoListFile(file, emptyList())
        assertEquals(emptyList(), readRepoListFile(file))
    }
}
