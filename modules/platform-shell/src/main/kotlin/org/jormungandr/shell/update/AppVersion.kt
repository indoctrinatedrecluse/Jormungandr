package org.jormungandr.shell.update

/**
 * Version metadata and semantic version comparison utilities for Jörmungandr.
 */
object AppVersion {
    const val CURRENT = "1.2.0"
    const val REPO_OWNER = "indoctrinatedrecluse"
    const val REPO_NAME = "Jormungandr"

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
