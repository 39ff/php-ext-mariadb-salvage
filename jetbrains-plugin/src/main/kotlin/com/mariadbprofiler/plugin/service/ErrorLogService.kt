package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class ErrorLogService(private val project: Project) {

    data class ErrorEntry(
        val timestamp: LocalDateTime,
        val source: String,
        val message: String,
        val level: Level
    ) {
        val formattedTimestamp: String
            get() = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }

    enum class Level { INFO, WARN, ERROR }

    private val entries = mutableListOf<ErrorEntry>()
    private val listeners = mutableListOf<() -> Unit>()

    fun addError(source: String, message: String) {
        add(Level.ERROR, source, message)
    }

    fun addWarning(source: String, message: String) {
        add(Level.WARN, source, message)
    }

    fun addInfo(source: String, message: String) {
        add(Level.INFO, source, message)
    }

    private fun add(level: Level, source: String, message: String) {
        synchronized(entries) {
            entries.add(ErrorEntry(LocalDateTime.now(), source, message, level))
            if (entries.size > 500) {
                entries.removeAt(0)
            }
        }
        listeners.forEach { it() }
    }

    fun getEntries(): List<ErrorEntry> {
        synchronized(entries) {
            return entries.toList()
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
        listeners.forEach { it() }
    }

    fun getErrorCount(): Int {
        synchronized(entries) {
            return entries.count { it.level == Level.ERROR }
        }
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
