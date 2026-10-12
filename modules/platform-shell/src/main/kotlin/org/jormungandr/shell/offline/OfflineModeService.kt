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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic

/**
 * Service managing Air-Gapped / Offline development mode.
 * When enabled, outbound network calls (update checks, cloud analytics, external telemetry)
 * are blocked or skipped, prioritizing local pip/conda wheels and offline Ollama inference.
 */
@Service(Service.Level.APP)
@State(name = "JormungandrOfflineMode", storages = [Storage("jormungandr-offline.xml")])
class OfflineModeService : PersistentStateComponent<OfflineModeService.State> {

    data class State(
        var offlineModeEnabled: Boolean = false,
        var blockExternalTelemetry: Boolean = true,
        var preferLocalWheels: Boolean = true
    )

    private var myState = State()

    fun interface OfflineModeListener {
        fun onOfflineModeChanged(isEnabled: Boolean)
    }

    val isOfflineModeEnabled: Boolean
        get() = myState.offlineModeEnabled

    fun setOfflineModeEnabled(enabled: Boolean) {
        if (myState.offlineModeEnabled != enabled) {
            myState.offlineModeEnabled = enabled
            ApplicationManager.getApplication().messageBus
                .syncPublisher(TOPIC)
                .onOfflineModeChanged(enabled)
        }
    }

    fun toggleOfflineMode(): Boolean {
        setOfflineModeEnabled(!myState.offlineModeEnabled)
        return myState.offlineModeEnabled
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val TOPIC = Topic.create("JormungandrOfflineModeTopic", OfflineModeListener::class.java)

        fun getInstance(): OfflineModeService =
            ApplicationManager.getApplication().getService(OfflineModeService::class.java)
    }
}
