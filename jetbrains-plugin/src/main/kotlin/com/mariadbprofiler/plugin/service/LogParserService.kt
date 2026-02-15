package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.QueryEntry
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
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
            return Pair(emptyList(), offset)
        }

        val entries = mutableListOf<QueryEntry>()
        val fis = FileInputStream(file)
        try {
            val channel = fis.channel
            channel.position(offset)
            val reader = BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8))
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        entries.add(json.decodeFromString<QueryEntry>(trimmed))
                    } catch (e: Exception) {
                        log.debug("Failed to parse incremental line: ${e.message}")
                    }
                }
            }
            return Pair(entries, channel.position())
        } finally {
            fis.close()
        }
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

    fun tailRawLog(filePath: String, fromOffset: Long): Pair<List<String>, Long> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return Pair(emptyList(), fromOffset)
        }

        val fis = FileInputStream(file)
        try {
            val channel = fis.channel
            channel.position(fromOffset)
            val reader = BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8))
            val lines = mutableListOf<String>()
            reader.forEachLine { line ->
                if (line.isNotEmpty()) {
                    lines.add(line)
                }
            }
            return Pair(lines, channel.position())
        } finally {
            fis.close()
        }
    }
}
