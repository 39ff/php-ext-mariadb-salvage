package com.mariadbprofiler.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.mariadbprofiler.plugin.service.ErrorLogService
import com.mariadbprofiler.plugin.service.JobManagerService
import com.mariadbprofiler.plugin.settings.ProfilerState
import java.io.File
import java.util.concurrent.TimeUnit

class StopJobAction : AnAction() {

    private val log = Logger.getInstance(StopJobAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = service<ProfilerState>()
        val jobManager = project.getService(JobManagerService::class.java)

        val activeJobs = jobManager.getActiveJobs()
        if (activeJobs.isEmpty()) {
            Messages.showInfoMessage(project, "No active profiling jobs.", "MariaDB Profiler")
            return
        }

        // If multiple active jobs, let user choose
        val jobKey = if (activeJobs.size == 1) {
            activeJobs.first().key
        } else {
            val keys = activeJobs.map { it.key }.toTypedArray()
            val selected = Messages.showChooseDialog(
                "Select a job to stop:",
                "Stop Profiling Job",
                keys,
                keys.first(),
                Messages.getQuestionIcon()
            )
            if (selected < 0) return
            keys[selected]
        }

        val phpPath = state.phpPath
        val cliPath = state.cliScriptPath.ifEmpty {
            val projectBase = project.basePath ?: ""
            val candidate = File(projectBase, "cli/mariadb_profiler.php")
            if (candidate.exists()) candidate.absolutePath else ""
        }

        if (cliPath.isEmpty()) {
            val errorLog = project.getService(ErrorLogService::class.java)
            errorLog.addError("StopJob", "CLI script path not configured")
            Messages.showErrorDialog(
                project,
                "CLI script path not configured. Please set it in the Settings tab of MariaDB Profiler.",
                "MariaDB Profiler"
            )
            return
        }

        object : Task.Backgroundable(project, "Stopping Profiling Job", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val process = ProcessBuilder(phpPath, cliPath, "job", "end", jobKey)
                        .redirectErrorStream(true)
                        .start()

                    val output = process.inputStream.bufferedReader().readText()
                    val completed = process.waitFor(60, TimeUnit.SECONDS)

                    val errorLog = project.getService(ErrorLogService::class.java)

                    if (!completed) {
                        process.destroyForcibly()
                        errorLog.addError("StopJob", "Process timed out after 60 seconds")
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Process timed out after 60 seconds.",
                                "MariaDB Profiler"
                            )
                        }
                        return
                    }

                    val exitCode = process.exitValue()
                    ApplicationManager.getApplication().invokeLater {
                        if (exitCode == 0) {
                            errorLog.addInfo("StopJob", "Profiling job stopped: $jobKey")
                            Messages.showInfoMessage(
                                project,
                                "Profiling job stopped: $jobKey\n$output",
                                "MariaDB Profiler"
                            )
                        } else {
                            errorLog.addError("StopJob", "Failed to stop job (exit code $exitCode): $output")
                            Messages.showErrorDialog(
                                project,
                                "Failed to stop job (exit code $exitCode):\n$output",
                                "MariaDB Profiler"
                            )
                        }
                    }
                } catch (ex: Exception) {
                    log.error("Failed to stop profiling job", ex)
                    val errorLog = project.getService(ErrorLogService::class.java)
                    errorLog.addError("StopJob", "Exception: ${ex.message}")
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Error: ${ex.message}", "MariaDB Profiler")
                    }
                }
            }
        }.queue()
    }
}
