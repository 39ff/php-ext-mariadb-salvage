package com.mariadbprofiler.plugin.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class FileWatcherService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(FileWatcherService::class.java)

    private var timer: Timer? = null
    private val timerLock = Any()
    private val watchers = ConcurrentHashMap<String, FileWatcher>()

    data class FileWatcher(
        val filePath: String,
        var lastModified: Long = 0,
        var lastSize: Long = 0,
        val onChange: (File) -> Unit
    )

    fun watchFile(filePath: String, onChange: (File) -> Unit) {
        val file = File(filePath)
        watchers[filePath] = FileWatcher(
            filePath = filePath,
            lastModified = if (file.exists()) file.lastModified() else 0,
            lastSize = if (file.exists()) file.length() else 0,
            onChange = onChange
        )
        ensureTimerRunning()
    }

    fun unwatchFile(filePath: String) {
        watchers.remove(filePath)
        if (watchers.isEmpty()) {
            stopTimer()
        }
    }

    fun unwatchAll() {
        watchers.clear()
        stopTimer()
    }

    private fun ensureTimerRunning() {
        synchronized(timerLock) {
            if (timer == null) {
                timer = Timer("MariaDB-Profiler-FileWatcher", true)
                timer?.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        checkFiles()
                    }
                }, 1000, 1000)
            }
        }
    }

    private fun stopTimer() {
        synchronized(timerLock) {
            timer?.cancel()
            timer = null
        }
    }

    private fun checkFiles() {
        for ((_, watcher) in watchers) {
            try {
                val file = File(watcher.filePath)
                if (file.exists()) {
                    val modified = file.lastModified()
                    val size = file.length()
                    if (modified != watcher.lastModified || size != watcher.lastSize) {
                        watcher.lastModified = modified
                        watcher.lastSize = size
                        watcher.onChange(file)
                    }
                }
            } catch (e: Exception) {
                log.debug("Error checking file ${watcher.filePath}: ${e.message}")
            }
        }
    }

    override fun dispose() {
        unwatchAll()
    }
}
