package org.jormungandr.shell.update

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

/**
 * Interactive dialog displaying available Jörmungandr updates, delta patch benefits,
 * download progress bar, and 1-click restart & apply update action.
 */
class UpdateDialog(
    private val info: UpdateInfo,
    project: Project? = null
) : DialogWrapper(project, true) {

    private val downloadManager = UpdateDownloadManager()
    private var downloadedFile: File? = null

    private val statusLabel = JLabel("Ready to download update package.")
    private val progressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
        string = "0%"
        preferredSize = Dimension(520, 20)
    }

    private val primaryActionBtn = JButton("⬇️ Download & Update").apply {
        font = font.deriveFont(Font.BOLD, 12f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private val cancelBtn = JButton("Later").apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    init {
        title = "Jörmungandr Software Update"
        isResizable = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(16, 20, 16, 20)
            preferredSize = Dimension(580, 480)
        }

        // 1. Top Header Row
        val header = JPanel(BorderLayout(14, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val iconLabel = JLabel(JormungandrIcons.LOGO_64).apply {
            verticalAlignment = SwingConstants.TOP
        }
        header.add(iconLabel, BorderLayout.WEST)

        val headerText = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val titleLabel = JBLabel("New Version Available: v${info.latestVersion}").apply {
            font = font.deriveFont(Font.BOLD, 17f)
            foreground = Color(15, 23, 42)
        }
        val subLabel = JBLabel("Jörmungandr ${info.releaseTag} is available for installation.").apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = Color(100, 116, 139)
        }
        headerText.add(titleLabel)
        headerText.add(Box.createVerticalStrut(4))
        headerText.add(subLabel)
        header.add(headerText, BorderLayout.CENTER)
        root.add(header)
        root.add(Box.createVerticalStrut(14))

        // 2. Version & Patch Badge Info Panel
        val badgePanel = JPanel(BorderLayout()).apply {
            background = Color(248, 250, 252)
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1, true),
                EmptyBorder(10, 12, 10, 12)
            )
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val badgeLeft = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val currentTag = JLabel("Installed: v${info.currentVersion}").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(71, 85, 105)
        }
        val arrow = JLabel("➔").apply { foreground = Color(148, 163, 184) }
        val nextTag = JLabel("Target: v${info.latestVersion}").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(37, 99, 235)
        }
        badgeLeft.add(currentTag)
        badgeLeft.add(arrow)
        badgeLeft.add(nextTag)

        val badgeRight = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val patchTypeBadge = if (info.isDeltaPatch) {
            JLabel("⚡ Smart Delta Patch: ${info.formatSize(info.downloadSize)}").apply {
                font = font.deriveFont(Font.BOLD, 11f)
                foreground = Color(22, 101, 52) // Dark Green
            }
        } else {
            JLabel("📦 Full Distribution: ${info.formatSize(info.downloadSize)}").apply {
                font = font.deriveFont(Font.BOLD, 11f)
                foreground = Color(180, 83, 9)
            }
        }
        badgeRight.add(patchTypeBadge)

        badgePanel.add(badgeLeft, BorderLayout.WEST)
        badgePanel.add(badgeRight, BorderLayout.EAST)
        root.add(badgePanel)
        root.add(Box.createVerticalStrut(10))

        if (info.isDeltaPatch) {
            val savingsNote = JLabel("✓ High-efficiency delta update: Downloads only new & modified files, preserving full speed and low bandwidth.").apply {
                font = font.deriveFont(Font.PLAIN, 10.5f)
                foreground = Color(22, 101, 52)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            root.add(savingsNote)
            root.add(Box.createVerticalStrut(8))
        }

        // 3. Release Notes / Changelog
        val notesHeader = JLabel("Release Notes:").apply {
            font = font.deriveFont(Font.BOLD, 11.5f)
            foreground = Color(51, 65, 85)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        root.add(notesHeader)
        root.add(Box.createVerticalStrut(4))

        val notesArea = JTextArea(info.releaseNotes.ifBlank { "No release notes provided." }).apply {
            isEditable = false
            font = Font("Segoe UI", Font.PLAIN, 11)
            lineWrap = true
            wrapStyleWord = true
            background = Color(255, 255, 255)
            margin = Insets(8, 8, 8, 8)
            caretPosition = 0
        }
        val notesScroll = JBScrollPane(notesArea).apply {
            border = LineBorder(Color(226, 232, 240), 1)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        root.add(notesScroll)
        root.add(Box.createVerticalStrut(12))

        // 4. Progress Section
        val progressBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        statusLabel.font = statusLabel.font.deriveFont(Font.PLAIN, 11f)
        statusLabel.foreground = Color(71, 85, 105)
        progressBox.add(statusLabel)
        progressBox.add(Box.createVerticalStrut(4))
        progressBox.add(progressBar)
        root.add(progressBox)
        root.add(Box.createVerticalStrut(12))

        // 5. Actions Footer
        val footer = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = MatteBorder(1, 0, 0, 0, Color(241, 245, 249))
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val footerRight = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8)).apply { isOpaque = false }
        cancelBtn.addActionListener {
            downloadManager.cancel()
            close(CANCEL_EXIT_CODE)
        }

        primaryActionBtn.addActionListener {
            if (downloadedFile != null) {
                // Apply update and restart
                val success = UpdateApplyManager.applyAndRestart(downloadedFile!!)
                if (success) {
                    close(OK_EXIT_CODE)
                } else {
                    Messages.showErrorDialog(
                        "Failed to launch external updater handoff. Please check system permissions or try the standalone installer.",
                        "Update Failed"
                    )
                }
            } else {
                startDownload()
            }
        }

        footerRight.add(cancelBtn)
        footerRight.add(primaryActionBtn)
        footer.add(footerRight, BorderLayout.EAST)
        root.add(footer)

        return root
    }

    override fun createActions(): Array<Action> = emptyArray() // Handled in custom footer

    private fun startDownload() {
        primaryActionBtn.isEnabled = false
        primaryActionBtn.text = "Downloading..."
        cancelBtn.text = "Cancel"
        statusLabel.text = "Connecting to GitHub Releases server..."

        Thread {
            val file = downloadManager.download(info.downloadUrl, info.downloadSize) { bytesRead, totalBytes, percent ->
                SwingUtilities.invokeLater {
                    progressBar.value = percent
                    progressBar.string = "$percent%"
                    val readStr = info.formatSize(bytesRead)
                    val totalStr = info.formatSize(totalBytes)
                    statusLabel.text = "Downloading patch: $readStr / $totalStr ($percent%)..."
                }
            }

            SwingUtilities.invokeLater {
                if (file != null && file.exists()) {
                    downloadedFile = file
                    statusLabel.text = "✅ Download verified! Ready to apply patch."
                    statusLabel.foreground = Color(22, 101, 52)
                    progressBar.value = 100
                    progressBar.string = "Complete (100%)"
                    primaryActionBtn.isEnabled = true
                    primaryActionBtn.text = "🔄 Restart & Apply Update"
                    primaryActionBtn.background = Color(37, 99, 235)
                } else {
                    statusLabel.text = "❌ Download cancelled or failed."
                    statusLabel.foreground = Color(185, 28, 28)
                    primaryActionBtn.isEnabled = true
                    primaryActionBtn.text = "Retry Download"
                }
            }
        }.start()
    }
}
