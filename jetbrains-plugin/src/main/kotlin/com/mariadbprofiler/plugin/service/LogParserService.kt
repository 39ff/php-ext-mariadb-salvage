package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.QueryEntry
import kotlinx.serialization.json.Json
import java.io.File

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
            log.warn("Cannot read JSONL file: $filePath")
            return emptyList()
        }

        val entries = mutableListOf<QueryEntry>()
        file.useLines { lines ->
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        entries.add(json.decodeFromString<QueryEntry>(trimmed))
                    } catch (e: Exception) {
                        log.debug("Failed to parse line $index in $filePath: ${e.message}")
                    }
                }
            }
        }
        return entries
    }

    fun parseJsonlFileFromOffset(filePath: String, offset: Long): Pair<List<QueryEntry>, Long> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return Pair(emptyList(), 0L)
        }

        val entries = mutableListOf<QueryEntry>()
        val raf = java.io.RandomAccessFile(file, "r")
        try {
            raf.seek(offset)
            var line = raf.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        entries.add(json.decodeFromString<QueryEntry>(trimmed))
                    } catch (e: Exception) {
                        log.debug("Failed to parse incremental line: ${e.message}")
                    }
                }
                line = raf.readLine()
            }
            return Pair(entries, raf.filePointer)
        } finally {
            raf.close()
        }
    }

    fun readRawLog(filePath: String, maxLines: Int = 500): List<String> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return emptyList()
        }

        val lines = file.readLines()
        return if (lines.size > maxLines) lines.takeLast(maxLines) else lines
    }

    fun tailRawLog(filePath: String, fromOffset: Long): Pair<List<String>, Long> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return Pair(emptyList(), 0L)
        }

        val raf = java.io.RandomAccessFile(file, "r")
        try {
            raf.seek(fromOffset)
            val lines = mutableListOf<String>()
            var line = raf.readLine()
            while (line != null) {
                if (line.isNotEmpty()) {
                    lines.add(line)
                }
                line = raf.readLine()
            }
            return Pair(lines, raf.filePointer)
        } finally {
            raf.close()
        }
    }
}
