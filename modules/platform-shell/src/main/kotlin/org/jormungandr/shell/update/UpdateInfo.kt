package org.jormungandr.shell.update

/**
 * Encapsulates discovered release metadata, assets, and delta patch feasibility.
 */
data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTag: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val isDeltaPatch: Boolean,
    val downloadUrl: String,
    val downloadSize: Long,
    val fullDownloadUrl: String? = null,
    val fullDownloadSize: Long? = null
) {
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
