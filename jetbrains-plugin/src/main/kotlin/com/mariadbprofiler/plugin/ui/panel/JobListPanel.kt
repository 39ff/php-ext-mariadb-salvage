package com.mariadbprofiler.plugin.ui.panel

import com.intellij.openapi.util.text.StringUtil
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
    private val onJobSelected: (JobInfo?) -> Unit,
    private val onStartJob: (() -> Unit)? = null,
    private val onStopJob: ((JobInfo) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<JobInfo>()
    private val jobList = JBList(listModel)
    private var suppressSelectionEvents = false
    private val startButton = JButton("Start").apply { toolTipText = "Start a new profiling session" }
    private val stopButton = JButton("Stop").apply { toolTipText = "Stop the selected profiling session"; isEnabled = false }

    init {
        setupUI()
    }

    private fun setupUI() {
        // Header with Start/Stop buttons
        val header = JPanel(BorderLayout()).apply {
            val label = JBLabel("Jobs")
            label.border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            add(label, BorderLayout.WEST)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
            buttonPanel.add(startButton)
            buttonPanel.add(stopButton)
            add(buttonPanel, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)

        startButton.addActionListener { onStartJob?.invoke() }
        stopButton.addActionListener {
            val selected = jobList.selectedValue
            if (selected != null && selected.isActive) {
                onStopJob?.invoke(selected)
            }
        }

        // Job list
        jobList.apply {
            cellRenderer = JobCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting && !suppressSelectionEvents) {
                    val selected = selectedValue
                    onJobSelected(selected)
                    stopButton.isEnabled = selected?.isActive == true
                }
            }
        }
        add(JBScrollPane(jobList), BorderLayout.CENTER)
    }

    fun setJobs(jobs: List<JobInfo>) {
        val selectedKey = jobList.selectedValue?.key
        suppressSelectionEvents = true
        try {
            listModel.clear()
            jobs.forEach { listModel.addElement(it) }

            // Re-select previous job if still exists
            if (selectedKey != null) {
                for (i in 0 until listModel.size) {
                    if (listModel.getElementAt(i).key == selectedKey) {
                        jobList.selectedIndex = i
                        break
                    }
                }
            }
        } finally {
            suppressSelectionEvents = false
        }
    }

    fun getSelectedJob(): JobInfo? = jobList.selectedValue

    fun selectJobByKey(key: String) {
        for (i in 0 until listModel.size) {
            if (listModel.getElementAt(i).key == key) {
                jobList.selectedIndex = i
                jobList.ensureIndexIsVisible(i)
                onJobSelected(listModel.getElementAt(i))
                stopButton.isEnabled = listModel.getElementAt(i).isActive
                break
            }
        }
    }

    fun setStartEnabled(enabled: Boolean) {
        startButton.isEnabled = enabled
    }

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
            val shortKey = StringUtil.escapeXmlEntities(
                if (job.key.length > 12) job.key.take(12) + "..." else job.key
            )
            val queryInfo = job.queryCount?.let {
                " (${StringUtil.escapeXmlEntities(it.toString())} queries)"
            } ?: ""
            val escapedDuration = StringUtil.escapeXmlEntities(job.formattedDuration)

            text = "<html><font color='$statusColor'>$statusIcon</font> $shortKey$queryInfo<br>" +
                    "<font size='-2' color='gray'>$escapedDuration</font></html>"

            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            return this
        }
    }
}
