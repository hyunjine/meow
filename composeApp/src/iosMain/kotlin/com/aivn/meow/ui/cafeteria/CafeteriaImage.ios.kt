package com.aivn.meow.ui.cafeteria

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aivn.meow.cafeteria.KakaoChannelClient
import com.aivn.meow.platform.IosFiles
import com.aivn.meow.platform.sha256
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

private val http by lazy { HttpClient(Darwin) }

/**
 * 디코드한 이미지 LRU. 폰 메모리를 생각해 데스크톱(48)보다 작게 잡는다.
 * get/put 은 호출한 코루틴(UI · 메인 스레드)에서만 부르므로 잠금을 두지 않는다.
 */
private object MemoryCache {
    private const val MAX_ENTRIES = 24
    private val map = LinkedHashMap<String, ImageBitmap>()

    // 읽을 때마다 맨 뒤로 보내, 맨 앞이 가장 오래 안 쓴 항목이 되게 한다.
    fun get(url: String): ImageBitmap? = map.remove(url)?.also { map[url] = it }

    fun put(url: String, image: ImageBitmap) {
        map.remove(url)
        map[url] = image
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
    }
}

/** #165 디스크 캐시: 앱 Caches/meow/kakao/<sha256(url)> (데스크톱 `~/.cache/meow/kakao/` 에 해당). */
private fun cachePath(url: String): String {
    val hash = sha256(url.encodeToByteArray()).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "${IosFiles.cacheDir}/kakao/$hash"
}

actual fun peekCachedImage(url: String): ImageBitmap? = MemoryCache.get(url)

actual suspend fun loadCachedImage(url: String): ImageBitmap? {
    MemoryCache.get(url)?.let { return it }
    return try {
        val bytes = withContext(Dispatchers.IO) { IosFiles.readBytes(cachePath(url))?.takeIf { it.isNotEmpty() } }
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

private suspend fun saveDisk(url: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
    runCatching { IosFiles.writeBytes(cachePath(url), bytes) }
}

private suspend fun download(url: String): ByteArray? {
    val response = http.get(url) { header(HttpHeaders.UserAgent, KakaoChannelClient.USER_AGENT) }
    return if (response.status.isSuccess()) response.readRawBytes() else null
}
