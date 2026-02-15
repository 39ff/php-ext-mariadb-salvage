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

    private val countLabel = JBLabel("0 queries").apply {
        foreground = JBColor.GRAY
    }

    private val clearButton = JButton("Clear")

    private var isMonitoring = false
    private var currentJobKey: String? = null
    private var tailOffset: Long = 0
    private var queryCount: Int = 0
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
        toolbar.add(Box.createHorizontalStrut(12))
        toolbar.add(countLabel)
        add(toolbar, BorderLayout.NORTH)

        // Log area
        add(JBScrollPane(logArea), BorderLayout.CENTER)

        // Button handlers
        clearButton.addActionListener {
            logArea.text = ""
            queryCount = 0
            countLabel.text = "0 queries"
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

        val jsonlPath = jobManager.getJsonlPath(jobKey)
        tailOffset = 0
        queryCount = 0
        logArea.text = ""

        // Load existing content
        val (existingEntries, initialOffset) = logParser.parseJsonlFileFromOffset(jsonlPath, 0)
        if (existingEntries.isNotEmpty()) {
            val sb = StringBuilder()
            for (entry in existingEntries) {
                sb.append(formatQueryEntry(entry))
            }
            logArea.text = sb.toString()
            queryCount = existingEntries.size
            countLabel.text = "$queryCount queries"
            tailOffset = initialOffset
            logArea.caretPosition = logArea.document.length
        }

        // Watch the JSONL file for changes
        fileWatcher.watchFile(jsonlPath) {
            SwingUtilities.invokeLater {
                val (newEntries, newOffset) = logParser.parseJsonlFileFromOffset(jsonlPath, tailOffset)
                if (newEntries.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (entry in newEntries) {
                        sb.append(formatQueryEntry(entry))
                    }
                    logArea.append(sb.toString())
                    tailOffset = newOffset
                    queryCount += newEntries.size
                    countLabel.text = "$queryCount queries"

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

        fileWatcher.unwatchFile(jobManager.getJsonlPath(jobKey))

        isMonitoring = false
        statusLabel.text = "Stopped"
        statusLabel.foreground = JBColor.GRAY
    }

    private fun formatQueryEntry(entry: com.mariadbprofiler.plugin.model.QueryEntry): String {
        val ts = entry.formattedTimestamp
        val type = entry.queryType.label.padEnd(7)
        val tag = if (entry.tags.isNotEmpty()) " [${entry.tags.joinToString(",")}]" else ""
        val src = if (entry.sourceFile.isNotEmpty()) "  <- ${entry.sourceFile}" else ""
        return "$ts  $type  ${entry.query}$tag$src\n"
    }
}
