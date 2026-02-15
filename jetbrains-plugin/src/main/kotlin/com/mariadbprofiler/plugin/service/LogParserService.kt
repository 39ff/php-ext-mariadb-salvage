package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.QueryEntry
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class LogParserService(private val project: Project) {

    private val log = Logger.getInstance(LogParserService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseJsonlFile(filePath: String): List<QueryEntry> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return emptyList()
        }

        val entries = mutableListOf<QueryEntry>()
        var parseErrors = 0
        file.useLines { lines ->
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        entries.add(json.decodeFromString<QueryEntry>(trimmed))
                    } catch (e: Exception) {
                        log.debug("Failed to parse line $index in $filePath: ${e.message}")
                        parseErrors++
                    }
                }
            }
        }
        if (parseErrors > 0) {
            val errorLog = project.getService(ErrorLogService::class.java)
            errorLog.addWarning("LogParser", "$parseErrors line(s) failed to parse in ${file.name}")
        }
        return entries
    }

    /**
     * Read JSONL entries starting from byte offset.
     * Returns (entries, newOffset) where newOffset is the file size at read time.
     */
    fun parseJsonlFileFromOffset(filePath: String, offset: Long): Pair<List<QueryEntry>, Long> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return Pair(emptyList(), offset)
        }

        val fileSize = file.length()
        if (fileSize <= offset) {
            return Pair(emptyList(), offset)
        }

        val entries = mutableListOf<QueryEntry>()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val bytes = ByteArray((fileSize - offset).toInt())
            raf.readFully(bytes)
            val content = String(bytes, StandardCharsets.UTF_8)
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        entries.add(json.decodeFromString<QueryEntry>(trimmed))
                    } catch (e: Exception) {
                        log.debug("Failed to parse incremental line: ${e.message}")
                    }
                }
            }
        }
        return Pair(entries, fileSize)
    }

    fun readRawLog(filePath: String, maxLines: Int = 500): List<String> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return emptyList()
        }

        val buffer = ArrayDeque<String>(maxLines)
        file.useLines { lines ->
            lines.forEach { line ->
                if (buffer.size >= maxLines) {
                    buffer.removeFirst()
                }
                buffer.addLast(line)
            }
        }
        return buffer.toList()
    }

    /**
     * Read raw log lines starting from byte offset.
     * Returns (lines, newOffset) where newOffset is the file size at read time.
     */
    fun tailRawLog(filePath: String, fromOffset: Long): Pair<List<String>, Long> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return Pair(emptyList(), fromOffset)
        }

        val fileSize = file.length()
        if (fileSize <= fromOffset) {
            return Pair(emptyList(), fromOffset)
        }

        val lines = mutableListOf<String>()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(fromOffset)
            val bytes = ByteArray((fileSize - fromOffset).toInt())
            raf.readFully(bytes)
            val content = String(bytes, StandardCharsets.UTF_8)
            for (line in content.lines()) {
                if (line.isNotEmpty()) {
                    lines.add(line)
                }
            }
        }
        return Pair(lines, fileSize)
    }
}
