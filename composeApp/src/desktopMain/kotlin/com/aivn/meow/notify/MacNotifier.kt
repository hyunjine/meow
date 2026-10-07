package com.aivn.meow.notify

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 데스크톱 알림 한 건. [url] 이 있으면 (헬퍼 앱 경로에서) 알림 클릭 시 그 주소를 연다. */
data class DesktopNotice(val title: String, val message: String, val url: String? = null)

internal const val MAX_TITLE_LENGTH = 100
internal const val MAX_MESSAGE_LENGTH = 200

internal const val NOTIFIER_APP_NAME = "Meow Notifier.app"
internal const val NOTIFIER_EXECUTABLE = "Contents/MacOS/MeowNotifier"

/** 헬퍼가 알림 권한 거부로 끝났을 때의 종료 코드. 이때는 osascript 로 대신 보내지 않는다. */
private const val EXIT_NOT_AUTHORIZED = 2

fun isMacOs(): Boolean = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)

/**
 * macOS 알림을 보낸다.
 *
 * Compose TrayState.sendNotification(AWT TrayIcon.displayMessage)은 macOS 에서 폐기된 NSUserNotification 경로를 써서
 * 번들되지 않은 java 프로세스에서는 아무것도 뜨지 않는다. 그래서 Meow 아이콘을 가진 헬퍼 앱(Meow Notifier.app,
 * UserNotifications)으로 보내고, 헬퍼가 없거나 실패하면 osascript `display notification` 으로 대신 보낸다.
 * 모든 작업은 UI 스레드 밖(Dispatchers.IO)에서 하고, 실패는 로그만 남긴다.
 */
object MacNotifier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 설치된 헬퍼 실행 파일. 처음 쓸 때 한 번만 설치/갱신한다. */
    private val helper: File? by lazy { installNotifierApp() }

    fun send(notice: DesktopNotice) {
        scope.launch {
            val exe = helper
            if (exe != null) {
                // 성공(0)·권한 거부(2)·시간 초과(null, 첫 권한 프롬프트 대기 등)면 osascript 로 다시 보내지 않는다.
                when (run(notifierHelperCommand(exe.absolutePath, notice), timeoutSec = 120)) {
                    0, EXIT_NOT_AUTHORIZED, null -> return@launch
                    else -> Unit
                }
            }
            run(osascriptNotificationCommand(notice.title, notice.message), timeoutSec = 10)
        }
    }

    /** 명령을 실행해 종료 코드를 돌려준다. 시작 실패는 -1, 시간 초과는 null. */
    private fun run(command: List<String>, timeoutSec: Long): Int? = runCatching {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            System.err.println("[meow] notification command timed out: ${command.first()}")
            return null
        }
        process.exitValue().also { if (it != 0) System.err.println("[meow] notification command failed (exit $it): ${command.first()}") }
    }.getOrElse {
        System.err.println("[meow] notification command failed: ${it.message}")
        -1
    }

    /**
     * 앱 리소스(compose.application.resources.dir)에 든 헬퍼 앱을 ~/Library/Application Support/meow 로 복사한다.
     * 알림 권한은 번들 id 기준이므로 위치가 바뀌지 않는 곳에 둔다. 실행 파일이 달라졌을 때만 다시 복사한다.
     */
    private fun installNotifierApp(): File? = runCatching {
        val target = File(System.getProperty("user.home"), "Library/Application Support/meow/$NOTIFIER_APP_NAME")
        val source = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, NOTIFIER_APP_NAME) }
            ?.takeIf { File(it, NOTIFIER_EXECUTABLE).isFile }
        if (source != null && !sameFile(File(source, NOTIFIER_EXECUTABLE), File(target, NOTIFIER_EXECUTABLE))) {
            target.deleteRecursively()
            target.parentFile.mkdirs()
            // ditto 는 실행 권한·서명·확장 속성을 그대로 보존해 복사한다.
            val exit = run(listOf("ditto", source.absolutePath, target.absolutePath), timeoutSec = 30)
            if (exit != 0) return@runCatching null
        }
        File(target, NOTIFIER_EXECUTABLE).takeIf { it.canExecute() }
    }.getOrNull()

    private fun sameFile(a: File, b: File): Boolean =
        b.isFile && a.length() == b.length() && a.readBytes().contentEquals(b.readBytes())
}

/** 헬퍼 앱 실행 인자. 셸을 거치지 않고 argv 로 넘기므로 따옴표·줄바꿈이 섞여도 안전하다. */
internal fun notifierHelperCommand(executable: String, notice: DesktopNotice): List<String> = buildList {
    add(executable)
    add("--title"); add(truncate(notice.title, MAX_TITLE_LENGTH))
    add("--body"); add(truncate(notice.message, MAX_MESSAGE_LENGTH))
    notice.url?.takeIf { it.isNotBlank() }?.let { add("--url"); add(it) }
}

/**
 * osascript 실행 인자. 제목/본문은 스크립트 문자열에 끼워 넣지 않고 `on run argv` 의 인자로 넘겨,
 * 따옴표·역슬래시·줄바꿈이 섞여도 스크립트가 깨지거나 주입되지 않게 한다.
 */
internal fun osascriptNotificationCommand(title: String, message: String): List<String> = listOf(
    "osascript",
    "-e", "on run argv",
    "-e", "display notification (item 2 of argv) with title (item 1 of argv)",
    "-e", "end run",
    "--",
    truncate(title, MAX_TITLE_LENGTH),
    truncate(message, MAX_MESSAGE_LENGTH),
)

internal fun truncate(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1) + "…"
