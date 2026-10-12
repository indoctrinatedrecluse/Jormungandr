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

package org.jormungandr.shell.samples

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.jormungandr.shell.icon.JormungandrIcons

/**
 * Quickstart action allowing users to open and explore the bundled Jörmungandr samples project.
 */
class OpenSampleProjectAction : AnAction(
    "Explore Bundled Data Science Samples...",
    "Open the curated Jörmungandr samples workspace with notebooks, lakehouses, datasets, and pipelines",
    JormungandrIcons.APP_ICON
) {

    override fun actionPerformed(e: AnActionEvent) {
        val samplesDir = DefaultSamplesInitializer.locateSamplesDirectory()
        if (samplesDir != null && samplesDir.exists()) {
            ProjectUtil.openOrImport(samplesDir.toPath(), e.project, false)
        } else {
            Messages.showWarningDialog(
                e.project,
                "Bundled samples directory could not be located on disk.",
                "Samples Workspace"
            )
        }
    }
}
