package org.jormungandr.shell.lifecycle

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jormungandr.shell.telemetry.HardwareTelemetryService

/**
 * Early shutdown listener that triggers upon IntelliJ IDE exit signal.
 * Ensures all background schedulers, telemetry threads, and child processes
 * are cleanly terminated BEFORE IntelliJ attempts modal settings persistence.
 * Prevents IDE shutdown from hanging on the "Saving Settings" progress modal.
 */
class JormungandrShutdownListener : AppLifecycleListener {

    private val LOG = Logger.getInstance(JormungandrShutdownListener::class.java)

    override fun appClosing() {
        LOG.info("Jörmungandr exit signal received (appClosing). Terminating background daemons to prevent Saving Settings hang...")
        cleanupBackgroundDaemons()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        LOG.info("Jörmungandr appWillBeClosed (isRestart=$isRestart). Finalizing clean shutdown...")
        cleanupBackgroundDaemons()
    }

    private fun cleanupBackgroundDaemons() {
        // 1. Immediately shut down Hardware Telemetry poller
        try {
            val telemetry = ApplicationManager.getApplication().getService(HardwareTelemetryService::class.java)
            telemetry?.dispose()
            LOG.info("Hardware telemetry poller disposed successfully on app closing.")
        } catch (t: Throwable) {
            LOG.warn("Failed to dispose HardwareTelemetryService on shutdown", t)
        }

        // 2. Shut down Jupyter Kernel sessions if the service is loaded
        try {
            val app = ApplicationManager.getApplication()
            val jupyterServiceClass = Class.forName("org.jormungandr.jupyter.kernel.JupyterKernelService")
            val jupyterService = app.getService(jupyterServiceClass)
            if (jupyterService is com.intellij.openapi.Disposable) {
                jupyterService.dispose()
                LOG.info("Jupyter kernel service disposed successfully on app closing.")
            }
        } catch (_: ClassNotFoundException) {
            // Jupyter module not present or not active
        } catch (t: Throwable) {
            LOG.warn("Failed to dispose JupyterKernelService on shutdown", t)
        }
    }
}
