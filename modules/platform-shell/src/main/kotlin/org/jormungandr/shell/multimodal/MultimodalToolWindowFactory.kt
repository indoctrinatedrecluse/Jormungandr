package org.jormungandr.shell.multimodal

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Tool window factory for Multimodal AI Studio (Vision & Audio Analytics).
 */
class MultimodalToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val studioPanel = MultimodalStudioPanel()
        val content = ContentFactory.getInstance().createContent(studioPanel, "Multimodal AI", false)
        toolWindow.contentManager.addContent(content)
    }
}
