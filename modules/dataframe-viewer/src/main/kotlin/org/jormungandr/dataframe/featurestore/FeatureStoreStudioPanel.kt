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

package org.jormungandr.dataframe.featurestore

import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive Feature Store & Time-Travel Lakehouse Studio UI.
 * Facilitates point-in-time leakage-free feature enrichment and historical snapshot checkout.
 */
class FeatureStoreStudioPanel(
    private var activeDataFrame: DataFrame = DataFrame.empty(),
    private val onDataFrameUpdated: ((DataFrame) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val providerCombo = JComboBox(FeatureStoreProvider.values())
    private val viewCombo = JComboBox(FeatureStoreEngine.getFeatureViews().map { it.name }.toTypedArray())

    private val entitiesBadge = JBLabel("Entities: ${FeatureStoreEngine.getEntities().size}")
    private val featuresBadge = JBLabel("Features: 5")
    private val snapshotsBadge = JBLabel("Snapshots: 0")

    private val featuresTableModel = DefaultTableModel(arrayOf("Feature Name", "Data Type", "Description"), 0)
    private val featuresTable = JBTable(featuresTableModel)

    private val snapshotsTableModel = DefaultTableModel(arrayOf("Version", "Timestamp", "Rows", "Operation", "Message"), 0)
    private val snapshotsTable = JBTable(snapshotsTableModel)

    init {
        setupUI()
        updateFeatureViewDetails()
        refreshSnapshotsList()
    }

    fun setDataFrame(df: DataFrame) {
        this.activeDataFrame = df
        if (FeatureStoreEngine.getSnapshots().isEmpty()) {
            FeatureStoreEngine.recordSnapshot(df, "INITIAL_LOAD", "Base observation dataset loaded")
            refreshSnapshotsList()
        }
    }

    private fun setupUI() {
        // 1. Top Ribbon & Toolbar
        val topPanel = JPanel(BorderLayout(0, 4)).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val ribbonRow = JPanel(BorderLayout()).apply { isOpaque = false }
        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>🏪 Feature Store &amp; Time-Travel Lakehouse</b>"))
        leftHeader.add(createPill(entitiesBadge, Color(38, 139, 210)))
        leftHeader.add(createPill(featuresBadge, Color(133, 153, 0)))
        leftHeader.add(createPill(snapshotsBadge, Color(108, 113, 196)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        rightHeader.add(JBLabel("Provider:"))
        rightHeader.add(providerCombo)
        rightHeader.add(JBLabel("Feature View:"))
        rightHeader.add(viewCombo)

        viewCombo.addActionListener { updateFeatureViewDetails() }

        val copyFeastBtn = JButton("Feast Code").apply {
            addActionListener {
                val vName = viewCombo.selectedItem as? String ?: return@addActionListener
                val code = FeatureStoreEngine.generateFeastPythonConfig(vName)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                Messages.showInfoMessage("Feast configuration copied to clipboard!", "Feature Store Export")
            }
        }
        val copyHopsworksBtn = JButton("Hopsworks Code").apply {
            addActionListener {
                val vName = viewCombo.selectedItem as? String ?: return@addActionListener
                val code = FeatureStoreEngine.generateHopsworksPythonConfig(vName)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                Messages.showInfoMessage("Hopsworks configuration copied to clipboard!", "Feature Store Export")
            }
        }
        rightHeader.add(copyFeastBtn)
        rightHeader.add(copyHopsworksBtn)

        ribbonRow.add(leftHeader, BorderLayout.WEST)
        ribbonRow.add(rightHeader, BorderLayout.EAST)
        topPanel.add(ribbonRow, BorderLayout.NORTH)

        // Action Toolbar
        val actionBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply { isOpaque = false }
        val pitJoinBtn = JButton("⚡ Execute Point-in-Time Feature Join").apply {
            font = font.deriveFont(Font.BOLD)
            background = Color(33, 150, 243)
            foreground = Color.BLACK
            addActionListener { executePointInTimeJoin() }
        }
        val rollbackBtn = JButton("⏮️ Rollback to Selected Snapshot").apply {
            addActionListener { rollbackToSelectedSnapshot() }
        }

        actionBar.add(pitJoinBtn)
        actionBar.add(rollbackBtn)
        topPanel.add(actionBar, BorderLayout.SOUTH)

        // 2. Center Split (Features Schema + Time-Travel History)
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            dividerLocation = 480
            resizeWeight = 0.5
        }

        val featuresPanel = JPanel(BorderLayout(0, 4)).apply {
            border = EmptyBorder(6, 8, 6, 8)
            add(JBLabel("<b>Selected Feature View Schema &amp; Storage</b>"), BorderLayout.NORTH)
            add(JBScrollPane(featuresTable), BorderLayout.CENTER)
        }

        val snapshotsPanel = JPanel(BorderLayout(0, 4)).apply {
            border = EmptyBorder(6, 8, 6, 8)
            add(JBLabel("<b>Time-Travel Snapshot Timeline (Delta / Lakehouse Audit)</b>"), BorderLayout.NORTH)
            add(JBScrollPane(snapshotsTable), BorderLayout.CENTER)
        }

        splitPane.leftComponent = featuresPanel
        splitPane.rightComponent = snapshotsPanel

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    private fun updateFeatureViewDetails() {
        val selectedViewName = viewCombo.selectedItem as? String ?: return
        val view = FeatureStoreEngine.getFeatureViews().firstOrNull { it.name == selectedViewName } ?: return

        while (featuresTableModel.rowCount > 0) featuresTableModel.removeRow(0)
        for (f in view.features) {
            featuresTableModel.addRow(arrayOf(f.name, f.dataType, f.description))
        }
        featuresBadge.text = "Features: ${view.features.size}"
    }

    private fun refreshSnapshotsList() {
        while (snapshotsTableModel.rowCount > 0) snapshotsTableModel.removeRow(0)
        val dfFormat = SimpleDateFormat("HH:mm:ss")
        val snList = FeatureStoreEngine.getSnapshots()
        for (s in snList) {
            snapshotsTableModel.addRow(
                arrayOf(
                    "v${s.version}",
                    dfFormat.format(Date(s.timestampMs)),
                    s.rowCount.toString(),
                    s.changeType,
                    s.commitMessage
                )
            )
        }
        snapshotsBadge.text = "Snapshots: ${snList.size}"
    }

    private fun executePointInTimeJoin() {
        val selectedViewName = viewCombo.selectedItem as? String ?: return
        val view = FeatureStoreEngine.getFeatureViews().firstOrNull { it.name == selectedViewName } ?: return

        val config = PointInTimeJoinConfig(
            entityJoinKey = "user_id",
            timestampColumn = "event_timestamp",
            selectedFeatures = view.features.map { it.name }
        )

        val enriched = FeatureStoreEngine.executePointInTimeJoin(activeDataFrame, selectedViewName, config)
        setDataFrame(enriched)
        onDataFrameUpdated?.invoke(enriched)
        refreshSnapshotsList()

        Messages.showInfoMessage(
            "Point-in-Time Join executed without data leakage! Added ${view.features.size} features to DataFrame.",
            "Feature Store Join Complete"
        )
    }

    private fun rollbackToSelectedSnapshot() {
        val row = snapshotsTable.selectedRow
        if (row < 0) {
            Messages.showWarningDialog("Please select a snapshot from the timeline to rollback.", "Time-Travel Rollback")
            return
        }

        val version = snapshotsTable.getValueAt(row, 0).toString().removePrefix("v").toLongOrNull() ?: return
        val restored = FeatureStoreEngine.restoreSnapshot(version)
        if (restored != null) {
            activeDataFrame = restored
            onDataFrameUpdated?.invoke(restored)
            Messages.showInfoMessage("Reverted active DataFrame to snapshot v$version!", "Time-Travel Rollback")
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
