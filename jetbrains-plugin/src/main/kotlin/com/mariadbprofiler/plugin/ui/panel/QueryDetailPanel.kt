package com.mariadbprofiler.plugin.ui.panel

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.mariadbprofiler.plugin.model.BacktraceFrame
import com.mariadbprofiler.plugin.model.QueryEntry
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class QueryDetailPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val sqlArea = JTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, 13).let { f ->
            if (f.family == "JetBrains Mono") f else Font(Font.MONOSPACED, Font.PLAIN, 13)
        }
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    private val tablesLabel = JBLabel()
    private val tagsLabel = JBLabel()
    private val timestampLabel = JBLabel()
    private val backtracePanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val emptyLabel = JBLabel("Select a query to view details").apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = JBColor.GRAY
    }

    private val contentPanel = JPanel(BorderLayout())
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    init {
        setupUI()
        showEmpty()
    }

    private fun setupUI() {
        // SQL panel
        val sqlPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("SQL")
            add(JBScrollPane(sqlArea).apply { preferredSize = Dimension(0, 120) })
        }

        // Metadata panel
        val metaPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Details")
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(2, 6, 2, 6)
            }

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
            add(JBLabel("Timestamp:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(timestampLabel, gbc)

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
            add(JBLabel("Tables:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(tablesLabel, gbc)

            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
            add(JBLabel("Tags:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(tagsLabel, gbc)
        }

        // Backtrace panel
        val btPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Backtrace")
            add(JBScrollPane(backtracePanel).apply { preferredSize = Dimension(0, 100) })
        }

        contentPanel.apply {
            add(sqlPanel, BorderLayout.NORTH)
            add(metaPanel, BorderLayout.CENTER)
            add(btPanel, BorderLayout.SOUTH)
        }

        cardPanel.add(emptyLabel, "empty")
        cardPanel.add(JBScrollPane(contentPanel), "detail")

        add(cardPanel, BorderLayout.CENTER)
    }

    fun showEntry(entry: QueryEntry?) {
        if (entry == null) {
            showEmpty()
            return
        }

        sqlArea.text = entry.query
        sqlArea.caretPosition = 0
        timestampLabel.text = entry.formattedTimestamp
        tablesLabel.text = entry.tables.joinToString(", ").ifEmpty { "-" }
        tagsLabel.text = entry.tags.joinToString(", ").ifEmpty { "-" }

        // Build backtrace links
        backtracePanel.removeAll()
        entry.backtrace.forEach { frame ->
            backtracePanel.add(createBacktraceLink(frame))
        }
        if (entry.backtrace.isEmpty()) {
            backtracePanel.add(JBLabel("  (no backtrace)").apply {
                foreground = JBColor.GRAY
            })
        }
        backtracePanel.revalidate()
        backtracePanel.repaint()

        cardLayout.show(cardPanel, "detail")
    }

    private fun showEmpty() {
        cardLayout.show(cardPanel, "empty")
    }

    private fun createBacktraceLink(frame: BacktraceFrame): JComponent {
        val escaped = StringUtil.escapeXmlEntities(frame.displayText)
        val linkLabel = JBLabel("<html>  -&gt; $escaped</html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = JBColor(0x2962FF, 0x82B1FF)
            toolTipText = "Click to open ${frame.file}:${frame.line}"

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    navigateToFile(frame.file, frame.line)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    text = "<html>  -&gt; <u>$escaped</u></html>"
                }

                override fun mouseExited(e: MouseEvent?) {
                    text = "<html>  -&gt; $escaped</html>"
                }
            })
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 1)).apply {
            add(linkLabel)
            maximumSize = Dimension(Int.MAX_VALUE, 24)
        }
    }

    private fun navigateToFile(filePath: String, line: Int) {
        val vf = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val descriptor = OpenFileDescriptor(project, vf, line - 1, 0)
        descriptor.navigate(true)
    }
}
