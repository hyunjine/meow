@file:OptIn(ExperimentalForeignApi::class)

package com.aivn.meow.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/**
 * #165 docx 용 최소 zip 읽기 · 다시 묶기 (iOS, 압축은 시스템 zlib). 데스크톱의 java.util.zip 대신 쓴다.
 * 지원: 저장(0) · deflate(8) 항목, 암호화 · zip64 · 분할 아카이브는 지원하지 않는다(Word 문서엔 없다).
 */
internal class ZipArchive private constructor(private val bytes: ByteArray, val entries: List<Entry>) {

    class Entry(
        val name: String,
        val method: Int,
        val flags: Int,
        val modTime: Int,
        val modDate: Int,
        val crc: Int,
        val compressedSize: Int,
        val size: Int,
        val localHeaderOffset: Int,
        val nameBytes: ByteArray,
    )

    /** [name] 항목의 압축을 푼 내용. 없으면 null. */
    fun read(name: String): ByteArray? = entries.firstOrNull { it.name == name }?.let { uncompressed(it) }

    /**
     * [name] 항목만 [content] 로 바꾸고 나머지 항목은 순서 · 압축 데이터 · 시각 그대로 다시 묶는다.
     * 바꾼 항목은 deflate 로 다시 압축한다(원래 저장 방식이었으면 저장 그대로).
     */
    fun rewrite(name: String, content: ByteArray): ByteArray {
        val out = ByteSink(bytes.size + content.size)
        val central = ByteSink(entries.size * 64)
        for (entry in entries) {
            val replaced = entry.name == name
            val method: Int
            val data: ByteArray
            val crc: Int
            val size: Int
            if (replaced) {
                method = if (entry.method == METHOD_STORED) METHOD_STORED else METHOD_DEFLATED
                data = if (method == METHOD_STORED) content else deflateRaw(content)
                crc = crc32Of(content)
                size = content.size
            } else {
                method = entry.method
                data = rawData(entry)
                crc = entry.crc
                size = entry.size
            }
            // 크기 · CRC 를 로컬 헤더에 바로 쓰므로 data descriptor(비트 3)는 끈다.
            val flags = entry.flags and FLAG_DATA_DESCRIPTOR.inv()
            val offset = out.size
            out.u32(SIG_LOCAL)
            out.u16(20)
            out.u16(flags)
            out.u16(method)
            out.u16(entry.modTime)
            out.u16(entry.modDate)
            out.u32(crc)
            out.u32(data.size)
            out.u32(size)
            out.u16(entry.nameBytes.size)
            out.u16(0)
            out.bytes(entry.nameBytes)
            out.bytes(data)

            central.u32(SIG_CENTRAL)
            central.u16(20)
            central.u16(20)
            central.u16(flags)
            central.u16(method)
            central.u16(entry.modTime)
            central.u16(entry.modDate)
            central.u32(crc)
            central.u32(data.size)
            central.u32(size)
            central.u16(entry.nameBytes.size)
            central.u16(0) // extra
            central.u16(0) // comment
            central.u16(0) // disk
            central.u16(0) // internal attrs
            central.u32(0) // external attrs
            central.u32(offset)
            central.bytes(entry.nameBytes)
        }
        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        out.u32(SIG_EOCD)
        out.u16(0)
        out.u16(0)
        out.u16(entries.size)
        out.u16(entries.size)
        out.u32(centralBytes.size)
        out.u32(centralOffset)
        out.u16(0)
        return out.toByteArray()
    }

    private fun rawData(entry: Entry): ByteArray {
        val at = entry.localHeaderOffset
        if (u32(bytes, at) != SIG_LOCAL) throw ZipFormatException("zip 로컬 헤더가 올바르지 않아요 (${entry.name})")
        val start = at + 30 + u16(bytes, at + 26) + u16(bytes, at + 28)
        val end = start + entry.compressedSize
        if (end > bytes.size) throw ZipFormatException("zip 항목이 잘렸어요 (${entry.name})")
        return bytes.copyOfRange(start, end)
    }

    private fun uncompressed(entry: Entry): ByteArray {
        val raw = rawData(entry)
        val data = when (entry.method) {
            METHOD_STORED -> raw
            METHOD_DEFLATED -> inflateRaw(raw, entry.size)
            else -> throw ZipFormatException("지원하지 않는 zip 압축 방식이에요 (${entry.method})")
        }
        if (crc32Of(data) != entry.crc) throw ZipFormatException("zip 항목의 CRC 가 맞지 않아요 (${entry.name})")
        return data
    }

