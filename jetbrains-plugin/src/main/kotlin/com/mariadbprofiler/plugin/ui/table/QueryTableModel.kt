package com.mariadbprofiler.plugin.ui.table

import com.intellij.openapi.components.service
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.model.QueryType
import com.mariadbprofiler.plugin.settings.ProfilerState
import javax.swing.table.AbstractTableModel

class QueryTableModel : AbstractTableModel() {

    private var allEntries: List<QueryEntry> = emptyList()
    private var filteredEntries: List<QueryEntry> = emptyList()
    private var typeFilter: QueryType? = null
    private var textFilter: String = ""

    private val columns = arrayOf("#", "Time", "Type", "SQL", "Tags", "Function", "File")

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
            5 -> getFrameFunction(entry)
            6 -> getFrameFile(entry)
            else -> ""
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return if (columnIndex == 0) Integer::class.java else String::class.java
    }

    /**
     * Get the backtrace frame at the depth configured for this entry's tag.
     */
    private fun getFrameAtDepth(entry: QueryEntry): com.mariadbprofiler.plugin.model.BacktraceFrame? {
        if (entry.backtrace.isEmpty()) return null
        val state = service<ProfilerState>()
        val depth = state.getDepthForTag(entry.tag)
        return entry.backtrace.getOrNull(depth) ?: entry.backtrace.lastOrNull()
    }

    private fun getFrameFile(entry: QueryEntry): String {
        val frame = getFrameAtDepth(entry) ?: return ""
        val fileName = java.io.File(frame.file).name
        return "$fileName:${frame.line}"
    }

    private fun getFrameFunction(entry: QueryEntry): String {
        val frame = getFrameAtDepth(entry) ?: return ""
        return when {
            frame.call.isNotEmpty() -> frame.call + "()"
            frame.class_name.isNotEmpty() -> "${frame.class_name}::${frame.function}()"
            frame.function.isNotEmpty() -> frame.function + "()"
            else -> ""
        }
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
                    getFrameFile(entry).lowercase().contains(textFilter) ||
                    getFrameFunction(entry).lowercase().contains(textFilter)
            matchesType && matchesText
        }
        fireTableDataChanged()
    }
}
