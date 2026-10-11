package org.jormungandr.shell.update

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.jormungandr.shell.icon.JormungandrIcons
import javax.swing.SwingUtilities

/**
 * Action allowing users to check GitHub for Jörmungandr updates and delta patches on demand.
 */
class CheckForUpdatesAction : AnAction(
    "Check for Jörmungandr Updates...",
    "Check GitHub for new Jörmungandr releases and smart delta patches",
    JormungandrIcons.APP_ICON
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val service = UpdateCheckService.getInstance()

        service.checkForUpdates(
            onResult = { info ->
                SwingUtilities.invokeLater {
                    if (info != null) {
                        val dialog = UpdateDialog(info, project)
                        dialog.show()
                    } else {
                        Messages.showInfoMessage(
                            project,
                            "You are currently running Jörmungandr v${AppVersion.CURRENT}, which is the latest version available.",
                            "Jörmungandr is Up to Date"
                        )
                    }
                }
            },
            onError = { err ->
                SwingUtilities.invokeLater {
                    Messages.showWarningDialog(
                        project,
                        "Could not check for updates (${err.message}).\n\nPlease check your internet connection or visit https://github.com/${AppVersion.REPO_OWNER}/${AppVersion.REPO_NAME}/releases directly.",
                        "Check for Updates Failed"
                    )
                }
            }
        )
    }
}
