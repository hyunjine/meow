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
                onNewRequests = { prs ->
                    prs.forEach { pr ->
                        trayState.sendNotification(
                            Notification(
                                title = "새 PR 리뷰 요청 · ${pr.repo}",
                                message = "#${pr.number} · ${pr.title} — @${pr.author}",
                                type = Notification.Type.Info,
                            )
                        )
                    }
                },
            )
        }
    }
}

private fun openUrlInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
