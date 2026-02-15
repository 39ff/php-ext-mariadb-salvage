package com.mariadbprofiler.plugin.ui.table

import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.model.QueryType
import javax.swing.table.AbstractTableModel

class QueryTableModel : AbstractTableModel() {

    private var allEntries: List<QueryEntry> = emptyList()
    private var filteredEntries: List<QueryEntry> = emptyList()
    private var typeFilter: QueryType? = null
    private var textFilter: String = ""

    private val columns = arrayOf("#", "Time", "Type", "SQL", "Tags", "File")

    override fun getRowCount(): Int = filteredEntries.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val entry = filteredEntries[rowIndex]
        return when (columnIndex) {
            0 -> rowIndex + 1
            1 -> entry.formattedTimestamp
            2 -> entry.queryType.label
            3 -> entry.shortSql
            4 -> entry.tags.joinToString(", ")
            5 -> entry.sourceFile
            else -> ""
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return if (columnIndex == 0) Integer::class.java else String::class.java
    }

    fun getEntryAt(rowIndex: Int): QueryEntry? {
        return filteredEntries.getOrNull(rowIndex)
    }

    fun setEntries(entries: List<QueryEntry>) {
        allEntries = entries
        applyFilters()
    }

    fun addEntries(entries: List<QueryEntry>) {
        allEntries = allEntries + entries
        applyFilters()
    }

    fun setTypeFilter(type: QueryType?) {
        typeFilter = type
        applyFilters()
    }

    fun setTextFilter(text: String) {
        textFilter = text.lowercase()
        applyFilters()
    }

    fun clear() {
        allEntries = emptyList()
        filteredEntries = emptyList()
        fireTableDataChanged()
    }

    private fun applyFilters() {
        filteredEntries = allEntries.filter { entry ->
            val matchesType = typeFilter == null || entry.queryType == typeFilter
            val matchesText = textFilter.isEmpty() ||
                    entry.query.lowercase().contains(textFilter) ||
                    entry.tags.any { it.lowercase().contains(textFilter) } ||
                    entry.sourceFile.lowercase().contains(textFilter)
            matchesType && matchesText
        }
        fireTableDataChanged()
    }
}
