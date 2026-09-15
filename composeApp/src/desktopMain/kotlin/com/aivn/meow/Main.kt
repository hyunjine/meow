package com.aivn.meow

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aivn.meow.config.loadAppConfig
import com.aivn.meow.github.GithubClient
import io.ktor.client.engine.cio.CIO
import java.awt.Desktop
import java.net.URI

fun main() = application {
    val config = loadAppConfig()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Meow",
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
    ) {
        if (config == null) {
            MissingTokenApp()
        } else {
            App(
                config = config,
                githubClientFactory = { token -> GithubClient(token, CIO) },
                onOpenUrl = { url ->
                    runCatching {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(URI(url))
                        }
                    }
                },
            )
        }
    }
}
