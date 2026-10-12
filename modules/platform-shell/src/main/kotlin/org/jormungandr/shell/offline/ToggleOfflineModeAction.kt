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

package org.jormungandr.shell.offline

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * Action to toggle air-gapped / offline development mode.
 */
class ToggleOfflineModeAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean {
        return OfflineModeService.getInstance().isOfflineModeEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = OfflineModeService.getInstance()
        service.setOfflineModeEnabled(state)

        val project = e.project
        val title = if (state) "Air-Gapped Offline Mode Enabled" else "Online Mode Restored"
        val message = if (state) {
            "External update checks and network telemetry are now disabled. Local package caches and local models are prioritized."
        } else {
            "Standard network access and automatic cloud update checks have been restored."
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jormungandr.Notifications")
            ?.createNotification(title, message, if (state) NotificationType.WARNING else NotificationType.INFORMATION)
            ?.notify(project)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val selected = isSelected(e)
        e.presentation.text = if (selected) "Disable Air-Gapped Mode" else "Enable Air-Gapped / Offline Mode"
        e.presentation.description = "Toggle offline mode to prevent outbound network telemetry and prioritize local ML models"
    }
}
