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

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.concurrent.TimeUnit

data class PythonPackageInfo(
    val name: String,
    val version: String
)

data class PythonEnvironmentInfo(
    val interpreterPath: String,
    val versionString: String,
    val isVirtualEnv: Boolean,
    val isUvManaged: Boolean,
    val envDirectory: File?,
    val packages: List<PythonPackageInfo>
)

data class ProcessExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * High-speed Python environment manager integrating Astral's `uv` fast package manager
 * and local virtual environments.
 */
@Service(Service.Level.APP)
class UvEnvironmentService {

    companion object {
        private val LOG = Logger.getInstance(UvEnvironmentService::class.java)
        fun getInstance(): UvEnvironmentService = service()
    }

    /**
     * Resolves the `uv` executable location from PATH or default user tooling paths.
     */
    fun findUvExecutable(): String? {
        val exeName = if (SystemInfo.isWindows) "uv.exe" else "uv"

        // 1. Check PATH
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
        for (dir in pathDirs) {
            val candidate = File(dir, exeName)
            if (candidate.isFile && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }

        // 2. Check ~/.cargo/bin
        val userHome = System.getProperty("user.home")
        val cargoCandidate = File(File(userHome, ".cargo/bin"), exeName)
        if (cargoCandidate.isFile && cargoCandidate.canExecute()) {
            return cargoCandidate.absolutePath
        }

        // 3. Check ~/.local/bin
        val localBinCandidate = File(File(userHome, ".local/bin"), exeName)
        if (localBinCandidate.isFile && localBinCandidate.canExecute()) {
            return localBinCandidate.absolutePath
        }

        return null
    }

    fun isUvAvailable(): Boolean = findUvExecutable() != null

    fun getUvVersion(): String? {
        val uv = findUvExecutable() ?: return null
        val result = executeProcess(listOf(uv, "--version"), null, 5)
        return if (result.isSuccess) result.stdout.trim() else null
    }

    /**
     * Introspects the active project's Python environment (.venv or system runtime).
     */
    fun detectEnvironment(project: Project?): PythonEnvironmentInfo {
        val baseDir = project?.basePath?.let { File(it) }
        val venvDir = baseDir?.let { findProjectVenv(it) }

        if (venvDir != null && venvDir.isDirectory) {
            val pythonExe = findVenvPython(venvDir)
            val versionStr = readPythonVersionFromCfg(venvDir) ?: "Python 3.x"
            val isUv = isUvManagedVenv(venvDir)
            val packages = listInstalledPackages(pythonExe, venvDir)

            return PythonEnvironmentInfo(
                interpreterPath = pythonExe.absolutePath,
                versionString = versionStr,
                isVirtualEnv = true,
                isUvManaged = isUv,
                envDirectory = venvDir,
                packages = packages
            )
        }

        // Fallback to system Python
        val systemPython = findSystemPython()
        val version = getSystemPythonVersion(systemPython)
        val packages = listInstalledPackages(File(systemPython), baseDir)

        return PythonEnvironmentInfo(
            interpreterPath = systemPython,
            versionString = version,
            isVirtualEnv = false,
            isUvManaged = false,
            envDirectory = null,
            packages = packages
        )
    }

    /**
     * Creates a new virtual environment using `uv venv` (or `python -m venv` fallback).
     */
    fun createVirtualEnv(projectDir: File, pythonVersion: String? = null): ProcessExecutionResult {
        val uv = findUvExecutable()
        val commands = if (uv != null) {
            if (!pythonVersion.isNullOrBlank()) {
                listOf(uv, "venv", "--python", pythonVersion, ".venv")
            } else {
                listOf(uv, "venv", ".venv")
            }
        } else {
            val python = findSystemPython()
            listOf(python, "-m", "venv", ".venv")
        }

        LOG.info("Creating virtual environment in ${projectDir.absolutePath} using: ${commands.joinToString(" ")}")
        return executeProcess(commands, projectDir, 30)
    }

    /**
     * Adds and installs packages using `uv pip install` or `uv add`.
     */
    fun installPackages(projectDir: File, packages: List<String>): ProcessExecutionResult {
        if (packages.isEmpty()) return ProcessExecutionResult(0, "", "")

        val uv = findUvExecutable()
        val venvDir = findProjectVenv(projectDir)
        val commands = if (uv != null) {
            listOf(uv, "pip", "install") + packages
        } else {
            val pythonExe = venvDir?.let { findVenvPython(it).absolutePath } ?: findSystemPython()
            listOf(pythonExe, "-m", "pip", "install") + packages
        }

        LOG.info("Installing packages in ${projectDir.absolutePath}: ${packages.joinToString(", ")}")
        return executeProcess(commands, projectDir, 60)
    }

    /**
     * Synchronizes virtual environment dependencies against requirements.txt or pyproject.toml.
     */
    fun syncDependencies(projectDir: File): ProcessExecutionResult {
        val uv = findUvExecutable()
        val reqFile = File(projectDir, "requirements.txt")
        val pyprojectFile = File(projectDir, "pyproject.toml")

        val commands = if (uv != null) {
            if (reqFile.exists()) {
                listOf(uv, "pip", "sync", reqFile.name)
            } else if (pyprojectFile.exists()) {
                listOf(uv, "sync")
            } else {
                listOf(uv, "pip", "install", "-r", "requirements.txt")
            }
        } else {
            val venvDir = findProjectVenv(projectDir)
            val pythonExe = venvDir?.let { findVenvPython(it).absolutePath } ?: findSystemPython()
            listOf(pythonExe, "-m", "pip", "install", "-r", "requirements.txt")
        }

        return executeProcess(commands, projectDir, 60)
    }

    private fun findProjectVenv(projectDir: File): File? {
        val candidates = listOf(".venv", "venv", "env", ".env")
        for (name in candidates) {
            val dir = File(projectDir, name)
            if (dir.isDirectory && findVenvPython(dir).exists()) {
                return dir
            }
        }
        return null
    }

    private fun findVenvPython(venvDir: File): File {
        return if (SystemInfo.isWindows) {
            File(venvDir, "Scripts/python.exe")
        } else {
            File(venvDir, "bin/python")
        }
    }

    private fun isUvManagedVenv(venvDir: File): Boolean {
        val pyvenvCfg = File(venvDir, "pyvenv.cfg")
        if (pyvenvCfg.exists()) {
            val text = runCatching { pyvenvCfg.readText() }.getOrDefault("")
            if (text.contains("uv", ignoreCase = true)) return true
        }
        return findUvExecutable() != null
    }

    private fun readPythonVersionFromCfg(venvDir: File): String? {
        val pyvenvCfg = File(venvDir, "pyvenv.cfg")
        if (pyvenvCfg.exists()) {
            val lines = runCatching { pyvenvCfg.readLines() }.getOrDefault(emptyList())
            for (line in lines) {
                if (line.startsWith("version", ignoreCase = true)) {
                    val v = line.substringAfter("=").trim()
                    return "Python $v"
                }
            }
        }
        return null
    }

    private fun findSystemPython(): String {
        val exe = if (SystemInfo.isWindows) "python.exe" else "python3"
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
            .sortedBy { if (it.contains("WindowsApps", ignoreCase = true)) 1 else 0 }

        for (dir in pathDirs) {
            val candidate = File(dir, exe)
            if (candidate.isFile && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
        return if (SystemInfo.isWindows) "python" else "python3"
    }

    private fun getSystemPythonVersion(pythonPath: String): String {
        val res = executeProcess(listOf(pythonPath, "--version"), null, 5)
        return if (res.isSuccess) res.stdout.trim().ifEmpty { res.stderr.trim() } else "Python 3.x"
    }

    private fun listInstalledPackages(pythonExe: File, workingDir: File?): List<PythonPackageInfo> {
        val uv = findUvExecutable()
        val commands = if (uv != null && workingDir != null) {
            listOf(uv, "pip", "list")
        } else {
            listOf(pythonExe.absolutePath, "-m", "pip", "list")
        }

        val result = executeProcess(commands, workingDir, 10)
        if (!result.isSuccess) return emptyList()

        val packages = mutableListOf<PythonPackageInfo>()
        val lines = result.stdout.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("Package") || trimmed.startsWith("---")) continue
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 2) {
                packages.add(PythonPackageInfo(parts[0], parts[1]))
            }
        }
        return packages
    }

    private fun executeProcess(commands: List<String>, workingDir: File?, timeoutSeconds: Long): ProcessExecutionResult {
        return try {
            val pb = ProcessBuilder(commands)
            if (workingDir != null && workingDir.isDirectory) {
                pb.directory(workingDir)
            }
            pb.redirectErrorStream(false)
            val process = pb.start()

            val stdoutStream = process.inputStream.bufferedReader().use { it.readText() }
            val stderrStream = process.errorStream.bufferedReader().use { it.readText() }

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                ProcessExecutionResult(-1, stdoutStream, "Process timed out after $timeoutSeconds seconds")
            } else {
                ProcessExecutionResult(process.exitValue(), stdoutStream, stderrStream)
            }
        } catch (e: Exception) {
            ProcessExecutionResult(-1, "", e.message ?: "Unknown process error")
        }
    }
}
