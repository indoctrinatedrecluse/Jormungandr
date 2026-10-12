package org.jormungandr.shell.update

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

private val LOG = logger<UpdateApplyManager>()

/**
 * Handles unpacking of verified patches and launching the detached external updater process.
 */
object UpdateApplyManager {

    /**
     * Unpacks patch or portable zip into staging directory (or launches installer executable directly),
     * spawns the detached updater/installer process, and gracefully exits the IDE.
     */
    fun applyAndRestart(packageFile: File): Boolean {
        return try {
            val ideHome = File(PathManager.getHomePath()).canonicalFile

            // 1. Direct Windows Installer Executable (.exe)
            if (packageFile.name.endsWith(".exe", ignoreCase = true)) {
                LOG.info("Spawning detached turnkey installer: ${packageFile.absolutePath}")
                val pb = ProcessBuilder(packageFile.absolutePath)
                pb.directory(packageFile.parentFile ?: ideHome)
                pb.start()

                // Gracefully terminate current IDE instance
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().exit(true, true, true)
                }
                return true
            }

            // 2. Zip Archive (Delta Patch or Full Portable Distribution)
            val stagingDir = File(ideHome, "update-staging")
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            stagingDir.mkdirs()

            LOG.info("Extracting package archive ${packageFile.absolutePath} to ${stagingDir.absolutePath}...")
            unzip(packageFile, stagingDir)

            // Locate external updater script
            var updaterBat = File(ideHome, "bin/jormungandr-updater.bat")
            if (!updaterBat.exists()) {
                // Check staging or installer fallback
                val stagingBat = File(stagingDir, "files/bin/jormungandr-updater.bat")
                if (stagingBat.exists()) {
                    updaterBat = stagingBat
                } else {
                    // Generate minimal fallback batch updater
                    updaterBat = File(stagingDir, "apply_update.bat")
                    createFallbackUpdater(updaterBat)
                }
            }

            // Locate relaunch executable
            val relaunchExe = when {
                File(ideHome, "Jormungandr.bat").exists() -> File(ideHome, "Jormungandr.bat")
                File(ideHome, "bin/jormungandr64.exe").exists() -> File(ideHome, "bin/jormungandr64.exe")
                File(ideHome, "bin/idea64.exe").exists() -> File(ideHome, "bin/idea64.exe")
                else -> File(ideHome, "bin/idea.bat")
            }

            val currentPid = ProcessHandle.current().pid()

            LOG.info("Spawning detached updater process: ${updaterBat.absolutePath}")
            val pb = ProcessBuilder(
                "cmd.exe", "/c",
                updaterBat.absolutePath,
                currentPid.toString(),
                ideHome.absolutePath,
                stagingDir.absolutePath,
                relaunchExe.absolutePath
            )
            pb.directory(ideHome)
            pb.redirectErrorStream(true)
            pb.start()

            // Gracefully terminate the current IDE instance
            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().exit(true, true, true)
            }
            true
        } catch (e: Throwable) {
            LOG.error("Failed to prepare update handoff", e)
            false
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val targetFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun createFallbackUpdater(batFile: File) {
        batFile.writeText(
            """
            @echo off
            set PID=%1
            set TARGET=%~2
            set STAGING=%~3
            set RELAUNCH=%~4

            echo Waiting for Jormungandr (PID %PID%) to close...
            timeout /t 2 /nobreak >nul
            :WAIT_LOOP
            tasklist /fi "PID eq %PID%" 2>nul | find "%PID%" >nul
            if "%ERRORLEVEL%"=="0" (
                timeout /t 1 /nobreak >nul
                goto WAIT_LOOP
            )
            timeout /t 1 /nobreak >nul

            echo Applying update files...
            if exist "%STAGING%\files" (
                xcopy /s /e /y "%STAGING%\files\*" "%TARGET%\"
            ) else (
                xcopy /s /e /y "%STAGING%\*" "%TARGET%\"
            )

            rd /s /q "%STAGING%" 2>nul
            start "" "%RELAUNCH%"
            """.trimIndent()
        )
    }
}