    companion object {
        private const val SIG_LOCAL = 0x04034b50
        private const val SIG_CENTRAL = 0x02014b50
        private const val SIG_EOCD = 0x06054b50
        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8
        private const val FLAG_ENCRYPTED = 0x1
        private const val FLAG_DATA_DESCRIPTOR = 0x8

        fun open(bytes: ByteArray): ZipArchive {
            val eocd = findEocd(bytes)
            val count = u16(bytes, eocd + 10)
            val cdOffset = u32(bytes, eocd + 16)
            if (cdOffset == -1 || count == 0xFFFF) throw ZipFormatException("zip64 문서는 지원하지 않아요")
            val entries = ArrayList<Entry>(count)
            var p = cdOffset
            repeat(count) {
                if (p + 46 > bytes.size || u32(bytes, p) != SIG_CENTRAL) throw ZipFormatException("zip 목록이 올바르지 않아요")
                val flags = u16(bytes, p + 8)
                if (flags and FLAG_ENCRYPTED != 0) throw ZipFormatException("암호화된 문서는 읽을 수 없어요")
                val nameLen = u16(bytes, p + 28)
                val extraLen = u16(bytes, p + 30)
                val commentLen = u16(bytes, p + 32)
                val nameBytes = bytes.copyOfRange(p + 46, p + 46 + nameLen)
                entries += Entry(
                    name = nameBytes.decodeToString(),
                    method = u16(bytes, p + 10),
                    flags = flags,
                    modTime = u16(bytes, p + 12),
                    modDate = u16(bytes, p + 14),
                    crc = u32(bytes, p + 16),
                    compressedSize = u32(bytes, p + 20),
                    size = u32(bytes, p + 24),
                    localHeaderOffset = u32(bytes, p + 42),
                    nameBytes = nameBytes,
                )
                p += 46 + nameLen + extraLen + commentLen
            }
            return ZipArchive(bytes, entries)
        }

        private fun findEocd(bytes: ByteArray): Int {
            // EOCD(22바이트) 뒤에 최대 64KB 주석이 올 수 있다.
            val min = maxOf(0, bytes.size - 22 - 0xFFFF)
            for (i in bytes.size - 22 downTo min) {
                if (u32(bytes, i) == SIG_EOCD) return i
            }
            throw ZipFormatException("zip 문서가 아니에요")
        }

        private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

        private fun u32(b: ByteArray, at: Int): Int = u16(b, at) or (u16(b, at + 2) shl 16)
    }
}

internal class ZipFormatException(message: String) : Exception(message)

private class ByteSink(capacity: Int) {
    private var buf = ByteArray(maxOf(capacity, 64))
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buf.size) return
        buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
    }

    fun u16(v: Int) {
        ensure(2)
        buf[size++] = v.toByte()
        buf[size++] = (v shr 8).toByte()
    }

    fun u32(v: Int) {
        u16(v and 0xFFFF)
        u16(v ushr 16)
    }

    fun bytes(b: ByteArray) {
        ensure(b.size)
        b.copyInto(buf, size)
        size += b.size
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

internal fun crc32Of(data: ByteArray): Int {
    if (data.isEmpty()) return 0
    return data.usePinned { crc32(0.convert(), it.addressOf(0).reinterpret<UByteVar>(), data.size.convert()) }.toInt()
}

/** raw deflate(zip 항목 형식, 헤더 없음) 압축 풀기. */
internal fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray {
    val output = ByteArray(maxOf(expectedSize, 1))
    if (input.isEmpty()) return ByteArray(0)
    memScoped {
        val stream = alloc<z_stream>()
        val init = inflateInit2_(stream.ptr, -15, zlibVersion()?.toKString(), sizeOf<z_stream>().convert())
        if (init != Z_OK) throw ZipFormatException("압축을 풀 준비를 하지 못했어요 ($init)")
        try {
            input.usePinned { inPin ->
                output.usePinned { outPin ->
                    stream.next_in = inPin.addressOf(0).reinterpret()
                    stream.avail_in = input.size.convert()
                    stream.next_out = outPin.addressOf(0).reinterpret()
                    stream.avail_out = output.size.convert()
                    val result = inflate(stream.ptr, Z_FINISH)
                    if (result != Z_STREAM_END && !(result == Z_BUF_ERROR && expectedSize == 0)) {
                        throw ZipFormatException("압축을 풀지 못했어요 ($result)")
                    }
                }
            }
            val produced = stream.total_out.toInt()
            return if (produced == output.size) output else output.copyOf(produced)
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}

/** raw deflate(zip 항목 형식, 헤더 없음) 압축. */
internal fun deflateRaw(input: ByteArray): ByteArray {
    memScoped {
        val stream = alloc<z_stream>()
        val init = deflateInit2_(
            stream.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            -15,
            8,
            Z_DEFAULT_STRATEGY,
            zlibVersion()?.toKString(),
            sizeOf<z_stream>().convert(),
        )
        if (init != Z_OK) throw ZipFormatException("압축할 준비를 하지 못했어요 ($init)")
        try {
            val output = ByteArray(deflateBound(stream.ptr, input.size.convert()).toInt() + 16)
            val source = if (input.isEmpty()) ByteArray(1) else input
            source.usePinned { inPin ->
                output.usePinned { outPin ->
                    stream.next_in = inPin.addressOf(0).reinterpret()
                    stream.avail_in = input.size.convert()
                    stream.next_out = outPin.addressOf(0).reinterpret()
                    stream.avail_out = output.size.convert()
                    val result = deflate(stream.ptr, Z_FINISH)
                    if (result != Z_STREAM_END) throw ZipFormatException("압축하지 못했어요 ($result)")
                }
            }
            return output.copyOf(stream.total_out.toInt())
        } finally {
            deflateEnd(stream.ptr)
        }
    }
}
