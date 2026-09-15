package com.aivn.meow

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Meow",
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
    ) {
        App()
    }
}
