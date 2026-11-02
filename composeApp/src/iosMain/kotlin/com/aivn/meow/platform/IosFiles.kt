@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.aivn.meow.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * #165 iOS 파일 경로 · 입출력. 설정은 앱 Documents/meow, 캐시는 Caches/meow 아래에 둔다
 * (데스크톱의 `~/.config/meow` · `~/.cache/meow` 에 해당).
 */
internal object IosFiles {
    val configDir: String by lazy { "${searchDir(NSDocumentDirectory)}/meow" }
    val cacheDir: String by lazy { "${searchDir(NSCachesDirectory)}/meow" }

    private fun searchDir(directory: NSSearchPathDirectory): String =
        NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String

    fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    fun ensureDir(dir: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }

    fun readText(path: String): String? =
        if (!exists(path)) null else NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)

    fun writeText(path: String, text: String) {
        ensureDir(path.substringBeforeLast('/'))
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    fun delete(path: String) {
        if (exists(path)) NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    fun readBytes(path: String): ByteArray? {
        if (!exists(path)) return null
        return NSData.dataWithContentsOfFile(path)?.toByteArray()
    }

    fun writeBytes(path: String, bytes: ByteArray) {
        ensureDir(path.substringBeforeLast('/'))
        bytes.toNSData().writeToFile(path, atomically = true)
    }
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

/** SHA-256 다이제스트 (CommonCrypto). */
@OptIn(ExperimentalUnsignedTypes::class)
internal fun sha256(bytes: ByteArray): ByteArray {
    val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes
    input.usePinned { b ->
        out.usePinned { o -> CC_SHA256(b.addressOf(0), bytes.size.convert(), o.addressOf(0)) }
    }
    return out.toByteArray()
}
