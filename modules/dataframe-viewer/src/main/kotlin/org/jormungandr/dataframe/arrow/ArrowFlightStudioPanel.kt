package org.jormungandr.dataframe.arrow

import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive Swing UI Panel for Apache Arrow Flight & Zero-Copy IPC Streaming.
 * Connects to live Flight endpoints, displays streaming bandwidth/latency metrics,
 * loads batches directly into the active DataFrame, and generates Python server code.
 */
class ArrowFlightStudioPanel(
    private var activeDataFrame: DataFrame = DataFrame.empty(),
    private val onDataFrameStreamed: ((DataFrame) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val hostField = JBTextField("localhost", 12)
    private val portField = JBTextField("8815", 5)
    private val ticketField = JBTextField("telemetry_stream", 14)

    // Metrics display
    private val statusBadge = JBLabel("Ready to Connect")
    private val throughputBadge = JBLabel("Throughput: 0.0 MB/s")
    private val rowCountBadge = JBLabel("Rows Streamed: 0")

    private val logArea = JBTextArea(10, 40).apply {
        isEditable = false
        font = Font("Consolas", Font.PLAIN, 12)
        text = "[*] Arrow Flight Studio ready. Enter host and ticket to stream zero-copy columnar batches.\n"
    }

    init {
        setupUI()
    }

    fun setDataFrame(df: DataFrame) {
        this.activeDataFrame = df
    }

    private fun setupUI() {
        // 1. Top Header & Toolbar
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>✈️ Arrow Flight &amp; IPC Streaming</b>"))
        leftHeader.add(createPill(statusBadge, Color(38, 139, 210)))
        leftHeader.add(createPill(throughputBadge, Color(133, 153, 0)))
        leftHeader.add(createPill(rowCountBadge, Color(108, 113, 196)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val streamBtn = JButton("🚀 Connect & Stream").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 11.5f)
            addActionListener { initiateFlightStream() }
        }
        val copyServerBtn = JButton("🐍 Python Server Code").apply {
            isFocusPainted = false
            addActionListener { copyPythonServerCode() }
        }
        val exportIpcBtn = JButton("💾 Export Arrow IPC").apply {
            isFocusPainted = false
            addActionListener { exportActiveDfToIpc() }
        }

        rightHeader.add(streamBtn)
        rightHeader.add(copyServerBtn)
        rightHeader.add(exportIpcBtn)

        topPanel.add(leftHeader, BorderLayout.WEST)
        topPanel.add(rightHeader, BorderLayout.EAST)

        // 2. Connection Parameters Toolbar
        val connPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 6)).apply {
            border = CompoundBorder(LineBorder(Color(230, 230, 230), 1), EmptyBorder(4, 10, 4, 10))
            background = Color(250, 250, 250)
        }
        connPanel.add(JBLabel("Flight Host:"))
        connPanel.add(hostField)
        connPanel.add(JBLabel("Port:"))
        connPanel.add(portField)
        connPanel.add(JBLabel("Ticket / Stream:"))
        connPanel.add(ticketField)

        val northContainer = JPanel(BorderLayout())
        northContainer.add(topPanel, BorderLayout.NORTH)
        northContainer.add(connPanel, BorderLayout.SOUTH)
        add(northContainer, BorderLayout.NORTH)

        // 3. Center Activity Log
        val centerPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
            add(JBLabel("<b>Stream Diagnostic Log:</b>").apply { border = EmptyBorder(0, 0, 4, 0) }, BorderLayout.NORTH)
            add(JBScrollPane(logArea), BorderLayout.CENTER)
        }
        add(centerPanel, BorderLayout.CENTER)
    }

    private fun initiateFlightStream() {
        val host = hostField.text.trim().ifEmpty { "localhost" }
        val port = portField.text.trim().toIntOrNull() ?: 8815
        val ticketName = ticketField.text.trim().ifEmpty { "default_flight" }

        logArea.append("[*] Connecting to grpc://$host:$port with ticket '$ticketName'...\n")
        statusBadge.text = "Streaming Active"

        val endpoint = FlightEndpoint(host, port)
        val ticket = FlightTicket(ticketName.toByteArray(Charsets.UTF_8), ticketName)

        val df = ArrowFlightConnector.fetchStream(endpoint, ticket) { metrics ->
            throughputBadge.text = String.format("Throughput: %.2f MB/s", metrics.throughputMbPerSec)
            rowCountBadge.text = "Rows Streamed: ${metrics.rowsStreamed}"
            if (metrics.isCompleted) {
                statusBadge.text = if (metrics.errorMessage != null) "Stream Failed" else "Completed (Zero-Copy)"
            }
        }

        if (df.rowCount > 0) {
            logArea.append("[+] Successfully ingested ${df.rowCount} rows across ${df.columnCount} Arrow columnar fields!\n")
            logArea.append("[+] Shape: ${df.shape.first} rows x ${df.shape.second} cols | Ingested via Zero-Copy IPC buffer.\n")
            onDataFrameStreamed?.invoke(df)
        } else {
            logArea.append("[-] Stream ended with 0 rows or connection timed out.\n")
        }
    }

    private fun copyPythonServerCode() {
        val port = portField.text.trim().toIntOrNull() ?: 8815
        val ticketName = ticketField.text.trim().ifEmpty { "telemetry_stream" }
        val code = ArrowFlightConnector.generatePythonFlightServerCode(port, ticketName)

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(code), null)
        Messages.showInfoMessage("PyArrow Flight server script copied to clipboard! Run it with `python -m flight_server`.", "Arrow Flight Script Ready")
    }

    private fun exportActiveDfToIpc() {
        if (activeDataFrame.isEmpty) {
            Messages.showWarningDialog("Active DataFrame is empty. Load or stream a dataset first.", "Export Arrow IPC")
            return
        }

        val bytes = ArrowIpcStreamReader.writeIpcStream(activeDataFrame)
        val sizeKb = bytes.size / 1024.0
        logArea.append("[+] Serialized active DataFrame '${activeDataFrame.name}' to ${String.format("%.2f", sizeKb)} KB of Arrow IPC bytes.\n")
        Messages.showInfoMessage("Successfully encoded ${activeDataFrame.rowCount} rows to Arrow IPC format (${String.format("%.2f", sizeKb)} KB).", "Arrow IPC Encoded")
    }

    private fun createPill(label: JBLabel, color: Color): JPanel {
        return JPanel(FlowLayout(FlowLayout.CENTER, 4, 1)).apply {
            background = Color(color.red, color.green, color.blue, 30)
            border = CompoundBorder(LineBorder(color, 1), EmptyBorder(1, 6, 1, 6))
            add(label.apply {
                foreground = Color(color.red / 2, color.green / 2, color.blue / 2)
                font = font.deriveFont(Font.BOLD, 10.5f)
            })
        }
    }
}
