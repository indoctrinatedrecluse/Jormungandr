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

package org.jormungandr.shell.environment

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Status bar widget displaying the active Python runtime, `uv` status, and package count.
 */
class PythonEnvironmentStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    companion object {
        const val WIDGET_ID = "org.jormungandr.shell.environment.PythonEnvironmentStatusBarWidget"
    }

    private val uvService = UvEnvironmentService.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val label = JBLabel("🐍 Python: Discovering...").apply {
        border = JBUI.Borders.empty(0, 4)
        font = font.deriveFont(11f)
    }

    private val panel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Active Python Environment & uv Fast Package Manager (Click to manage)"
        add(label)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showEnvironmentMenu(e.component, e.x, e.y)
            }
        })
    }

    private var activeEnv: PythonEnvironmentInfo? = null

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent = panel

    override fun install(statusBar: StatusBar) {
        refreshEnvironment()
    }

    override fun dispose() {
        scope.cancel()
    }

    fun refreshEnvironment() {
        scope.launch {
            val env = uvService.detectEnvironment(project)
            activeEnv = env
            withContext(Dispatchers.Main) {
                val envType = when {
                    env.isUvManaged -> "uv .venv"
                    env.isVirtualEnv -> ".venv"
                    else -> "System"
                }
                val pkgCount = if (env.packages.isNotEmpty()) " • ${env.packages.size} pkgs" else ""
                label.text = "🐍 ${env.versionString} ($envType)$pkgCount"
                label.foreground = if (env.isUvManaged) Color(16, 185, 129) else JBColor.foreground()
            }
        }
    }

    private fun showEnvironmentMenu(invoker: Component, x: Int, y: Int) {
        val popup = JPopupMenu()
        val env = activeEnv

        val titleItem = JMenuItem("🐍 Python Environment (${env?.versionString ?: "Unknown"})").apply {
            isEnabled = false
            font = font.deriveFont(Font.BOLD)
        }
        popup.add(titleItem)
        popup.addSeparator()

        val inspectItem = JMenuItem("🔍 Inspect Installed Packages (${env?.packages?.size ?: 0})...").apply {
            addActionListener { showPackagesDialog() }
        }
        popup.add(inspectItem)

        val installItem = JMenuItem("⚡ Fast Install Package (uv add / pip)...").apply {
            addActionListener { promptInstallPackage() }
        }
        popup.add(installItem)

        val syncItem = JMenuItem("🔄 Sync Dependencies (uv pip sync)...").apply {
            addActionListener { triggerSyncDependencies() }
        }
        popup.add(syncItem)

        val initVenvItem = JMenuItem("🌱 Initialize Clean .venv with uv...").apply {
            addActionListener { triggerCreateVenv() }
        }
        popup.add(initVenvItem)

        popup.addSeparator()

        val copyPathItem = JMenuItem("📋 Copy Interpreter Path").apply {
            addActionListener {
                val path = env?.interpreterPath ?: ""
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(path), null)
                Messages.showInfoMessage(project, "Interpreter path copied:\n$path", "Copied")
            }
        }
        popup.add(copyPathItem)

        val refreshItem = JMenuItem("🔄 Refresh Environment").apply {
            addActionListener { refreshEnvironment() }
        }
        popup.add(refreshItem)

        popup.show(invoker, x, y)
    }

    private fun promptInstallPackage() {
        val baseDir = project.basePath?.let { File(it) } ?: return
        val pkg = Messages.showInputDialog(
            project,
            "Enter Python package name to install (e.g., pandas, polars, torch):",
            "Fast Install Package (uv)",
            Messages.getQuestionIcon()
        )?.trim() ?: return

        if (pkg.isNotEmpty()) {
            scope.launch {
                val res = uvService.installPackages(baseDir, listOf(pkg))
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        Messages.showInfoMessage(project, "Package '$pkg' successfully installed!", "Package Installed")
                        refreshEnvironment()
                    } else {
                        Messages.showErrorDialog(project, "Failed to install '$pkg':\n${res.stderr}", "Installation Error")
                    }
                }
            }
        }
    }

    private fun triggerSyncDependencies() {
        val baseDir = project.basePath?.let { File(it) } ?: return
        scope.launch {
            val res = uvService.syncDependencies(baseDir)
            withContext(Dispatchers.Main) {
                if (res.isSuccess) {
                    Messages.showInfoMessage(project, "Dependencies successfully synchronized!", "Sync Complete")
                    refreshEnvironment()
                } else {
                    Messages.showErrorDialog(project, "Sync failed:\n${res.stderr}", "Sync Error")
                }
            }
        }
    }

    private fun triggerCreateVenv() {
        val baseDir = project.basePath?.let { File(it) } ?: return
        val confirm = Messages.showYesNoDialog(
            project,
            "Create a clean virtual environment (.venv) in '${baseDir.name}'?",
            "Create Virtual Environment",
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.YES) {
            scope.launch {
                val res = uvService.createVirtualEnv(baseDir)
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        Messages.showInfoMessage(project, "Virtual environment created in .venv!", "Environment Ready")
                        refreshEnvironment()
                    } else {
                        Messages.showErrorDialog(project, "Failed to create venv:\n${res.stderr}", "Creation Error")
                    }
                }
            }
        }
    }

    private fun showPackagesDialog() {
        val env = activeEnv ?: return
        val model = DefaultTableModel(arrayOf("Package", "Version"), 0)
        for (p in env.packages) {
            model.addRow(arrayOf(p.name, p.version))
        }

        val table = JBTable(model).apply {
            rowHeight = 22
        }

        val panel = JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(420, 360)
            val info = JBLabel("Total Packages: ${env.packages.size} | Path: ${env.interpreterPath}").apply {
                font = font.deriveFont(11f)
            }
            add(info, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

        JOptionPane.showMessageDialog(
            null,
            panel,
            "Installed Packages - ${env.versionString}",
            JOptionPane.PLAIN_MESSAGE
        )
    }
}

class PythonEnvironmentStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = PythonEnvironmentStatusBarWidget.WIDGET_ID
    override fun getDisplayName(): String = "Python Runtime & uv Manager"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = PythonEnvironmentStatusBarWidget(project)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
