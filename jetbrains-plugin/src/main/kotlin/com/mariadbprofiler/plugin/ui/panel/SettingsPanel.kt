package com.mariadbprofiler.plugin.ui.panel

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.mariadbprofiler.plugin.service.ErrorLogService
import com.mariadbprofiler.plugin.settings.ProfilerState
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.*

class SettingsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val logDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Profiler Log Directory", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val phpPathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select PHP Executable", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }
    private val cliScriptField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select CLI Script", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("php")
        )
    }
    private val maxQueriesSpinner = JSpinner(SpinnerNumberModel(10000, 100, 100000, 1000))
    private val refreshIntervalSpinner = JSpinner(SpinnerNumberModel(5, 1, 60, 1))
    private val tailBufferSpinner = JSpinner(SpinnerNumberModel(500, 50, 10000, 100))
    private val tagDepthField = JTextField().apply {
        toolTipText = "Tag=Depth pairs, comma-separated. e.g. laravel=8,symfony=7,default=0"
    }

    private val statusLabel = JBLabel("").apply { foreground = JBColor.GRAY }

    init {
        setupUI()
        loadFromState()
    }

    private fun setupUI() {
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        // Paths section
        contentPanel.add(createSectionTitle("Paths"))
        contentPanel.add(Box.createVerticalStrut(4))
        contentPanel.add(createLabeledField("Profiler Log Directory:", logDirField))
        contentPanel.add(Box.createVerticalStrut(6))
        contentPanel.add(createLabeledField("PHP Executable Path:", phpPathField))
        contentPanel.add(Box.createVerticalStrut(6))
        contentPanel.add(createLabeledField("CLI Script Path:", cliScriptField))
        contentPanel.add(Box.createVerticalStrut(16))

        // Performance section
        contentPanel.add(createSectionTitle("Performance"))
        contentPanel.add(Box.createVerticalStrut(4))
        contentPanel.add(createLabeledField("Max Queries to Display:", maxQueriesSpinner))
        contentPanel.add(Box.createVerticalStrut(6))
        contentPanel.add(createLabeledField("Auto-refresh Interval (sec):", refreshIntervalSpinner))
        contentPanel.add(Box.createVerticalStrut(6))
        contentPanel.add(createLabeledField("Live Tail Buffer Size:", tailBufferSpinner))
        contentPanel.add(Box.createVerticalStrut(16))

        // Backtrace section
        contentPanel.add(createSectionTitle("Backtrace Display"))
        contentPanel.add(Box.createVerticalStrut(4))
        contentPanel.add(createLabeledField("Tag Depth Mapping:", tagDepthField))
        contentPanel.add(Box.createVerticalStrut(2))
        contentPanel.add(JBLabel("  Format: tag=depth,tag=depth  (e.g. laravel=8,default=0)").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(11f)
            alignmentX = LEFT_ALIGNMENT
        })
        contentPanel.add(Box.createVerticalStrut(2))
        contentPanel.add(JBLabel("  The depth selects which backtrace frame to show in Query Log columns.").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(11f)
            alignmentX = LEFT_ALIGNMENT
        })
        contentPanel.add(Box.createVerticalStrut(16))

        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }
        val saveButton = JButton("Save").apply {
            addActionListener { saveToState() }
        }
        val resetButton = JButton("Reset").apply {
            addActionListener { loadFromState() }
        }
        val validateButton = JButton("Validate Paths").apply {
            addActionListener { validatePaths() }
        }
        buttonPanel.add(saveButton)
        buttonPanel.add(resetButton)
        buttonPanel.add(validateButton)
        contentPanel.add(buttonPanel)
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(statusLabel.apply { alignmentX = LEFT_ALIGNMENT })

        contentPanel.add(Box.createVerticalGlue())

        add(JBScrollPane(contentPanel), BorderLayout.CENTER)
    }

    private fun loadFromState() {
        val state = service<ProfilerState>()
        logDirField.text = state.logDir
        phpPathField.text = state.phpPath
        cliScriptField.text = state.cliScriptPath
        maxQueriesSpinner.value = state.maxQueries
        refreshIntervalSpinner.value = state.refreshInterval
        tailBufferSpinner.value = state.tailBufferSize
        tagDepthField.text = state.tagDepthMapping
        statusLabel.text = ""
    }

    private fun saveToState() {
        val state = service<ProfilerState>()
        state.logDir = logDirField.text
        state.phpPath = phpPathField.text
        state.cliScriptPath = cliScriptField.text
        state.maxQueries = maxQueriesSpinner.value as Int
        state.refreshInterval = refreshIntervalSpinner.value as Int
        state.tailBufferSize = tailBufferSpinner.value as Int
        state.tagDepthMapping = tagDepthField.text

        statusLabel.text = "Settings saved."
        statusLabel.foreground = JBColor(0x2E7D32, 0x81C784)

        project.getService(ErrorLogService::class.java)
            .addInfo("Settings", "Settings updated")
    }

    private fun validatePaths() {
        val errors = mutableListOf<String>()
        val logDir = File(logDirField.text)
        if (!logDir.exists()) {
            errors.add("Log directory does not exist: ${logDirField.text}")
        } else if (!logDir.isDirectory) {
            errors.add("Log directory is not a directory: ${logDirField.text}")
        } else {
            val jobsFile = File(logDir, "jobs.json")
            if (!jobsFile.exists()) {
                errors.add("jobs.json not found in log directory")
            }
        }

        val phpFile = File(phpPathField.text)
        if (phpPathField.text.isNotEmpty() && phpPathField.text != "php" && !phpFile.exists()) {
            errors.add("PHP executable not found: ${phpPathField.text}")
        }

        if (cliScriptField.text.isNotEmpty()) {
            val cliFile = File(cliScriptField.text)
            if (!cliFile.exists()) {
                errors.add("CLI script not found: ${cliScriptField.text}")
            }
        }

        val errorLog = project.getService(ErrorLogService::class.java)
        if (errors.isEmpty()) {
            statusLabel.text = "All paths are valid."
            statusLabel.foreground = JBColor(0x2E7D32, 0x81C784)
            errorLog.addInfo("Settings", "Path validation passed")
        } else {
            statusLabel.text = "${errors.size} issue(s) found. Check Errors tab."
            statusLabel.foreground = JBColor(0xD32F2F, 0xEF5350)
            errors.forEach { errorLog.addError("Settings", it) }
        }
    }

    private fun createSectionTitle(title: String): JComponent {
        return JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun createLabeledField(label: String, field: JComponent): JComponent {
        return JPanel(BorderLayout(8, 0)).apply {
            add(JBLabel(label).apply { preferredSize = Dimension(200, 24) }, BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 30)
        }
    }
}
