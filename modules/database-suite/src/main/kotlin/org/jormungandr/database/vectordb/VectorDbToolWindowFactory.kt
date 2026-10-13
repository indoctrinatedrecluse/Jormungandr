package org.jormungandr.database.vectordb

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Tool window factory for Vector DB & RAG Semantic Search Studio.
 */
class VectorDbToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val studioPanel = VectorDbStudioPanel()
        val content = ContentFactory.getInstance().createContent(studioPanel, "Vector DB & RAG", false)
        toolWindow.contentManager.addContent(content)
    }
}
