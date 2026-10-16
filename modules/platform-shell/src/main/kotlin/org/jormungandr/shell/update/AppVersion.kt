package org.jormungandr.shell.update

import java.io.File

/**
 * Version metadata and semantic version comparison utilities for Jörmungandr.
 * Dynamically resolves the active version from disk or installation metadata,
 * ensuring that after any update (patch, full zip, or installer), the version
 * immediately reflects the newly applied release.
 */
object AppVersion {
    const val DEFAULT_VERSION = "1.6.0"
    const val REPO_OWNER = "indoctrinatedrecluse"
    const val REPO_NAME = "Jormungandr"

    @Volatile
    var overrideVersionForTesting: String? = null

    /**
     * The active version string of the Jörmungandr IDE.
     * Evaluated dynamically to guarantee immediate reflection of updates.
     */
    val CURRENT: String
        get() = overrideVersionForTesting ?: resolveCurrentVersion()

    private fun resolveCurrentVersion(): String {
        // 1. JVM system property override
        System.getProperty("jormungandr.version")?.trim()?.takeIf { it.isNotBlank() }?.let {
            return cleanVersion(it)
        }

        // 2. Read jormungandr-version.txt from IDE home or bin/
        try {
            val homePath = com.intellij.openapi.application.PathManager.getHomePath()
            if (!homePath.isNullOrBlank()) {
                val homeDir = File(homePath)
                val versionFile = File(homeDir, "jormungandr-version.txt")
                if (versionFile.exists()) {
                    val content = versionFile.readText().trim()
                    if (content.isNotBlank()) return cleanVersion(content)
                }

                val binVersionFile = File(homeDir, "bin/jormungandr-version.txt")
                if (binVersionFile.exists()) {
                    val content = binVersionFile.readText().trim()
                    if (content.isNotBlank()) return cleanVersion(content)
                }

                // Check product-info.json for custom Jörmungandr version tag
                val productInfo = File(homeDir, "product-info.json")
                if (productInfo.exists()) {
                    val text = productInfo.readText()
                    val match = Regex("\"jormungandrVersion\"\\s*:\\s*\"([^\"]+)\"").find(text)
                    if (match != null) {
                        return cleanVersion(match.groupValues[1])
                    }
                }
            }
        } catch (_: Throwable) {
            // PathManager or file read might not be available during detached startup or tests
        }

        // 3. Plugin descriptor version
        try {
            val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
                com.intellij.openapi.extensions.PluginId.getId("org.jormungandr.ide")
            )
            val ver = plugin?.version
            if (!ver.isNullOrBlank()) {
                return cleanVersion(ver)
            }
        } catch (_: Throwable) {
            // Plugin manager not available
        }

        return DEFAULT_VERSION
    }

    /**
     * Determines whether [latestTag] represents a newer version than [currentTag].
     */
    fun isNewer(latestTag: String, currentTag: String = CURRENT): Boolean {
        val latest = cleanVersion(latestTag)
        val current = cleanVersion(currentTag)
        if (latest == current) return false

        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun cleanVersion(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")
}
