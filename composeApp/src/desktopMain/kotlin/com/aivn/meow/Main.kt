package com.aivn.meow

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.aivn.meow.config.loadAppConfig
import com.aivn.meow.config.readGithubToken
import com.aivn.meow.github.GithubClient
import com.aivn.meow.model.CommentSource
import com.aivn.meow.model.SectionItem
import com.aivn.meow.notify.DesktopNotice
import com.aivn.meow.notify.MacNotifier
import com.aivn.meow.notify.isMacOs
import com.aivn.meow.schedule.openOutlookCalendar
import com.aivn.meow.ui.MeowNotice
import io.ktor.client.engine.cio.CIO
import java.awt.Desktop
import java.net.URI
import meow.composeapp.generated.resources.Res
import meow.composeapp.generated.resources.app_icon
import meow.composeapp.generated.resources.tray_template
import org.jetbrains.compose.resources.painterResource

fun main() {
    // 메뉴 바 아이콘을 macOS 템플릿 이미지로 표시해 다크/라이트 메뉴 바 모두에서 보이게 한다 (JDK 21+ 지원).
    // build.gradle.kts 의 jvmArgs 에도 넣었지만, 다른 실행 경로를 위해 Tray 생성 전에 한 번 더 지정한다.
    System.setProperty("apple.awt.enableTemplateImages", "true")
    runApp()
}

private fun runApp() = application {
    val config = loadAppConfig()
    val trayState = rememberTrayState()
    // macOS 에서는 AWT 트레이 알림이 뜨지 않아 헬퍼 앱(실패 시 osascript)으로 보낸다. 그 외 OS 는 트레이 알림을 쓴다.
    val notify: (DesktopNotice) -> Unit = if (isMacOs()) {
        MacNotifier::send
    } else {
        { trayState.sendNotification(Notification(it.title, it.message, Notification.Type.Info)) }
    }

    Tray(
        state = trayState,
        icon = painterResource(Res.drawable.tray_template),
        tooltip = "Meow · PR Review",
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Meow",
        icon = painterResource(Res.drawable.app_icon),
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
    ) {
        if (config == null) {
            MissingTokenApp()
        } else {
            App(
                config = config,
                // 요청마다 토큰을 다시 읽어, 교체된 토큰이 재시작 없이 반영되게 한다. 읽기 실패 시 시작 시 토큰 사용.
                githubClientFactory = { token -> GithubClient({ readGithubToken() ?: token }, CIO) },
                onOpenUrl = ::openUrlInBrowser,
                msEngine = CIO,
                onNotices = { notices -> notices.toNotifications().forEach(notify) },
                onOpenOutlookCalendar = { openOutlookCalendar(::openUrlInBrowser) },
            )
        }
    }
}

/** 한 번의 조회에서 이 수 이상이면 개별 알림 대신 한 건으로 묶는다. */
private const val GROUP_THRESHOLD = 4
private const val COMMENT_PREVIEW_LENGTH = 60

private fun List<MeowNotice>.toNotifications(): List<DesktopNotice> {
    if (size < GROUP_THRESHOLD) return map { it.toNotification() }
    // 종류별 개수 요약. 예) 리뷰 요청 2 · 멘션 1 · 내 이슈 댓글 3
    val summary = groupBy { it.kindLabel() }.entries.joinToString(" · ") { (label, list) -> "$label ${list.size}" }
    return listOf(DesktopNotice(title = "새 알림 ${size}건", message = summary))
}

private fun MeowNotice.kindLabel(): String = when (this) {
    is MeowNotice.ReviewRequested -> "리뷰 요청"
    is MeowNotice.Mentioned -> "멘션"
    is MeowNotice.NewComment -> when (source) {
        CommentSource.MyIssue -> "내 이슈 댓글"
        CommentSource.MyPr -> "내 PR 댓글"
        CommentSource.PrReview -> "리뷰 의견"
        CommentSource.Thread -> "참여 스레드 댓글"
    }
    is MeowNotice.Assigned -> "할당 이슈"
    is MeowNotice.MyPrReviewed -> if (approved) "내 PR 승인" else "내 PR 변경 요청"
}

private fun MeowNotice.toNotification(): DesktopNotice {
    val (title, message) = when (this) {
        is MeowNotice.ReviewRequested ->
            "새 PR 리뷰 요청 · ${pr.repo}" to "#${pr.number} · ${pr.title} — @${pr.author}"
        is MeowNotice.Mentioned ->
            "나를 멘션했어요 · ${item.repo}" to "#${item.number} · ${item.title} — @${item.author}"
        is MeowNotice.NewComment -> {
            val heading = when (source) {
                CommentSource.MyIssue -> "내 이슈에 새 댓글"
                CommentSource.MyPr -> "내 PR 에 새 댓글"
                CommentSource.PrReview -> "내 PR 리뷰 의견"
                CommentSource.Thread -> "참여한 스레드에 새 댓글"
            }
            "$heading · ${item.repo}" to "#${item.number} · ${item.title}" + commentPreview(item)
        }
        is MeowNotice.Assigned ->
            "새로 할당된 이슈 · ${item.repo}" to "#${item.number} · ${item.title}"
        is MeowNotice.MyPrReviewed ->
            "${if (approved) "내 PR 승인됨" else "내 PR 변경 요청"} · ${item.repo}" to "#${item.number} · ${item.title}"
    }
    return DesktopNotice(title = title, message = message, url = url())
}

/** 알림 클릭 시 열 주소. */
private fun MeowNotice.url(): String = when (this) {
    is MeowNotice.ReviewRequested -> pr.url
    is MeowNotice.Mentioned -> item.url
    is MeowNotice.NewComment -> item.url
    is MeowNotice.Assigned -> item.url
    is MeowNotice.MyPrReviewed -> item.url
}

/** 새 댓글 항목의 작성자(없으면 detail "@작성자 님의 댓글")와 body(댓글 전문)로 "@작성자: 앞부분". 데이터에 있는 만큼만 붙인다. */
private fun commentPreview(item: SectionItem): String {
    val commenter = item.comment?.commenter?.let { "@$it" } ?: item.detail?.removeSuffix(" 님의 댓글")
    val text = item.body?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { if (it.length > COMMENT_PREVIEW_LENGTH) it.take(COMMENT_PREVIEW_LENGTH) + "…" else it }
    val line = listOfNotNull(commenter, text).joinToString(": ")
    return if (line.isEmpty()) "" else "\n$line"
}

private fun openUrlInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
