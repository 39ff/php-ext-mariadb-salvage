package com.mariadbprofiler.plugin.ui.table

import com.intellij.ui.JBColor
import com.mariadbprofiler.plugin.model.QueryType
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class QueryCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (component is JLabel && column == 2) {
            val typeText = value?.toString() ?: ""
            if (!isSelected) {
                component.foreground = when (typeText) {
                    QueryType.SELECT.label -> JBColor(0x2E7D32, 0x81C784) // green
                    QueryType.INSERT.label -> JBColor(0x1565C0, 0x64B5F6) // blue
                    QueryType.UPDATE.label -> JBColor(0xE65100, 0xFFB74D) // orange
                    QueryType.DELETE.label -> JBColor(0xC62828, 0xEF5350) // red
                    else -> JBColor.foreground()
                }
            }
            component.horizontalAlignment = CENTER
        }

        if (component is JLabel && column == 0) {
            component.horizontalAlignment = RIGHT
        }

        return component
    }
}
