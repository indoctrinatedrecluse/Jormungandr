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

package org.jormungandr.shell.project

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import org.jormungandr.shell.icon.JormungandrIcons

/**
 * Quickstart action allowing instant opening of CSV, Parquet, or Arrow datasets in the DataFrame Viewer.
 */
class QuickOpenDatasetAction : AnAction(
    "Quick Open Dataset (Parquet / CSV / Arrow)...",
    "Open a local dataset file directly into the virtualized DataFrame Viewer",
    JormungandrIcons.APP_ICON
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withFileFilter { file ->
                val ext = file.extension?.lowercase()
                ext in listOf("parquet", "csv", "tsv", "arrow", "feather", "json")
            }
            .withTitle("Select Dataset to Open")

        val chosen = FileChooser.chooseFile(descriptor, project, null)
        if (chosen != null) {
            FileEditorManager.getInstance(project).openFile(chosen, true)
        }
    }
}
