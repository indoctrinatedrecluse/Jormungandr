/*
 * Copyright 2025–2026 indoctrinatedrecluse
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

package org.jormungandr.shell.memory

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Interactive Dialog to inspect current JVM memory allocation and apply pre-tuned profiles
 * optimized for Data Science, Deep Learning, and Heavy Notebook workloads.
 */
class MemoryTunerDialog(private val project: Project?) : DialogWrapper(project, true) {

    private val radio4g = JBRadioButton("Standard (4 GB Heap: -Xmx4096m) – Light/Medium tabular datasets")
    private val radio8g = JBRadioButton("Recommended Data Science (8 GB Heap: -Xmx8192m) – Parquet/DuckDB workloads")
    private val radio16g = JBRadioButton("Deep Learning & Lakehouse (16 GB Heap: -Xmx16384m) – Multi-GB datasets & in-memory caches")
    private val radioCustom = JBRadioButton("Current vmoptions configuration (Unchanged)")

    init {
        title = "Jörmungandr Memory & JVM Optimization Tuner"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 16)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(540, 260)
        }

        val runtime = Runtime.getRuntime()
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        val freeMb = runtime.freeMemory() / (1024 * 1024)
        val usedMb = totalMb - freeMb

        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel("<html><b>Current JVM Memory Utilization:</b></html>"))
            add(JBLabel("Heap Allocated: ${usedMb} MB used / ${maxMb} MB maximum limit"))
            add(JBLabel("Garbage Collector: G1GC with String Deduplication enabled"))
            add(JBUI.Borders.emptyBottom(12).wrap(JPanel()))
        }

        val group = ButtonGroup()
        group.add(radio4g)
        group.add(radio8g)
        group.add(radio16g)
        group.add(radioCustom)

        when {
            maxMb >= 14000 -> radio16g.isSelected = true
            maxMb >= 7000 -> radio8g.isSelected = true
            maxMb >= 3500 -> radio4g.isSelected = true
            else -> radioCustom.isSelected = true
        }

        val optionsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 1, 0, 0, 0)
            add(JBLabel("<html><br><b>Select JVM Optimization Preset:</b></html>"))
            add(radio4g)
            add(radio8g)
            add(radio16g)
            add(radioCustom)
        }

        root.add(headerPanel, BorderLayout.NORTH)
        root.add(optionsPanel, BorderLayout.CENTER)

        val footerLabel = JBLabel("<html><small>Changes take effect upon IDE restart. Tunings are written to bin/idea64.exe.vmoptions.</small></html>")
        root.add(footerLabel, BorderLayout.SOUTH)

        return root
    }

    override fun doOKAction() {
        val selectedMax = when {
            radio4g.isSelected -> "-Xmx4096m"
            radio8g.isSelected -> "-Xmx8192m"
            radio16g.isSelected -> "-Xmx16384m"
            else -> null
        }

        if (selectedMax != null) {
            applyVmoptionsTuning(selectedMax)
        }
        super.doOKAction()
    }

    private fun applyVmoptionsTuning(newXmx: String) {
        val vmOptionsFiles = listOf(
            File(System.getProperty("idea.home.path", "."), "bin/idea64.exe.vmoptions"),
            File(System.getProperty("idea.home.path", "."), "bin/idea.vmoptions")
        )

        for (file in vmOptionsFiles) {
            if (file.exists() && file.canWrite()) {
                val lines = file.readLines().map { line ->
                    if (line.startsWith("-Xmx")) newXmx else line
                }
                file.writeText(lines.joinToString(System.lineSeparator()))
            }
        }
    }
}
