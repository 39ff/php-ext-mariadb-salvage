package com.mariadbprofiler.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.mariadbprofiler.plugin.service.JobManagerService
import com.mariadbprofiler.plugin.settings.ProfilerState
import java.io.File

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
            val selected = Messages.showEditableChooseDialog(
                "Select a job to stop:",
                "Stop Profiling Job",
                Messages.getQuestionIcon(),
                keys,
                keys.first(),
                null
            ) ?: return
            selected
        }

        val phpPath = state.phpPath
        val cliPath = state.cliScriptPath.ifEmpty {
            val projectBase = project.basePath ?: ""
            val candidate = File(projectBase, "cli/mariadb_profiler.php")
            if (candidate.exists()) candidate.absolutePath else ""
        }

        if (cliPath.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "CLI script path not configured.",
                "MariaDB Profiler"
            )
            return
        }

        try {
            val process = ProcessBuilder(phpPath, cliPath, "job", "end", jobKey)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Messages.showInfoMessage(
                    project,
                    "Profiling job stopped: $jobKey\n$output",
                    "MariaDB Profiler"
                )
            } else {
                Messages.showErrorDialog(
                    project,
                    "Failed to stop job (exit code $exitCode):\n$output",
                    "MariaDB Profiler"
                )
            }
        } catch (ex: Exception) {
            log.error("Failed to stop profiling job", ex)
            Messages.showErrorDialog(project, "Error: ${ex.message}", "MariaDB Profiler")
        }
    }
}
