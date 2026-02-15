package com.mariadbprofiler.plugin.ui.panel

import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.model.QueryType
import com.mariadbprofiler.plugin.service.FrameResolverService
import com.mariadbprofiler.plugin.ui.table.QueryCellRenderer
import com.mariadbprofiler.plugin.ui.table.QueryTableModel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QueryLogPanel(
    private val onQuerySelected: (QueryEntry?) -> Unit
) : JPanel(BorderLayout()) {

    private val tableModel = QueryTableModel()

    fun setFrameResolver(resolver: FrameResolverService) {
        tableModel.frameResolver = resolver
    }
    val table = JBTable(tableModel)
    private val searchField = SearchTextField()
    private var filterButtons: Map<QueryType?, JToggleButton> = emptyMap()

    init {
        setupTable()
        setupToolbar()
    }

    private fun setupTable() {
        table.apply {
            setDefaultRenderer(Any::class.java, QueryCellRenderer())
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            rowHeight = 24
            setShowGrid(false)

            columnModel.getColumn(0).preferredWidth = 40   // #
            columnModel.getColumn(0).maxWidth = 60
            columnModel.getColumn(1).preferredWidth = 100  // Time
            columnModel.getColumn(1).maxWidth = 120
            columnModel.getColumn(2).preferredWidth = 70   // Type
            columnModel.getColumn(2).maxWidth = 80
            columnModel.getColumn(3).preferredWidth = 300  // SQL
            columnModel.getColumn(4).preferredWidth = 80   // Tags
            columnModel.getColumn(5).preferredWidth = 250  // Function
            columnModel.getColumn(6).preferredWidth = 180  // File

            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectionModel.addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    val row = selectedRow
                    onQuerySelected(if (row >= 0) tableModel.getEntryAt(row) else null)
                }
            }
        }

        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    private fun setupToolbar() {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))

        // Filter buttons
        val allBtn = createFilterButton("All", null, true)
        val selectBtn = createFilterButton("SELECT", QueryType.SELECT)
        val insertBtn = createFilterButton("INSERT", QueryType.INSERT)
        val updateBtn = createFilterButton("UPDATE", QueryType.UPDATE)
        val deleteBtn = createFilterButton("DELETE", QueryType.DELETE)

        filterButtons = mapOf(
            null to allBtn,
            QueryType.SELECT to selectBtn,
            QueryType.INSERT to insertBtn,
            QueryType.UPDATE to updateBtn,
            QueryType.DELETE to deleteBtn
        )

        val buttonGroup = ButtonGroup()
        filterButtons.values.forEach { btn ->
            buttonGroup.add(btn)
            toolbar.add(btn)
        }

        toolbar.add(Box.createHorizontalStrut(12))

        // Search field
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = doFilter()
            override fun removeUpdate(e: DocumentEvent?) = doFilter()
            override fun changedUpdate(e: DocumentEvent?) = doFilter()
            private fun doFilter() {
                tableModel.setTextFilter(searchField.text)
            }
        })
        toolbar.add(JLabel("Search: "))
        toolbar.add(searchField)

        add(toolbar, BorderLayout.NORTH)
    }

    private fun createFilterButton(text: String, type: QueryType?, selected: Boolean = false): JToggleButton {
        return JToggleButton(text, selected).apply {
            isFocusPainted = false
            addActionListener {
                tableModel.setTypeFilter(type)
            }
        }
    }

    fun setEntries(entries: List<QueryEntry>) {
        tableModel.setEntries(entries)
    }

    fun addEntries(entries: List<QueryEntry>) {
        tableModel.addEntries(entries)
    }

    fun clear() {
        tableModel.clear()
    }

    fun getQueryCount(): Int = tableModel.rowCount
}
