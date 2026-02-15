package com.mariadbprofiler.plugin.ui.panel

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.mariadbprofiler.plugin.model.JobInfo
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

class JobListPanel(
    private val onJobSelected: (JobInfo?) -> Unit
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<JobInfo>()
    private val jobList = JBList(listModel)

    init {
        setupUI()
    }

    private fun setupUI() {
        // Header
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JBLabel("Jobs"))
        }
        add(header, BorderLayout.NORTH)

        // Job list
        jobList.apply {
            cellRenderer = JobCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    onJobSelected(selectedValue)
                }
            }
        }
        add(JBScrollPane(jobList), BorderLayout.CENTER)
    }

    fun setJobs(jobs: List<JobInfo>) {
        val selected = jobList.selectedValue
        listModel.clear()
        jobs.forEach { listModel.addElement(it) }

        // Re-select previous job if still exists
        if (selected != null) {
            for (i in 0 until listModel.size) {
                if (listModel.getElementAt(i).key == selected.key) {
                    jobList.selectedIndex = i
                    break
                }
            }
        }
    }

    fun getSelectedJob(): JobInfo? = jobList.selectedValue

    private class JobCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            val job = value as? JobInfo ?: return this
            val statusIcon = if (job.isActive) "\u25CF" else "\u25CB" // ● or ○
            val statusColor = if (job.isActive) "green" else "gray"
            val shortKey = if (job.key.length > 12) job.key.take(12) + "..." else job.key
            val queryInfo = job.queryCount?.let { " ($it queries)" } ?: ""

            text = "<html><font color='$statusColor'>$statusIcon</font> $shortKey$queryInfo<br>" +
                    "<font size='-2' color='gray'>${job.formattedDuration}</font></html>"

            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            return this
        }
    }
}
