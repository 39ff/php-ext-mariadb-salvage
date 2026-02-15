package com.mariadbprofiler.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.mariadbprofiler.plugin.settings.ProfilerState
import java.io.File

class StartJobAction : AnAction() {

    private val log = Logger.getInstance(StartJobAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = service<ProfilerState>()

        val phpPath = state.phpPath
        val cliPath = state.cliScriptPath.ifEmpty {
            // Try to auto-detect from project
            val projectBase = project.basePath ?: ""
            val candidate = File(projectBase, "cli/mariadb_profiler.php")
            if (candidate.exists()) candidate.absolutePath else ""
        }

        if (cliPath.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "CLI script path not configured. Please set it in Settings > Tools > MariaDB Profiler.",
                "MariaDB Profiler"
            )
            return
        }

        try {
            val process = ProcessBuilder(phpPath, cliPath, "job", "start")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Messages.showInfoMessage(
                    project,
                    "Profiling job started.\n$output",
                    "MariaDB Profiler"
                )
            } else {
                Messages.showErrorDialog(
                    project,
                    "Failed to start job (exit code $exitCode):\n$output",
                    "MariaDB Profiler"
                )
            }
        } catch (ex: Exception) {
            log.error("Failed to start profiling job", ex)
            Messages.showErrorDialog(
                project,
                "Error: ${ex.message}",
                "MariaDB Profiler"
            )
        }
    }
}
