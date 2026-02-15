package com.mariadbprofiler.plugin.ui.panel

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.mariadbprofiler.plugin.model.QueryType
import com.mariadbprofiler.plugin.service.StatisticsService
import java.awt.*
import javax.swing.*

class StatisticsPanel : JPanel(BorderLayout()) {

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    }

    private val emptyLabel = JBLabel("No data to display").apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = JBColor.GRAY
    }

    init {
        add(JBScrollPane(contentPanel), BorderLayout.CENTER)
    }

    fun updateStats(stats: StatisticsService.QueryStats) {
        contentPanel.removeAll()

        if (stats.totalQueries == 0) {
            contentPanel.add(emptyLabel)
            contentPanel.revalidate()
            contentPanel.repaint()
            return
        }

        // Total queries
        contentPanel.add(createSectionTitle("Overview"))
        contentPanel.add(createKeyValue("Total Queries", stats.totalQueries.toString()))
        contentPanel.add(Box.createVerticalStrut(16))

        // By type
        contentPanel.add(createSectionTitle("Query Types"))
        contentPanel.add(Box.createVerticalStrut(4))
        contentPanel.add(QueryTypeBarChart(stats.byType, stats.totalQueries))
        contentPanel.add(Box.createVerticalStrut(16))

        // By table (top 20)
        contentPanel.add(createSectionTitle("Top Tables"))
        val topTables = stats.byTable.entries
            .sortedByDescending { it.value }
            .take(20)
        topTables.forEach { (table, count) ->
            contentPanel.add(createBarRow(table, count, stats.totalQueries))
        }
        if (stats.byTable.isEmpty()) {
            contentPanel.add(JBLabel("  (no tables detected)").apply {
                foreground = JBColor.GRAY
            })
        }
        contentPanel.add(Box.createVerticalStrut(16))

        // By tag (top 20)
        if (stats.byTag.isNotEmpty()) {
            contentPanel.add(createSectionTitle("Top Tags"))
            val topTags = stats.byTag.entries
                .sortedByDescending { it.value }
                .take(20)
            topTags.forEach { (tag, count) ->
                contentPanel.add(createBarRow(tag, count, stats.totalQueries))
            }
        }

        contentPanel.add(Box.createVerticalGlue())
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    fun clear() {
        contentPanel.removeAll()
        contentPanel.add(emptyLabel)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun createSectionTitle(title: String): JComponent {
        return JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun createKeyValue(key: String, value: String): JComponent {
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JBLabel("  $key: "))
            add(JBLabel(value).apply { font = font.deriveFont(Font.BOLD) })
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 24)
        }
    }

    private fun createBarRow(label: String, count: Int, total: Int): JComponent {
        return JPanel(BorderLayout(8, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
            val pct = if (total > 0) (count.toDouble() / total * 100) else 0.0

            add(JBLabel(label).apply { preferredSize = Dimension(160, 20) }, BorderLayout.WEST)
            add(BarComponent(pct), BorderLayout.CENTER)
            add(JBLabel("$count (%.1f%%)".format(pct)).apply {
                preferredSize = Dimension(100, 20)
            }, BorderLayout.EAST)

            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 24)
        }
    }
}

private class QueryTypeBarChart(
    private val byType: Map<QueryType, Int>,
    private val total: Int
) : JPanel() {

    init {
        preferredSize = Dimension(400, 60)
        maximumSize = Dimension(Int.MAX_VALUE, 60)
        alignmentX = LEFT_ALIGNMENT
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val barHeight = 20
        val gap = 4
        var y = 4

        val types = listOf(QueryType.SELECT, QueryType.INSERT, QueryType.UPDATE, QueryType.DELETE)
        val colors = mapOf(
            QueryType.SELECT to JBColor(Color(0x4CAF50), Color(0x81C784)),
            QueryType.INSERT to JBColor(Color(0x2196F3), Color(0x64B5F6)),
            QueryType.UPDATE to JBColor(Color(0xFF9800), Color(0xFFB74D)),
            QueryType.DELETE to JBColor(Color(0xF44336), Color(0xEF5350))
        )

        for (type in types) {
            val count = byType[type] ?: 0
            if (count == 0 && total > 0) { y += barHeight + gap; continue }
            val pct = if (total > 0) count.toDouble() / total else 0.0
            val barWidth = ((width - 180) * pct).toInt()

            g2.color = colors[type] ?: JBColor.GRAY
            g2.fillRoundRect(80, y, maxOf(barWidth, 2), barHeight, 4, 4)

            g2.color = JBColor.foreground()
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g2.drawString(type.label, 4, y + 15)
            g2.drawString("$count", 80 + maxOf(barWidth, 2) + 6, y + 15)

            y += barHeight + gap
        }
    }
}

private class BarComponent(private val pct: Double) : JPanel() {

    init {
        preferredSize = Dimension(200, 16)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Background
        g2.color = JBColor(Color(0xE0E0E0), Color(0x3C3F41))
        g2.fillRoundRect(0, 2, width, height - 4, 4, 4)

        // Bar
        val barWidth = ((width * pct) / 100).toInt()
        if (barWidth > 0) {
            g2.color = JBColor(Color(0x42A5F5), Color(0x64B5F6))
            g2.fillRoundRect(0, 2, barWidth, height - 4, 4, 4)
        }
    }
}
