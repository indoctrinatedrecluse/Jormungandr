package org.jormungandr.shell.update

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import java.net.HttpURLConnection
import java.net.URI

private val LOG = logger<UpdateCheckService>()

/**
 * Service that checks GitHub Releases for new Jörmungandr releases and smart delta patches.
 * Adheres strictly to release governance:
 * - If current version matches the upgradeable baseline for the latest release, only the delta patch is selected.
 * - If not, full reinstall package (turnkey installer exe or portable zip) is selected.
 */
class UpdateCheckService {

    companion object {
        fun getInstance(): UpdateCheckService {
            return runCatching {
                ApplicationManager.getApplication()?.getService(UpdateCheckService::class.java)
            }.getOrNull() ?: UpdateCheckService()
        }
    }

    /**
     * Checks asynchronously for updates against GitHub Releases.
     * Invokes [onResult] on EDT or pooled thread with [UpdateInfo] if an update is available, or null if up to date.
     */
    fun checkForUpdates(
        onResult: (UpdateInfo?) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        if (org.jormungandr.shell.offline.OfflineModeService.getInstance().isOfflineModeEnabled) {
            LOG.info("Offline / Air-Gapped mode active - skipping update check.")
            onResult(null)
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val url = URI("https://api.github.com/repos/${AppVersion.REPO_OWNER}/${AppVersion.REPO_NAME}/releases/latest").toURL()
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Jormungandr-IDE/${AppVersion.CURRENT}")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val code = conn.responseCode
                if (code != 200) {
                    LOG.info("GitHub Releases returned HTTP $code when checking for updates.")
                    onResult(null)
                    return@executeOnPooledThread
                }

                val responseJson = conn.inputStream.bufferedReader().use { it.readText() }
                val root = Gson().fromJson(responseJson, JsonObject::class.java)
                val tagName = root.get("tag_name")?.asString ?: ""
                val cleanLatest = AppVersion.cleanVersion(tagName)
                val releaseName = root.get("name")?.asString ?: "Jörmungandr $tagName"
                val releaseNotes = root.get("body")?.asString ?: ""
                val htmlUrl = root.get("html_url")?.asString ?: "https://github.com/${AppVersion.REPO_OWNER}/${AppVersion.REPO_NAME}/releases"

                val currentVer = AppVersion.CURRENT
                if (!AppVersion.isNewer(cleanLatest, currentVer)) {
                    LOG.info("Jörmungandr is up to date: installed=$currentVer, latest=$cleanLatest")
                    onResult(null)
                    return@executeOnPooledThread
                }

                val assets = root.getAsJsonArray("assets") ?: com.google.gson.JsonArray()
                var matchingPatchUrl: String? = null
                var matchingPatchSize: Long = 0L
                var patchFromVer: String? = null
                var anyPatchFromVer: String? = null

                var fullZipUrl: String? = null
                var fullZipSize: Long = 0L
                var installerUrl: String? = null
                var installerSize: Long = 0L

                val patchPattern = Regex("""^Jormungandr-Patch-v([0-9.]+)-to-v([0-9.]+)\.zip$""", RegexOption.IGNORE_CASE)

                for (item in assets) {
                    val obj = item.asJsonObject
                    val name = obj.get("name")?.asString ?: continue
                    val downloadUrl = obj.get("browser_download_url")?.asString ?: continue
                    val size = obj.get("size")?.asLong ?: 0L

                    val match = patchPattern.find(name)
                    if (match != null) {
                        val from = match.groupValues[1]
                        val to = match.groupValues[2]
                        anyPatchFromVer = from
                        if (from == currentVer && to == cleanLatest) {
                            matchingPatchUrl = downloadUrl
                            matchingPatchSize = size
                            patchFromVer = from
                        }
                    } else if (name.endsWith("-windows-x64.zip", ignoreCase = true)) {
                        fullZipUrl = downloadUrl
                        fullZipSize = size
                    } else if (name.endsWith(".exe", ignoreCase = true) && name.contains("Setup", ignoreCase = true)) {
                        installerUrl = downloadUrl
                        installerSize = size
                    }
                }

                // If current version matches the upgradeable version of the patch, select only the patch.
                // Otherwise, select full installer / portable package.
                val isDelta = matchingPatchUrl != null
                val finalDownloadUrl = if (isDelta) {
                    matchingPatchUrl!!
                } else {
                    installerUrl ?: fullZipUrl ?: htmlUrl
                }
                val finalDownloadSize = if (isDelta) {
                    matchingPatchSize
                } else {
                    if (installerUrl != null) installerSize else fullZipSize
                }

                val info = UpdateInfo(
                    currentVersion = currentVer,
                    latestVersion = cleanLatest,
                    releaseTag = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    htmlUrl = htmlUrl,
                    isDeltaPatch = isDelta,
                    downloadUrl = finalDownloadUrl,
                    downloadSize = finalDownloadSize,
                    patchFromVersion = patchFromVer ?: anyPatchFromVer,
                    fullDownloadUrl = fullZipUrl,
                    fullDownloadSize = fullZipSize,
                    installerUrl = installerUrl,
                    installerSize = installerSize
                )

                onResult(info)
            } catch (e: Throwable) {
                LOG.warn("Failed to check for Jörmungandr updates", e)
                onError(e)
            }
        }
    }
}
