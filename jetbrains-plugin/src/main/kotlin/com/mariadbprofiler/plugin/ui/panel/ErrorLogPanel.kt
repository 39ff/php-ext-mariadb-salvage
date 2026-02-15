package com.mariadbprofiler.plugin.ui.panel

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.mariadbprofiler.plugin.service.ErrorLogService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ErrorLogPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tableModel = ErrorTableModel()
    private val table = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 24
        columnModel.getColumn(0).preferredWidth = 70   // Time
        columnModel.getColumn(0).maxWidth = 80
        columnModel.getColumn(1).preferredWidth = 50    // Level
        columnModel.getColumn(1).maxWidth = 60
        columnModel.getColumn(2).preferredWidth = 120   // Source
        columnModel.getColumn(2).maxWidth = 160
        columnModel.getColumn(3).preferredWidth = 500   // Message

        setDefaultRenderer(Any::class.java, ErrorCellRenderer())
    }

    private val countLabel = JBLabel("No errors").apply {
        foreground = JBColor.GRAY
    }

    private val changeListener: () -> Unit = { refreshEntries() }

    init {
        setupUI()

        val errorLog = project.getService(ErrorLogService::class.java)
        errorLog.addChangeListener(changeListener)
        refreshEntries()
    }

    private fun setupUI() {
        // Toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        val clearButton = JButton("Clear").apply {
            addActionListener {
                project.getService(ErrorLogService::class.java).clear()
            }
        }
        toolbar.add(clearButton)
        toolbar.add(Box.createHorizontalStrut(12))
        toolbar.add(countLabel)
        add(toolbar, BorderLayout.NORTH)

        // Table
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    private fun refreshEntries() {
        SwingUtilities.invokeLater {
            val errorLog = project.getService(ErrorLogService::class.java)
            val entries = errorLog.getEntries()
            tableModel.setEntries(entries)

            val errorCount = entries.count { it.level == ErrorLogService.Level.ERROR }
            val warnCount = entries.count { it.level == ErrorLogService.Level.WARN }
            countLabel.text = when {
                entries.isEmpty() -> "No messages"
                else -> "${entries.size} messages ($errorCount errors, $warnCount warnings)"
            }
            countLabel.foreground = when {
                errorCount > 0 -> JBColor(Color(0xD32F2F), Color(0xEF5350))
                warnCount > 0 -> JBColor(Color(0xF57C00), Color(0xFFB74D))
                else -> JBColor.GRAY
            }

            // Auto-scroll to last row
            if (entries.isNotEmpty()) {
                table.scrollRectToVisible(table.getCellRect(entries.size - 1, 0, true))
            }
        }
    }

    fun removeListeners() {
        val errorLog = project.getService(ErrorLogService::class.java)
        errorLog.removeChangeListener(changeListener)
    }
}

private class ErrorTableModel : AbstractTableModel() {

    private var entries: List<ErrorLogService.ErrorEntry> = emptyList()

    private val columnNames = arrayOf("Time", "Level", "Source", "Message")

    fun setEntries(newEntries: List<ErrorLogService.ErrorEntry>) {
        entries = newEntries
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = entries.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val entry = entries[rowIndex]
        return when (columnIndex) {
            0 -> entry.formattedTimestamp
            1 -> entry.level.name
            2 -> entry.source
            3 -> entry.message
            else -> ""
        }
    }
}

private class ErrorCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean,
        hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (!isSelected) {
            // Color the Level column
            val level = table.getValueAt(row, 1) as? String ?: ""
            foreground = when (level) {
                "ERROR" -> JBColor(Color(0xD32F2F), Color(0xEF5350))
                "WARN" -> JBColor(Color(0xF57C00), Color(0xFFB74D))
                "INFO" -> JBColor(Color(0x1976D2), Color(0x64B5F6))
                else -> JBColor.foreground()
            }
        }

        return comp
    }
}
