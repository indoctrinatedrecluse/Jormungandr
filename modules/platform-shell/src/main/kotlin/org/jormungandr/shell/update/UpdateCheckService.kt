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

                if (!AppVersion.isNewer(cleanLatest, AppVersion.CURRENT)) {
                    LOG.info("Jörmungandr is up to date: installed=${AppVersion.CURRENT}, latest=$cleanLatest")
                    onResult(null)
                    return@executeOnPooledThread
                }

                val assets = root.getAsJsonArray("assets") ?: com.google.gson.JsonArray()
                var patchUrl: String? = null
                var patchSize: Long = 0L
                var fullZipUrl: String? = null
                var fullZipSize: Long = 0L

                val patchNameExpected = "Jormungandr-Patch-v${AppVersion.CURRENT}-to-v${cleanLatest}.zip"

                for (item in assets) {
                    val obj = item.asJsonObject
                    val name = obj.get("name")?.asString ?: continue
                    val downloadUrl = obj.get("browser_download_url")?.asString ?: continue
                    val size = obj.get("size")?.asLong ?: 0L

                    if (name.equals(patchNameExpected, ignoreCase = true) ||
                        (name.startsWith("Jormungandr-Patch-v${AppVersion.CURRENT}-") && name.endsWith(".zip", ignoreCase = true))) {
                        patchUrl = downloadUrl
                        patchSize = size
                    } else if (name.endsWith("-windows-x64.zip", ignoreCase = true)) {
                        fullZipUrl = downloadUrl
                        fullZipSize = size
                    }
                }

                val isDelta = patchUrl != null
                val finalDownloadUrl = patchUrl ?: fullZipUrl ?: htmlUrl
                val finalDownloadSize = if (isDelta) patchSize else fullZipSize

                val info = UpdateInfo(
                    currentVersion = AppVersion.CURRENT,
                    latestVersion = cleanLatest,
                    releaseTag = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    htmlUrl = htmlUrl,
                    isDeltaPatch = isDelta,
                    downloadUrl = finalDownloadUrl,
                    downloadSize = finalDownloadSize,
                    fullDownloadUrl = fullZipUrl,
                    fullDownloadSize = fullZipSize
                )

                onResult(info)
            } catch (e: Throwable) {
                LOG.warn("Failed to check for Jörmungandr updates", e)
                onError(e)
            }
        }
    }
}
