package com.aivn.meow.schedule

import com.aivn.meow.ui.schedule.OUTLOOK_WEB_CALENDAR_URL
import com.aivn.meow.ui.schedule.OutlookOpenResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val OUTLOOK_BUNDLE_ID = "com.microsoft.Outlook"

/** Outlook 을 켜고 ⌘2 로 일정 화면으로 전환한다 (구 · 새 Outlook for Mac 공통 단축키). */
private val CALENDAR_SCRIPT = """
    tell application id "$OUTLOOK_BUNDLE_ID" to activate
    delay 0.4
    tell application "System Events" to keystroke "2" using command down
""".trimIndent()

/** 외부 명령 실행 결과. */
data class CommandResult(val exitCode: Int, val output: String)

/** 외부 명령을 실행하는 함수. 테스트에서 바꿔 끼운다. */
typealias CommandRunner = (List<String>) -> CommandResult

/** Outlook 일정 화면을 연다. 프로세스 실행은 UI 스레드 밖(IO)에서 한다. */
suspend fun openOutlookCalendar(openUrl: (String) -> Unit): OutlookOpenResult =
    withContext(Dispatchers.IO) { openOutlookCalendar(::runCommand, openUrl) }

/**
 * 1) osascript 로 Outlook 활성화 + ⌘2 → 2) 실패하면 `open -b` 로 앱만 활성화 → 3) 앱이 없으면 웹 캘린더.
 * 손쉬운 사용 권한이 없어 2) 로 떨어진 경우 [OutlookOpenResult.AppOnlyNeedsPermission] 을 돌려준다.
 */
fun openOutlookCalendar(run: CommandRunner, openUrl: (String) -> Unit): OutlookOpenResult {
    val script = run(listOf("osascript", "-e", CALENDAR_SCRIPT))
    if (script.exitCode == 0) return OutlookOpenResult.Calendar
    val open = run(listOf("open", "-b", OUTLOOK_BUNDLE_ID))
    if (open.exitCode == 0) {
        return if (isAccessibilityDenied(script.output)) OutlookOpenResult.AppOnlyNeedsPermission else OutlookOpenResult.AppOnly
    }
    openUrl(OUTLOOK_WEB_CALENDAR_URL)
    return OutlookOpenResult.Browser
}

/** System Events 키 입력이 손쉬운 사용 권한 때문에 막혔는지 (osascript 오류 -1719 / -25211 / "not allowed"). */
internal fun isAccessibilityDenied(output: String): Boolean =
    output.contains("-1719") || output.contains("-25211") || output.contains("not allowed", ignoreCase = true)

private fun runCommand(command: List<String>): CommandResult = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching CommandResult(-1, output)
    }
    CommandResult(process.exitValue(), output)
}.getOrElse { CommandResult(-1, it.message.orEmpty()) }
