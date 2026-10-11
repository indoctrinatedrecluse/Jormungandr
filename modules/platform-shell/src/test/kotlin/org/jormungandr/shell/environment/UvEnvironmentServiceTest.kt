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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UvEnvironmentServiceTest {

    @Test
    fun `test uv discovery logic`() {
        val service = UvEnvironmentService()
        // Should not throw or crash on any platform
        val uv = service.findUvExecutable()
        val available = service.isUvAvailable()
        assertEquals(uv != null, available)
    }

    @Test
    fun `test environment detection in mock venv`(@TempDir tempDir: File) {
        val service = UvEnvironmentService()

        // Create mock .venv with pyvenv.cfg
        val venvDir = File(tempDir, ".venv")
        venvDir.mkdirs()
        val scriptsDir = File(venvDir, if (System.getProperty("os.name").lowercase().contains("win")) "Scripts" else "bin")
        scriptsDir.mkdirs()
        val pythonExe = File(scriptsDir, if (System.getProperty("os.name").lowercase().contains("win")) "python.exe" else "python")
        pythonExe.writeText("#!/bin/sh\n")

        val pyvenvCfg = File(venvDir, "pyvenv.cfg")
        pyvenvCfg.writeText(
            """
                home = C:\Python312
                include-system-site-packages = false
                version = 3.12.2
                executable = C:\Python312\python.exe
                command = C:\Users\User\.cargo\bin\uv.exe venv .venv
            """.trimIndent()
        )

        // Mock mock project
        val env = service.detectEnvironment(null)
        assertNotNull(env)
        assertTrue(env.versionString.isNotEmpty())
    }
}
