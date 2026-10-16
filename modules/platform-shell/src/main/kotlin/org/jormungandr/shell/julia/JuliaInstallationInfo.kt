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

package org.jormungandr.shell.julia

/**
 * Metadata descriptor for a detected Julia scientific computing installation.
 */
data class JuliaInstallationInfo(
    val executablePath: String,
    val homePath: String,
    val version: String,
    val installedPackages: Set<String>,
    val activeProject: String,
    val isAvailable: Boolean
) {
    val majorMinorVersion: String
        get() {
            val match = Regex("""(\d+\.\d+(?:\.\d+)?)""").find(version)
            return match?.value ?: "Unknown"
        }

    val hasDataFrames: Boolean
        get() = installedPackages.contains("DataFrames")

    val hasPlots: Boolean
        get() = installedPackages.contains("Plots")

    val hasFlux: Boolean
        get() = installedPackages.contains("Flux")

    val hasDifferentialEquations: Boolean
        get() = installedPackages.contains("DifferentialEquations")

    companion object {
        fun unavailable(message: String = "Julia runtime not detected"): JuliaInstallationInfo {
            return JuliaInstallationInfo(
                executablePath = "",
                homePath = "",
                version = message,
                installedPackages = emptySet(),
                activeProject = "@v1.10",
                isAvailable = false
            )
        }

        fun simulated(): JuliaInstallationInfo {
            return JuliaInstallationInfo(
                executablePath = "julia",
                homePath = "C:\\Users\\RECLUSE\\.julia",
                version = "Julia Version 1.10.4",
                installedPackages = setOf(
                    "DataFrames",
                    "Plots",
                    "Flux",
                    "DifferentialEquations",
                    "CSV",
                    "LinearAlgebra",
                    "BenchmarkTools",
                    "IJulia"
                ),
                activeProject = "@v1.10",
                isAvailable = true
            )
        }
    }
}
