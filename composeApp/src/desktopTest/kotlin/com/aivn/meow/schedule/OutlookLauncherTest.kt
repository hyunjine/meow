package com.aivn.meow.schedule

import com.aivn.meow.ui.schedule.OUTLOOK_WEB_CALENDAR_URL
import com.aivn.meow.ui.schedule.OutlookOpenResult
import kotlin.test.Test
import kotlin.test.assertEquals

class OutlookLauncherTest {
    private fun launch(osascript: CommandResult, open: CommandResult = CommandResult(0, "")): Pair<OutlookOpenResult, List<String>> {
        val opened = mutableListOf<String>()
        val result = openOutlookCalendar(
            run = { cmd -> if (cmd.first() == "osascript") osascript else open },
            openUrl = { opened += it },
        )
        return result to opened
    }

    @Test
    fun osascriptSucceeds_switchesToCalendar() {
        assertEquals(OutlookOpenResult.Calendar to emptyList<String>(), launch(CommandResult(0, "")))
    }

    @Test
    fun accessibilityDenied_activatesAppAndAsksForPermission() {
        val denied = CommandResult(1, "execution error: System Events got an error: osascript is not allowed to send keystrokes. (1002)")
        assertEquals(OutlookOpenResult.AppOnlyNeedsPermission to emptyList<String>(), launch(denied))
        assertEquals(OutlookOpenResult.AppOnlyNeedsPermission, launch(CommandResult(1, "error (-1719)")).first)
        assertEquals(OutlookOpenResult.AppOnlyNeedsPermission, launch(CommandResult(1, "error (-25211)")).first)
    }

    @Test
    fun otherScriptFailure_activatesAppWithoutHint() {
        assertEquals(OutlookOpenResult.AppOnly, launch(CommandResult(1, "some other error")).first)
    }

    @Test
    fun outlookMissing_opensWebCalendar() {
        val result = launch(CommandResult(1, "Can't get application id"), open = CommandResult(1, "Unable to find application"))
        assertEquals(OutlookOpenResult.Browser to listOf(OUTLOOK_WEB_CALENDAR_URL), result)
    }
}
