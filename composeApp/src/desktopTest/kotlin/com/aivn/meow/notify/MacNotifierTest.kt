package com.aivn.meow.notify

import kotlin.test.Test
import kotlin.test.assertEquals

class MacNotifierTest {
    @Test
    fun helperCommand_passesTitleBodyAndUrlAsArgv() {
        val cmd = notifierHelperCommand("/x/MeowNotifier", DesktopNotice("새 PR 리뷰 요청 · repo", "#1 · 제목", "https://github.com/o/r/pull/1"))
        assertEquals(
            listOf("/x/MeowNotifier", "--title", "새 PR 리뷰 요청 · repo", "--body", "#1 · 제목", "--url", "https://github.com/o/r/pull/1"),
            cmd,
        )
    }

    @Test
    fun helperCommand_omitsMissingUrl() {
        assertEquals(
            listOf("/x/MeowNotifier", "--title", "새 알림 5건", "--body", "멘션 5"),
            notifierHelperCommand("/x/MeowNotifier", DesktopNotice("새 알림 5건", "멘션 5")),
        )
    }

    @Test
    fun helperCommand_truncatesLongBody() {
        val cmd = notifierHelperCommand("/x", DesktopNotice("t", "a".repeat(500)))
        assertEquals("a".repeat(MAX_MESSAGE_LENGTH - 1) + "…", cmd[4])
    }

    @Test
    fun osascriptCommand_passesTitleAndMessageAsArgvAfterScript() {
        assertEquals(
            listOf(
                "osascript",
                "-e", "on run argv",
                "-e", "display notification (item 2 of argv) with title (item 1 of argv)",
                "-e", "end run",
                "--",
                "새 PR 리뷰 요청 · repo",
                "#1 · 제목",
            ),
            osascriptNotificationCommand("새 PR 리뷰 요청 · repo", "#1 · 제목"),
        )
    }

    @Test
    fun osascriptCommand_specialCharactersArePassedVerbatim() {
        val message = "#2 · say \"hi\" \\ end\n@me: \" & do shell script \"rm -rf ~\""
        val cmd = osascriptNotificationCommand("t\"itle", message)
        assertEquals(listOf("--", "t\"itle", message), cmd.takeLast(3))
    }

    @Test
    fun osascriptCommand_truncatesLongTitleAndMessage() {
        val cmd = osascriptNotificationCommand("b".repeat(300), "a".repeat(500))
        assertEquals(MAX_TITLE_LENGTH, cmd[cmd.size - 2].length)
        assertEquals(MAX_MESSAGE_LENGTH, cmd.last().length)
        assertEquals('…', cmd.last().last())
    }

    @Test
    fun truncate_keepsTextAtLimit() {
        assertEquals("a".repeat(MAX_MESSAGE_LENGTH), truncate("a".repeat(MAX_MESSAGE_LENGTH), MAX_MESSAGE_LENGTH))
    }
}
