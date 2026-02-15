package com.mariadbprofiler.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.JobInfo
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.service.JobManagerService
import com.mariadbprofiler.plugin.service.LogParserService
import com.mariadbprofiler.plugin.service.StatisticsService
import com.mariadbprofiler.plugin.ui.panel.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Timer
import java.util.TimerTask
import javax.swing.*

class ProfilerToolWindow(private val project: Project) : JPanel(BorderLayout()) {

    private val jobListPanel = JobListPanel(::onJobSelected)
    private val queryLogPanel = QueryLogPanel(::onQuerySelected)
    private val queryDetailPanel = QueryDetailPanel(project)
    private val statisticsPanel = StatisticsPanel()
    private val liveTailPanel = LiveTailPanel(project)

    private var refreshTimer: Timer? = null
    private var currentJob: JobInfo? = null
    private var currentEntries: List<QueryEntry> = emptyList()

    init {
        setupUI()
        startRefreshTimer()
    }

    private fun setupUI() {
        // Left panel: Job list
        val leftPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(200, 0)
            minimumSize = Dimension(150, 0)
            add(jobListPanel, BorderLayout.CENTER)

            // Refresh button at bottom
            val refreshBtn = JButton("Refresh").apply {
                addActionListener { refreshJobs() }
            }
            add(refreshBtn, BorderLayout.SOUTH)
        }

        // Right panel: Tabs
        val tabbedPane = JTabbedPane().apply {
            // Query Log tab (split: table on top, detail on bottom)
            val queryLogSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
                topComponent = queryLogPanel
                bottomComponent = queryDetailPanel
                resizeWeight = 0.6
                dividerLocation = 300
            }
            addTab("Query Log", queryLogSplit)

            // Statistics tab
            addTab("Statistics", statisticsPanel)

            // Live Tail tab
            addTab("Live Tail", liveTailPanel)
        }

        // Main split: left (jobs) | right (tabs)
        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            leftComponent = leftPanel
            rightComponent = tabbedPane
            dividerLocation = 200
            resizeWeight = 0.0
        }

        add(mainSplit, BorderLayout.CENTER)
    }

    private fun onJobSelected(job: JobInfo?) {
        currentJob = job
        if (job == null) {
            queryLogPanel.clear()
            queryDetailPanel.showEntry(null)
            statisticsPanel.clear()
            liveTailPanel.setJobKey(null)
            return
        }

        // Load queries for selected job
        val logParser = project.getService(LogParserService::class.java)
        val jobManager = project.getService(JobManagerService::class.java)
        val jsonlPath = jobManager.getJsonlPath(job.key)

        currentEntries = logParser.parseJsonlFile(jsonlPath)
        queryLogPanel.setEntries(currentEntries)
        queryDetailPanel.showEntry(null)

        // Update statistics
        val statsService = project.getService(StatisticsService::class.java)
        val stats = statsService.computeStats(currentEntries)
        statisticsPanel.updateStats(stats)

        // Set live tail job
        liveTailPanel.setJobKey(job.key)
    }

    private fun onQuerySelected(entry: QueryEntry?) {
        queryDetailPanel.showEntry(entry)
    }

    fun refreshJobs() {
        val jobManager = project.getService(JobManagerService::class.java)
        val jobs = jobManager.loadJobs()
        SwingUtilities.invokeLater {
            jobListPanel.setJobs(jobs)
        }
    }

    private fun startRefreshTimer() {
        refreshTimer = Timer("MariaDB-Profiler-Refresh", true)
        refreshTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                refreshJobs()
            }
        }, 0, 5000)
    }

    fun dispose() {
        refreshTimer?.cancel()
        refreshTimer = null
    }
}
