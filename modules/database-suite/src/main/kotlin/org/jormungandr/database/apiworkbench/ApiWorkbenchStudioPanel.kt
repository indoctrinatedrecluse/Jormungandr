/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.database.apiworkbench

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive API & Inference Microservice Workbench UI.
 * Supports REST, GraphQL, gRPC inference requests and controls the live embedded Mock Server.
 */
class ApiWorkbenchStudioPanel(
    private val project: Project? = null
) : JPanel(BorderLayout()) {

    private val protocolCombo = JComboBox(ApiProtocol.values()).apply {
        renderer = DefaultListCellRenderer().apply {
            // display name
        }
    }
    private val methodCombo = JComboBox(HttpMethod.values())
    private val endpointField = JBTextField("http://localhost:8089/health", 28)

    // Badges
    private val statusBadge = JBLabel("Ready")
    private val latencyBadge = JBLabel("0 ms")
    private val sizeBadge = JBLabel("0 B")
    private val mockServerBadge = JBLabel("Mock: Stopped")

    // Editors
    private val requestBodyArea = JBTextArea(8, 40).apply {
        font = Font("Consolas", Font.PLAIN, 12)
        text = "{\n  \"model\": \"churn_predictor\",\n  \"features\": [42, 105.2, true]\n}"
    }

    private val responseBodyArea = JBTextArea(12, 40).apply {
        isEditable = false
        font = Font("Consolas", Font.PLAIN, 12)
        background = Color(24, 24, 24)
        foreground = Color(230, 230, 230)
    }

    private val mockRoutesModel = DefaultTableModel(arrayOf("Path", "Method", "Status", "Content Type"), 0)
    private val mockRoutesTable = JBTable(mockRoutesModel)

    init {
        setupUI()
        refreshMockRoutesTable()
    }

    private fun setupUI() {
        // 1. Top URL & Dispatch Bar
        val topPanel = JPanel(BorderLayout(0, 4)).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val ribbonRow = JPanel(BorderLayout()).apply { isOpaque = false }
        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>⚡ API &amp; Inference Workbench</b>"))
        leftHeader.add(createPill(mockServerBadge, Color(156, 39, 176)))
        leftHeader.add(createPill(statusBadge, Color(46, 125, 50)))
        leftHeader.add(createPill(latencyBadge, Color(21, 101, 192)))
        leftHeader.add(createPill(sizeBadge, Color(230, 81, 0)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val mockToggleBtn = JButton("Start Mock Server").apply {
            addActionListener {
                if (ApiWorkbenchEngine.isMockServerRunning()) {
                    ApiWorkbenchEngine.stopMockServer()
                    text = "Start Mock Server"
                    mockServerBadge.text = "Mock: Stopped"
                    mockServerBadge.foreground = Color.GRAY
                } else {
                    val started = ApiWorkbenchEngine.startMockServer(8089)
                    if (started) {
                        text = "Stop Mock Server"
                        mockServerBadge.text = "Mock: Running (:8089)"
                        mockServerBadge.foreground = Color(46, 125, 50)
                    }
                }
            }
        }
        rightHeader.add(mockToggleBtn)

        val templateCombo = JComboBox(ApiWorkbenchEngine.getRequestTemplates().keys.toTypedArray())
        val applyTemplateBtn = JButton("Load Template").apply {
            addActionListener {
                val tName = templateCombo.selectedItem as? String ?: return@addActionListener
                val tmpl = ApiWorkbenchEngine.getRequestTemplates()[tName] ?: return@addActionListener
                protocolCombo.selectedItem = tmpl.protocol
                methodCombo.selectedItem = tmpl.method
                endpointField.text = tmpl.endpoint
                requestBodyArea.text = tmpl.body
            }
        }
        rightHeader.add(templateCombo)
        rightHeader.add(applyTemplateBtn)

        ribbonRow.add(leftHeader, BorderLayout.WEST)
        ribbonRow.add(rightHeader, BorderLayout.EAST)

        // URL Bar
        val urlBar = JPanel(BorderLayout(6, 0)).apply { isOpaque = false }
        val prefixPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        prefixPanel.add(protocolCombo)
        prefixPanel.add(methodCombo)

        val sendBtn = JButton("Send Request").apply {
            font = font.deriveFont(Font.BOLD)
            background = Color(33, 150, 243)
            foreground = Color.BLACK
            addActionListener { dispatchRequest() }
        }

        urlBar.add(prefixPanel, BorderLayout.WEST)
        urlBar.add(endpointField, BorderLayout.CENTER)
        urlBar.add(sendBtn, BorderLayout.EAST)

        topPanel.add(ribbonRow, BorderLayout.NORTH)
        topPanel.add(urlBar, BorderLayout.SOUTH)

        // 2. Center Split (Request Editor + Response Viewer)
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            dividerLocation = 220
            resizeWeight = 0.4
        }

        val requestTabs = JBTabbedPane()
        requestTabs.addTab("Request Body", JBScrollPane(requestBodyArea))

        // Mock Server Routes Tab
        val routesPanel = JPanel(BorderLayout(0, 4))
        routesPanel.add(JBScrollPane(mockRoutesTable), BorderLayout.CENTER)
        val addRouteBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        val newPathField = JBTextField("/api/v1/custom", 12)
        val addRouteBtn = JButton("+ Add Route").apply {
            addActionListener {
                val p = newPathField.text.trim()
                if (p.isNotEmpty()) {
                    ApiWorkbenchEngine.addMockRoute(
                        MockRoute(p, p, HttpMethod.GET, 200, "application/json", "{\"status\":\"custom-ok\"}")
                    )
                    refreshMockRoutesTable()
                }
            }
        }
        addRouteBar.add(JBLabel("Path:"))
        addRouteBar.add(newPathField)
        addRouteBar.add(addRouteBtn)
        routesPanel.add(addRouteBar, BorderLayout.SOUTH)

        requestTabs.addTab("Mock Server Routes", routesPanel)

        // Response Panel
        val responsePanel = JPanel(BorderLayout(0, 4)).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val responseHeader = JPanel(BorderLayout()).apply {
            val title = JBLabel("<b>Response Payload</b>")
            val copyBtn = JButton("Copy Response").apply {
                addActionListener {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(responseBodyArea.text), null)
                    Messages.showInfoMessage("Response copied to clipboard!", "API Workbench")
                }
            }
            add(title, BorderLayout.WEST)
            add(copyBtn, BorderLayout.EAST)
        }
        responsePanel.add(responseHeader, BorderLayout.NORTH)
        responsePanel.add(JBScrollPane(responseBodyArea), BorderLayout.CENTER)

        splitPane.topComponent = requestTabs
        splitPane.bottomComponent = responsePanel

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    private fun dispatchRequest() {
        val proto = protocolCombo.selectedItem as? ApiProtocol ?: ApiProtocol.REST
        val method = methodCombo.selectedItem as? HttpMethod ?: HttpMethod.GET
        val url = endpointField.text.trim()
        val body = requestBodyArea.text

        statusBadge.text = "Sending..."
        statusBadge.foreground = Color.DARK_GRAY

        val req = ApiRequest(
            name = "Interactive Request",
            protocol = proto,
            endpoint = url,
            method = method,
            headers = mapOf("Content-Type" to "application/json"),
            body = body
        )

        // Execute in background
        SwingUtilities.invokeLater {
            val res = ApiWorkbenchEngine.execute(req)
            statusBadge.text = "${res.statusCode} ${res.statusText}"
            statusBadge.foreground = if (res.isError) Color(211, 47, 47) else Color(46, 125, 50)
            latencyBadge.text = "${res.latencyMs} ms"
            sizeBadge.text = "${res.byteSize} B"
            responseBodyArea.text = res.body
            responseBodyArea.caretPosition = 0
        }
    }

    private fun refreshMockRoutesTable() {
        while (mockRoutesModel.rowCount > 0) mockRoutesModel.removeRow(0)
        for (r in ApiWorkbenchEngine.getRegisteredRoutes()) {
            mockRoutesModel.addRow(arrayOf(r.path, r.method.name, r.statusCode.toString(), r.contentType))
        }
    }

    private fun createPill(label: JBLabel, color: Color): JPanel {
        return JPanel(FlowLayout(FlowLayout.CENTER, 4, 1)).apply {
            isOpaque = true
            background = Color(color.red, color.green, color.blue, 35)
            border = LineBorder(color, 1, true)
            label.foreground = color.darker()
            add(label)
        }
    }
}
