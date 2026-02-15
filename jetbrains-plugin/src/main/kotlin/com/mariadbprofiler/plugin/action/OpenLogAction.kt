package com.mariadbprofiler.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.mariadbprofiler.plugin.service.JobManagerService

class OpenLogAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val jobManager = project.getService(JobManagerService::class.java)

        val logDir = LocalFileSystem.getInstance().findFileByPath(jobManager.getLogDirectory())

        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("jsonl")
            .withTitle("Open Profiler Log File")
            .withDescription("Select a .jsonl log file to view")

        if (logDir != null) {
            descriptor.withRoots(logDir)
        }

        val chosen = FileChooser.chooseFile(descriptor, project, logDir) ?: return
        FileEditorManager.getInstance(project).openFile(chosen, true)
    }
}
