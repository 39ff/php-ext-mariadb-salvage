package com.mariadbprofiler.plugin.ui.panel

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.mariadbprofiler.plugin.service.FileWatcherService
import com.mariadbprofiler.plugin.service.JobManagerService
import com.mariadbprofiler.plugin.service.LogParserService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*

class LiveTailPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val logArea = JTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, 12).let { f ->
            if (f.family == "JetBrains Mono") f else Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        lineWrap = false
    }

    private val statusLabel = JBLabel("Stopped").apply {
        foreground = JBColor.GRAY
    }

    private val clearButton = JButton("Clear")

    private var isMonitoring = false
    private var currentJobKey: String? = null
    private var tailOffset: Long = 0
    private val maxLines = 500

    init {
        setupUI()
    }

    private fun setupUI() {
        // Toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        toolbar.add(clearButton)
        toolbar.add(Box.createHorizontalStrut(12))
        toolbar.add(statusLabel)
        add(toolbar, BorderLayout.NORTH)

        // Log area
        add(JBScrollPane(logArea), BorderLayout.CENTER)

        // Button handlers
        clearButton.addActionListener {
            logArea.text = ""
        }
    }

    fun setJobKey(jobKey: String?) {
        if (isMonitoring) {
            stopMonitoring()
        }
        currentJobKey = jobKey
        if (jobKey != null) {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        val jobKey = currentJobKey ?: return
        val logParser = project.getService(LogParserService::class.java)
        val fileWatcher = project.getService(FileWatcherService::class.java)
        val jobManager = project.getService(JobManagerService::class.java)

        val rawLogPath = jobManager.getRawLogPath(jobKey)
        tailOffset = 0
        logArea.text = ""

        // Load existing content
        val existingLines = logParser.readRawLog(rawLogPath, maxLines)
        if (existingLines.isNotEmpty()) {
            logArea.text = existingLines.joinToString("\n") + "\n"
            val file = java.io.File(rawLogPath)
            if (file.exists()) tailOffset = file.length()
        }

        // Watch for changes
        fileWatcher.watchFile(rawLogPath) {
            SwingUtilities.invokeLater {
                val (newLines, newOffset) = logParser.tailRawLog(rawLogPath, tailOffset)
                if (newLines.isNotEmpty()) {
                    logArea.append(newLines.joinToString("\n") + "\n")
                    tailOffset = newOffset

                    // Trim if too many lines
                    val lineCount = logArea.lineCount
                    if (lineCount > maxLines) {
                        val removeEnd = logArea.getLineEndOffset(lineCount - maxLines)
                        logArea.replaceRange("", 0, removeEnd)
                    }

                    // Auto-scroll
                    logArea.caretPosition = logArea.document.length
                }
            }
        }

        isMonitoring = true
        statusLabel.text = "Watching: $jobKey"
        statusLabel.foreground = JBColor(0x2E7D32, 0x81C784)
    }

    private fun stopMonitoring() {
        val jobKey = currentJobKey ?: return
        val fileWatcher = project.getService(FileWatcherService::class.java)
        val jobManager = project.getService(JobManagerService::class.java)

        fileWatcher.unwatchFile(jobManager.getRawLogPath(jobKey))

        isMonitoring = false
        statusLabel.text = "Stopped"
        statusLabel.foreground = JBColor.GRAY
    }
}
