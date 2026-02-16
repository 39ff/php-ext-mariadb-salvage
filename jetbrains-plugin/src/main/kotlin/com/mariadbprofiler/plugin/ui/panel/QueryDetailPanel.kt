package com.mariadbprofiler.plugin.ui.panel

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.openapi.components.service
import com.mariadbprofiler.plugin.model.BacktraceFrame
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.service.FrameResolverService
import com.mariadbprofiler.plugin.settings.ProfilerState
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

    private val paramsArea = JTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, 12).let { f ->
            if (f.family == "JetBrains Mono") f else Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        foreground = JBColor(0x6A1B9A, 0xCE93D8)
    }
    private val paramsPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder("Bound Parameters")
        add(JBScrollPane(paramsArea).apply { preferredSize = Dimension(0, 60) })
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
            add(JBScrollPane(backtracePanel).apply { preferredSize = Dimension(0, 200) })
        }

        // Stack SQL + params + meta vertically, then backtrace at bottom
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(sqlPanel)
            add(paramsPanel)
            add(metaPanel)
        }

        contentPanel.apply {
            add(topPanel, BorderLayout.NORTH)
            add(btPanel, BorderLayout.CENTER)
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

        // Show bound query (with params interpolated) if available, otherwise template
        sqlArea.text = entry.boundQuery ?: entry.query
        sqlArea.caretPosition = 0

        // Show params panel only for prepared statements with bound values
        if (entry.hasParams) {
            val paramLines = entry.params.mapIndexed { i, v ->
                "  ?${i + 1} = ${v ?: "NULL"}"
            }
            paramsArea.text = paramLines.joinToString("\n")
            paramsArea.caretPosition = 0
            paramsPanel.isVisible = true
        } else {
            paramsPanel.isVisible = false
        }

        timestampLabel.text = entry.formattedTimestamp
        tablesLabel.text = entry.tables.joinToString(", ").ifEmpty { "-" }
        tagsLabel.text = entry.tags.joinToString(", ").ifEmpty { "-" }

        // Determine highlighted depth from Groovy frame resolver
        val resolver = project.getService(FrameResolverService::class.java)
        val highlightDepth = resolver.resolve(entry)

        // Build backtrace links with depth numbers
        backtracePanel.removeAll()
        entry.backtrace.forEachIndexed { index, frame ->
            val isHighlighted = index == highlightDepth
            backtracePanel.add(createBacktraceLink(frame, index, isHighlighted))
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

    private fun createBacktraceLink(frame: BacktraceFrame, depth: Int, isHighlighted: Boolean): JComponent {
        val escaped = StringUtil.escapeXmlEntities(frame.displayText)
        val depthStr = "#$depth".padEnd(4)
        val boldStart = if (isHighlighted) "<b>" else ""
        val boldEnd = if (isHighlighted) "</b>" else ""

        val state = service<ProfilerState>()
        val localPath = state.mapToLocalPath(frame.file)

        val normalColor = if (isHighlighted) JBColor(0x1B5E20, 0x66BB6A) else JBColor(0x2962FF, 0x82B1FF)
        val normalHtml = "<html>$boldStart$depthStr &lt;- $escaped$boldEnd</html>"
        val hoverHtml = "<html>$boldStart$depthStr &lt;- <u>$escaped</u>$boldEnd</html>"

        val linkLabel = JBLabel(normalHtml).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = normalColor
            toolTipText = "Click to open $localPath:${frame.line}"

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    navigateToFile(frame.file, frame.line)
                }
                override fun mouseEntered(e: MouseEvent?) {
                    text = hoverHtml
                }
                override fun mouseExited(e: MouseEvent?) {
                    text = normalHtml
                }
            })
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 1)).apply {
            if (isHighlighted) {
                background = JBColor(Color(0xE8F5E9), Color(0x1B3A1B))
                isOpaque = true
            }
            add(linkLabel)
            maximumSize = Dimension(Int.MAX_VALUE, 24)
        }
    }

    private fun navigateToFile(filePath: String, line: Int) {
        val state = service<ProfilerState>()
        val localPath = state.mapToLocalPath(filePath)
        val vf = LocalFileSystem.getInstance().findFileByPath(localPath) ?: return
        val descriptor = OpenFileDescriptor(project, vf, line - 1, 0)
        descriptor.navigate(true)
    }
}
