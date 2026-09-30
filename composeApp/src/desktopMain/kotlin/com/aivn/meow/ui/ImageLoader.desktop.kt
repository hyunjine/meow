package com.aivn.meow.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

private val imageHttp by lazy { HttpClient(CIO) }

actual suspend fun loadImageBitmap(url: String): ImageBitmap? = try {
    val response = imageHttp.get(url)
    if (!response.status.isSuccess()) {
        null
    } else {
        val bytes = response.readRawBytes()
        withContext(Dispatchers.Default) { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
