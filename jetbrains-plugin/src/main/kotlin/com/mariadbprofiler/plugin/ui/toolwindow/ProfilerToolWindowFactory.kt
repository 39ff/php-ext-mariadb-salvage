package com.mariadbprofiler.plugin.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ProfilerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val profilerWindow = ProfilerToolWindow(project)

        val content = ContentFactory.getInstance().createContent(
            profilerWindow,
            "MariaDB Profiler",
            false
        )

        toolWindow.contentManager.addContent(content)

        // Cleanup on dispose
        toolWindow.contentManager.addContentManagerListener(
            object : com.intellij.ui.content.ContentManagerListener {
                override fun contentRemoved(event: com.intellij.ui.content.ContentManagerEvent) {
                    profilerWindow.dispose()
                }
            }
        )
    }
}
