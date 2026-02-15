package com.mariadbprofiler.plugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class ProfilerConfigurable : Configurable {

    private var logDirField: TextFieldWithBrowseButton? = null
    private var phpPathField: TextFieldWithBrowseButton? = null
    private var cliScriptField: TextFieldWithBrowseButton? = null
    private var maxQueriesSpinner: JSpinner? = null
    private var refreshIntervalSpinner: JSpinner? = null
    private var tailBufferSpinner: JSpinner? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "MariaDB Profiler"

    override fun createComponent(): JComponent {
        logDirField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Profiler Log Directory", null, null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
        }

        phpPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select PHP Executable", null, null,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        }

        cliScriptField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select CLI Script", null, null,
                FileChooserDescriptorFactory.createSingleFileDescriptor("php")
            )
        }

        maxQueriesSpinner = JSpinner(SpinnerNumberModel(10000, 100, 100000, 1000))
        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(5, 1, 60, 1))
        tailBufferSpinner = JSpinner(SpinnerNumberModel(500, 50, 10000, 100))

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Profiler Log Directory:"), logDirField!!, 1, false)
            .addLabeledComponent(JBLabel("PHP Executable Path:"), phpPathField!!, 1, false)
            .addLabeledComponent(JBLabel("CLI Script Path:"), cliScriptField!!, 1, false)
            .addLabeledComponent(JBLabel("Max Queries to Display:"), maxQueriesSpinner!!, 1, false)
            .addLabeledComponent(JBLabel("Auto-refresh Interval (sec):"), refreshIntervalSpinner!!, 1, false)
            .addLabeledComponent(JBLabel("Live Tail Buffer Size:"), tailBufferSpinner!!, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val state = service<ProfilerState>()
        return logDirField?.text != state.logDir ||
                phpPathField?.text != state.phpPath ||
                cliScriptField?.text != state.cliScriptPath ||
                maxQueriesSpinner?.value != state.maxQueries ||
                refreshIntervalSpinner?.value != state.refreshInterval ||
                tailBufferSpinner?.value != state.tailBufferSize
    }

    override fun apply() {
        val state = service<ProfilerState>()
        state.logDir = logDirField?.text ?: state.logDir
        state.phpPath = phpPathField?.text ?: state.phpPath
        state.cliScriptPath = cliScriptField?.text ?: state.cliScriptPath
        state.maxQueries = maxQueriesSpinner?.value as? Int ?: state.maxQueries
        state.refreshInterval = refreshIntervalSpinner?.value as? Int ?: state.refreshInterval
        state.tailBufferSize = tailBufferSpinner?.value as? Int ?: state.tailBufferSize
    }

    override fun reset() {
        val state = service<ProfilerState>()
        logDirField?.text = state.logDir
        phpPathField?.text = state.phpPath
        cliScriptField?.text = state.cliScriptPath
        maxQueriesSpinner?.value = state.maxQueries
        refreshIntervalSpinner?.value = state.refreshInterval
        tailBufferSpinner?.value = state.tailBufferSize
    }

    override fun disposeUIResources() {
        logDirField = null
        phpPathField = null
        cliScriptField = null
        maxQueriesSpinner = null
        refreshIntervalSpinner = null
        tailBufferSpinner = null
        mainPanel = null
    }
}
