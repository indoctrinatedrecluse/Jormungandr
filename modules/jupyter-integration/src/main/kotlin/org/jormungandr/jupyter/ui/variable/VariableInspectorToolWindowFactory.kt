package org.jormungandr.jupyter.ui.variable

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.*
import org.jormungandr.jupyter.kernel.JupyterKernelService
import org.jormungandr.jupyter.variable.VariableInfo
import org.jormungandr.jupyter.variable.VariableInspectorService
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Interactive Variable & Tensor Inspector Tool Window.
 * Inspects live kernel memory, variables, DataFrames, Polars tables, and PyTorch tensors.
 */
class VariableInspectorToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = VariableInspectorPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class VariableInspectorPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val inspectorService = VariableInspectorService.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var allVariables: List<VariableInfo> = emptyList()
    private var displayedVariables: List<VariableInfo> = emptyList()

    private val searchField = JBTextField().apply {
        emptyText.text = "Filter variables by name or type..."
        putClientProperty("JTextField.Search.Icon", true)
    }

    private val tableModel = VariableTableModel()
    private val table = JBTable(tableModel).apply {
        fillsViewportHeight = true
        rowHeight = 26
        setShowGrid(true)
        gridColor = Color(238, 232, 213)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        setDefaultRenderer(Object::class.java, VariableCellRenderer())
    }

    private val statusLabel = JLabel("Ready. Connect or execute a notebook cell to inspect variables.")

    private val refreshBtn = JButton("🔄 Refresh").apply {
        toolTipText = "Refresh variable and tensor state from active kernel"
    }

    private val openDfBtn = JButton("📊 Open in DataFrame Viewer").apply {
        isEnabled = false
        toolTipText = "Open selected DataFrame directly in high-performance viewer"
    }

    private val plotBtn = JButton("📈 Plot").apply {
        isEnabled = false
        toolTipText = "Generate quick plot in Scientific Plot Viewer"
    }

    private val inspectTensorBtn = JButton("🔍 Tensor Details").apply {
        isEnabled = false
        toolTipText = "Inspect tensor device, shape, min/max, and gradient state"
    }

    private val deleteBtn = JButton("🗑️ Delete").apply {
        isEnabled = false
        toolTipText = "Delete variable from Python session"
    }

    init {
        background = Color(253, 246, 227) // Solarized Base3
        layout = BorderLayout(0, 4)

        // 1. Top Controls Bar
        val topPanel = JPanel(BorderLayout(6, 6)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(6, 8, 4, 8)
        }

        val toolbar = JToolBar().apply {
            isFloatable = false
            isOpaque = false
            add(refreshBtn)
            addSeparator(Dimension(8, 0))
            add(openDfBtn)
            addSeparator(Dimension(4, 0))
            add(plotBtn)
            addSeparator(Dimension(4, 0))
            add(inspectTensorBtn)
            addSeparator(Dimension(4, 0))
            add(deleteBtn)
        }

        topPanel.add(searchField, BorderLayout.NORTH)
        topPanel.add(toolbar, BorderLayout.SOUTH)
        add(topPanel, BorderLayout.NORTH)

        // 2. Main Grid
        add(JBScrollPane(table), BorderLayout.CENTER)

        // 3. Status Bar
        val bottomPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 8, 6, 8)
            add(statusLabel, BorderLayout.WEST)
        }
        add(bottomPanel, BorderLayout.SOUTH)

        // Listeners & Handlers
        setupListeners()
        observeVariables()
    }

    private fun setupListeners() {
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterVariables()
            override fun removeUpdate(e: DocumentEvent?) = filterVariables()
            override fun changedUpdate(e: DocumentEvent?) = filterVariables()
        })

        table.selectionModel.addListSelectionListener {
            val selected = getSelectedVariable()
            openDfBtn.isEnabled = selected != null && (selected.isDataFrame || selected.isPolars)
            plotBtn.isEnabled = selected != null
            inspectTensorBtn.isEnabled = selected != null && selected.isTensor
            deleteBtn.isEnabled = selected != null
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = getSelectedVariable() ?: return
                    when {
                        selected.isDataFrame || selected.isPolars -> triggerOpenDataFrame(selected)
                        selected.isTensor -> triggerInspectTensor(selected)
                        else -> triggerPlot(selected)
                    }
                }
            }
        })

        refreshBtn.addActionListener {
            refreshFromActiveKernel()
        }

        openDfBtn.addActionListener {
            getSelectedVariable()?.let { triggerOpenDataFrame(it) }
        }

        plotBtn.addActionListener {
            getSelectedVariable()?.let { triggerPlot(it) }
        }

        inspectTensorBtn.addActionListener {
            getSelectedVariable()?.let { triggerInspectTensor(it) }
        }

        deleteBtn.addActionListener {
            getSelectedVariable()?.let { triggerDelete(it) }
        }
    }

    private fun observeVariables() {
        scope.launch {
            inspectorService.variables.collect { vars ->
                SwingUtilities.invokeLater {
                    allVariables = vars
                    filterVariables()
                    statusLabel.text = "Tracking ${vars.size} active memory variables."
                }
            }
        }
    }

    private fun filterVariables() {
        val query = searchField.text.trim().lowercase()
        displayedVariables = if (query.isEmpty()) {
            allVariables
        } else {
            allVariables.filter {
                it.name.lowercase().contains(query) ||
                        it.typeName.lowercase().contains(query) ||
                        it.shape.lowercase().contains(query)
            }
        }
        tableModel.fireTableDataChanged()
    }

    private fun getSelectedVariable(): VariableInfo? {
        val row = table.selectedRow
        if (row in 0 until displayedVariables.size) {
            return displayedVariables[row]
        }
        return null
    }

    private fun getActiveSession() = inspectorService.getActiveSession()
        ?: ApplicationManager.getApplication().getService(JupyterKernelService::class.java).getActiveSessions().firstOrNull()

    private fun refreshFromActiveKernel() {
        val session = getActiveSession()
        if (session == null) {
            statusLabel.text = "No running Python kernel detected. Execute a notebook cell first."
            return
        }
        statusLabel.text = "Refreshing variables from kernel..."
        scope.launch {
            inspectorService.refresh(session)
        }
    }

    private fun triggerOpenDataFrame(v: VariableInfo) {
        val session = getActiveSession() ?: return
        statusLabel.text = "Launching DataFrame Viewer for ${v.name}..."
        scope.launch {
            inspectorService.openVariableInDataFrameViewer(session, v.name)
        }
    }

    private fun triggerPlot(v: VariableInfo) {
        val session = getActiveSession() ?: return
        statusLabel.text = "Plotting ${v.name}..."
        scope.launch {
            val img = inspectorService.plotVariable(session, v.name)
            if (img != null) {
                val plotManager = org.jormungandr.core.plot.PlotManagerService.getInstance()
                plotManager.addPlot(
                    org.jormungandr.core.plot.PlotItem(
                        title = "Variable Plot: ${v.name}",
                        source = "Variable Inspector",
                        image = img
                    )
                )
                SwingUtilities.invokeLater {
                    statusLabel.text = "Plot for ${v.name} sent to Scientific Plot Viewer."
                }
            } else {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Could not generate plot for ${v.name}."
                }
            }
        }
    }

    private fun triggerInspectTensor(v: VariableInfo) {
        val session = getActiveSession() ?: return
        scope.launch {
            val jsonDetails = inspectorService.inspectTensorDetails(session, v.name)
            SwingUtilities.invokeLater {
                if (jsonDetails != null) {
                    Messages.showInfoMessage(
                        project,
                        "Tensor Inspection for '${v.name}':\n\n$jsonDetails",
                        "PyTorch Tensor Inspector"
                    )
                } else {
                    Messages.showWarningDialog(
                        project,
                        "Could not inspect tensor details for '${v.name}'",
                        "Tensor Inspector"
                    )
                }
            }
        }
    }

    private fun triggerDelete(v: VariableInfo) {
        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete variable '${v.name}' from the running kernel memory?",
            "Delete Variable",
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.YES) {
            val session = getActiveSession() ?: return
            scope.launch {
                inspectorService.deleteVariable(session, v.name)
            }
        }
    }

    private inner class VariableTableModel : AbstractTableModel() {
        private val columns = arrayOf("Name", "Type", "Shape", "Size", "Device / Engine", "Preview")

        override fun getRowCount(): Int = displayedVariables.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val v = displayedVariables[rowIndex]
            return when (columnIndex) {
                0 -> v.name
                1 -> v.typeName
                2 -> v.shape
                3 -> v.sizeFormatted
                4 -> when {
                    v.isTensor -> "CUDA / ${v.tensorDevice ?: "cpu"}"
                    v.isPolars -> "Polars Arrow"
                    v.isDataFrame -> "Pandas"
                    else -> "-"
                }
                5 -> v.preview
                else -> ""
            }
        }
    }

    private inner class VariableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (!isSelected && row in displayedVariables.indices) {
                val v = displayedVariables[row]
                when {
                    v.isTensor -> {
                        c.foreground = Color(181, 137, 0) // Solarized Yellow / Gold
                    }
                    v.isDataFrame || v.isPolars -> {
                        c.foreground = Color(42, 161, 152) // Solarized Cyan
                    }
                    else -> {
                        c.foreground = Color(101, 123, 131) // Solarized Base00
                    }
                }
                c.background = if (row % 2 == 0) Color(253, 246, 227) else Color(238, 232, 213)
            }
            return c
        }
    }
}
