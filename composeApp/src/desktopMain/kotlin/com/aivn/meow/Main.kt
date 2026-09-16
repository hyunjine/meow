package com.aivn.meow

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.aivn.meow.config.loadAppConfig
import com.aivn.meow.github.GithubClient
import io.ktor.client.engine.cio.CIO
import java.awt.Desktop
import java.net.URI

fun main() = application {
    val config = loadAppConfig()
    val trayState = rememberTrayState()
    val icon = remember { MeowIconPainter() }

    Tray(
        state = trayState,
        icon = icon,
        tooltip = "Meow · PR Review",
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Meow",
        icon = icon,
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
    ) {
        if (config == null) {
            MissingTokenApp()
        } else {
            App(
                config = config,
                githubClientFactory = { token -> GithubClient(token, CIO) },
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

private class MeowIconPainter : Painter() {
    override val intrinsicSize: Size = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        drawRoundRect(
            color = Color(0xFF3D5EFF),
            cornerRadius = CornerRadius(size.width * 0.24f, size.height * 0.24f),
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.22f,
            center = center.copy(x = center.x - size.width * 0.14f, y = center.y - size.height * 0.02f),
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.22f,
            center = center.copy(x = center.x + size.width * 0.14f, y = center.y - size.height * 0.02f),
        )
    }
}
