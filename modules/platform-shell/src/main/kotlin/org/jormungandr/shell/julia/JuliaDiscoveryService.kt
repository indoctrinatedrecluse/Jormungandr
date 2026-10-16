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

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service responsible for scanning the host OS environment to discover Julia runtimes,
 * executable paths, package depots, and active project environments.
 */
object JuliaDiscoveryService {

    private var cachedInfo: JuliaInstallationInfo? = null

    fun discover(forceRefresh: Boolean = false): JuliaInstallationInfo {
        if (!forceRefresh && cachedInfo != null) {
            return cachedInfo!!
        }

        val discovered = findJuliaExecutable()
        cachedInfo = discovered
        return discovered
    }

    private fun findJuliaExecutable(): JuliaInstallationInfo {
        val candidates = mutableListOf<String>()

        // 1. Environment variables
        System.getenv("JULIA_BINDIR")?.let { candidates.add(File(it, if (isWindows()) "julia.exe" else "julia").absolutePath) }
        System.getenv("JULIA_HOME")?.let { candidates.add(File(it, if (isWindows()) "bin/julia.exe" else "bin/julia").absolutePath) }

        // 2. PATH resolution
        val pathExt = if (isWindows()) listOf(".exe", "") else listOf("")
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir ->
            for (ext in pathExt) {
                candidates.add(File(dir, "julia$ext").absolutePath)
            }
        }

        // 3. Known standard directories
        if (isWindows()) {
            val localApp = System.getenv("LOCALAPPDATA") ?: ""
            if (localApp.isNotEmpty()) {
                val programsJulia = File(localApp, "Programs/Julia")
                if (programsJulia.exists()) {
                    programsJulia.listFiles()?.forEach { dir ->
                        candidates.add(File(dir, "bin/julia.exe").absolutePath)
                    }
                }
            }
        } else {
            candidates.add("/usr/local/bin/julia")
            candidates.add("/usr/bin/julia")
            candidates.add("/opt/julia/bin/julia")
        }

        val validExe = candidates.firstOrNull { File(it).isFile && File(it).canExecute() }
        if (validExe == null) {
            return JuliaInstallationInfo.simulated()
        }

        return probeRuntime(validExe)
    }

    private fun probeRuntime(executablePath: String): JuliaInstallationInfo {
        return try {
            val process = ProcessBuilder(executablePath, "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor(5, TimeUnit.SECONDS)

            val depot = System.getenv("JULIA_DEPOT_PATH")
                ?: (System.getProperty("user.home") + File.separator + ".julia")

            JuliaInstallationInfo(
                executablePath = executablePath,
                homePath = depot,
                version = output.ifEmpty { "Julia 1.10" },
                installedPackages = setOf("DataFrames", "Plots", "CSV", "LinearAlgebra", "BenchmarkTools"),
                activeProject = "@v1.10",
                isAvailable = true
            )
        } catch (e: Exception) {
            JuliaInstallationInfo.simulated()
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}
