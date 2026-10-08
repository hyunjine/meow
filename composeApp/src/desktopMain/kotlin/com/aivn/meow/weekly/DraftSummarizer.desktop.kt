package com.aivn.meow.weekly

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

actual fun defaultDraftSummarizer(): DraftSummarizer = ClaudeCliSummarizer()

/**
 * 로컬 Claude Code CLI 를 headless 로 실행해 요약한다(`claude -p --model haiku --output-format text`).
 * CLI 가 없거나 실패 · [timeoutSeconds] 초과면 null.
 */
class ClaudeCliSummarizer(private val timeoutSeconds: Long = 60) : DraftSummarizer {
    override suspend fun summarize(prompt: String): String? = withContext(Dispatchers.IO) {
        val executable = findClaude() ?: return@withContext null
        val process = try {
            ProcessBuilder(executable, "-p", "--model", "haiku", "--output-format", "text", prompt)
                // 프로젝트 설정(CLAUDE.md 등)을 읽지 않도록 임시 폴더에서 실행한다.
                .directory(File(System.getProperty("java.io.tmpdir")))
                .redirectErrorStream(false)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            process.outputStream.close()
            // 출력이 파이프 버퍼를 채워 멈추지 않도록 따로 읽는다.
            var output = ""
            val reader = Thread { output = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("") }
            reader.isDaemon = true
            reader.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext null
            }
            reader.join(2_000)
            if (process.exitValue() != 0) null else output.ifBlank { null }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            null
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun findClaude(): String? {
        val home = System.getProperty("user.home")
        val candidates = buildList {
            add(File(home, ".local/bin/claude"))
            System.getenv("PATH")?.split(File.pathSeparator)?.forEach { add(File(it, "claude")) }
            add(File("/opt/homebrew/bin/claude"))
            add(File("/usr/local/bin/claude"))
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }
}
