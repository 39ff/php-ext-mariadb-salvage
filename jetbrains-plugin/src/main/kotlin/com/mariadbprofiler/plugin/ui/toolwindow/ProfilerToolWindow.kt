package com.mariadbprofiler.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.mariadbprofiler.plugin.model.JobInfo
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.service.ErrorLogService
import com.mariadbprofiler.plugin.service.JobManagerService
import com.mariadbprofiler.plugin.service.LogParserService
import com.mariadbprofiler.plugin.service.StatisticsService
import com.mariadbprofiler.plugin.settings.ProfilerState
import com.mariadbprofiler.plugin.ui.panel.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Timer
import java.util.TimerTask
import javax.swing.*

class ProfilerToolWindow(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val log = Logger.getInstance(ProfilerToolWindow::class.java)

    private val jobListPanel = JobListPanel(::onJobSelected, ::onStartJob, ::onStopJob)
    private val queryLogPanel = QueryLogPanel(::onQuerySelected)
    private val queryDetailPanel = QueryDetailPanel(project)
    private val statisticsPanel = StatisticsPanel()
    private val liveTailPanel = LiveTailPanel(project)
    private val settingsPanel = SettingsPanel(project)
    private val errorLogPanel = ErrorLogPanel(project)

    private var tabbedPane: JTabbedPane? = null
    private var refreshTimer: Timer? = null
    private var currentJob: JobInfo? = null
    private var currentEntries: List<QueryEntry> = emptyList()
    private val liveTailTabIndex: Int get() = 2
    private val errorTabIndex: Int get() = 4 // Settings=3, Errors=4

    private val errorChangeListener: () -> Unit = { updateErrorTabTitle() }

    init {
        setupUI()
        startRefreshTimer()

        val errorLog = project.getService(ErrorLogService::class.java)
        errorLog.addChangeListener(errorChangeListener)
    }

    private fun setupUI() {
        // Left panel: Job list
        val leftPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(200, 0)
            minimumSize = Dimension(150, 0)
            add(jobListPanel, BorderLayout.CENTER)
        }

        // Right panel: Tabs
        tabbedPane = JTabbedPane().apply {
            // Query Log tab
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

            // Settings tab
            addTab("Settings", settingsPanel)

            // Errors tab
            addTab("Errors", errorLogPanel)
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

        try {
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
        } catch (e: Exception) {
            val errorLog = project.getService(ErrorLogService::class.java)
            errorLog.addError("ProfilerWindow", "Failed to load job ${job.key}: ${e.message}")
        }
    }

    private fun onQuerySelected(entry: QueryEntry?) {
        queryDetailPanel.showEntry(entry)
    }

    private fun onStartJob() {
        val jobManager = project.getService(JobManagerService::class.java)
        val errorLog = project.getService(ErrorLogService::class.java)
        jobListPanel.setStartEnabled(false)

        object : Task.Backgroundable(project, "Starting Profiling Session", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val jobKey = jobManager.startJob()
                    errorLog.addInfo("StartJob", "Session started: $jobKey")

                    ApplicationManager.getApplication().invokeLater {
                        jobListPanel.setStartEnabled(true)
                        refreshJobs()
                        jobListPanel.selectJobByKey(jobKey)
                        tabbedPane?.selectedIndex = liveTailTabIndex
                    }
                } catch (ex: Exception) {
                    log.error("Failed to start profiling session", ex)
                    errorLog.addError("StartJob", "Exception: ${ex.message}")
                    ApplicationManager.getApplication().invokeLater {
                        jobListPanel.setStartEnabled(true)
                        Messages.showErrorDialog(project, "Error: ${ex.message}", "MariaDB Profiler")
                    }
                }
            }
        }.queue()
    }

    private fun onStopJob(job: JobInfo) {
        val jobManager = project.getService(JobManagerService::class.java)
        val errorLog = project.getService(ErrorLogService::class.java)

        object : Task.Backgroundable(project, "Stopping Profiling Session", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val queryCount = jobManager.stopJob(job.key)
                    errorLog.addInfo("StopJob", "Session stopped: ${job.key} ($queryCount queries)")

                    ApplicationManager.getApplication().invokeLater {
                        refreshJobs()
                    }
                } catch (ex: Exception) {
                    log.error("Failed to stop profiling session", ex)
                    errorLog.addError("StopJob", "Exception: ${ex.message}")
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Error: ${ex.message}", "MariaDB Profiler")
                    }
                }
            }
        }.queue()
    }

    fun refreshJobs() {
        val jobManager = project.getService(JobManagerService::class.java)
        try {
            val jobs = jobManager.loadJobs()
            SwingUtilities.invokeLater {
                jobListPanel.setJobs(jobs)
            }
        } catch (e: Exception) {
            val errorLog = project.getService(ErrorLogService::class.java)
            errorLog.addError("JobManager", "Failed to load jobs: ${e.message}")
        }
    }

    private fun updateErrorTabTitle() {
        SwingUtilities.invokeLater {
            val errorLog = project.getService(ErrorLogService::class.java)
            val count = errorLog.getErrorCount()
            val title = if (count > 0) "Errors ($count)" else "Errors"
            tabbedPane?.setTitleAt(errorTabIndex, title)
        }
    }

    private fun startRefreshTimer() {
        val state = service<ProfilerState>()
        val intervalMs = (state.refreshInterval.coerceAtLeast(1) * 1000).toLong()
        refreshTimer = Timer("MariaDB-Profiler-Refresh", true)
        refreshTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                refreshJobs()
            }
        }, 0, intervalMs)
    }

    override fun dispose() {
        refreshTimer?.cancel()
        refreshTimer = null
        errorLogPanel.removeListeners()
        val errorLog = project.getService(ErrorLogService::class.java)
        errorLog.removeChangeListener(errorChangeListener)
    }
}
