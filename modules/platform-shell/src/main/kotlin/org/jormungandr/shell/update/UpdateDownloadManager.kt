package org.jormungandr.shell.update

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<UpdateDownloadManager>()

/**
 * Handles streaming download of update packages with progress monitoring and cancellation.
 */
class UpdateDownloadManager {

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    /**
     * Downloads file from [downloadUrl] to a temporary `.zip` file.
     * Invokes [onProgress] with (bytesRead, totalBytes, percent).
     * Returns the downloaded File, or null if cancelled or failed.
     */
    fun download(
        downloadUrl: String,
        expectedSize: Long,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): File? {
        isCancelled.set(false)
        val tempFile = File.createTempFile("jormungandr_patch_", ".zip")
        tempFile.deleteOnExit()

        var currentUrl = downloadUrl
        var redirects = 0
        val maxRedirects = 5

        while (redirects < maxRedirects) {
            val url = URI(currentUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Jormungandr-IDE/${AppVersion.CURRENT}")
            conn.connectTimeout = 12000
            conn.readTimeout = 15000

            val status = conn.responseCode
            if (status in 300..399) {
                val newUrl = conn.getHeaderField("Location") ?: break
                currentUrl = newUrl
                redirects++
                continue
            }

            if (status != 200) {
                LOG.error("Download failed with HTTP status $status from $currentUrl")
                return null
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else expectedSize
            var bytesRead = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (isCancelled.get()) {
                            LOG.info("Download cancelled by user.")
                            tempFile.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                        bytesRead += read
                        val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                        onProgress(bytesRead, totalBytes, percent)
                    }
                }
            }
            return tempFile
        }

        return null
    }
}
