package com.aivn.meow.ui.cafeteria

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aivn.meow.cafeteria.KakaoChannelClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

private val http by lazy { HttpClient(CIO) }

/** 디코드한 이미지 LRU. 원본(xlarge)이 커서 개수를 작게 잡는다. */
private object MemoryCache {
    private const val MAX_ENTRIES = 48
    // accessOrder = true: 읽을 때마다 뒤로 가서, 맨 앞이 가장 오래 안 쓴 항목이 된다.
    private val map = java.util.LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)

    @Synchronized
    fun get(url: String): ImageBitmap? = map[url]

    @Synchronized
    fun put(url: String, image: ImageBitmap) {
        map[url] = image
        while (map.size > MAX_ENTRIES) {
            val eldest = map.keys.iterator().next()
            map.remove(eldest)
        }
    }
}

private val cacheDir: Path? by lazy {
    System.getProperty("user.home")?.let { Path.of(it, ".cache", "meow", "kakao") }
}

private fun cacheFile(url: String): Path? {
    val dir = cacheDir ?: return null
    val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return dir.resolve(hash)
}

actual fun peekCachedImage(url: String): ImageBitmap? = MemoryCache.get(url)

actual suspend fun loadCachedImage(url: String): ImageBitmap? {
    MemoryCache.get(url)?.let { return it }
    return try {
        val bytes = withContext(Dispatchers.IO) { readDisk(url) }
            ?: download(url)?.also { saveDisk(url, it) }
            ?: return null
        val image = withContext(Dispatchers.Default) { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        MemoryCache.put(url, image)
        image
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private fun readDisk(url: String): ByteArray? {
    val file = cacheFile(url)?.takeIf { Files.exists(it) } ?: return null
    return runCatching { Files.readAllBytes(file) }.getOrNull()?.takeIf { it.isNotEmpty() }
}

private suspend fun saveDisk(url: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
    val file = cacheFile(url) ?: return@withContext
    runCatching {
        Files.createDirectories(file.parent)
        val tmp = Files.createTempFile(file.parent, "dl", ".tmp")
        Files.write(tmp, bytes)
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

private suspend fun download(url: String): ByteArray? {
    val response = http.get(url) { header(HttpHeaders.UserAgent, KakaoChannelClient.USER_AGENT) }
    return if (response.status.isSuccess()) response.readRawBytes() else null
}
