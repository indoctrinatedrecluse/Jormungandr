package org.jormungandr.shell.branding

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Frame
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class JormungandrBrandingTest {

    @Test
    fun `test rebrandText replaces all known IntelliJ and JetBrains strings`() {
        assertEquals(
            "Welcome to Jörmungandr",
            JormungandrBranding.rebrandText("Welcome to IntelliJ IDEA")
        )
        assertEquals(
            "Jörmungandr",
            JormungandrBranding.rebrandText("IntelliJ IDEA Community Edition")
        )
        assertEquals(
            "About Jörmungandr",
            JormungandrBranding.rebrandText("About IntelliJ IDEA")
        )
        assertEquals(
            "Indoctrinated Recluse",
            JormungandrBranding.rebrandText("JetBrains s.r.o.")
        )
        assertEquals(
            "Jörmungandr Data Science",
            JormungandrBranding.rebrandText("JetBrains Data Science")
        )
    }

    @Test
    fun `test patchApplicationNamesInfo reflectively updates product identity`() {
        JormungandrBranding.patchApplicationNamesInfo()

        val namesInfo = ApplicationNamesInfo.getInstance()
        assertEquals("Jörmungandr", namesInfo.productName)
        assertEquals("Jörmungandr", namesInfo.fullProductName)
        assertEquals("jormungandr", namesInfo.scriptName)
    }

    @Test
    fun `test patchApplicationInfo reflectively updates company metadata`() {
        JormungandrBranding.patchApplicationInfo()

        val appInfo = ApplicationInfoImpl.getShadowInstance()
        assertEquals("Indoctrinated Recluse", appInfo.companyName)
        assertEquals("Indoctrinated Recluse", appInfo.shortCompanyName)
        assertEquals("/icons/jormungandr.svg", appInfo.applicationSvgIconUrl)
        assertEquals("/icons/jormungandr_16.svg", appInfo.smallApplicationSvgIconUrl)
    }

    @Test
    fun `test brandWindow updates frame title, icons, and child components`() {
        val frame = JFrame("Welcome to IntelliJ IDEA")
        val panel = JPanel()
        val label1 = JLabel("Welcome to IntelliJ IDEA")
        val label2 = JLabel("Copyright JetBrains s.r.o.")
        panel.add(label1)
        panel.add(label2)
        frame.contentPane.add(panel)

        // Run brandWindow synchronously on EDT
        SwingUtilities.invokeAndWait {
            JormungandrBranding.brandWindow(frame)
        }

        // Wait for nested invokeLater to complete
        SwingUtilities.invokeAndWait {
            assertTrue(frame.title.startsWith("Welcome to Jörmungandr"), "Frame title must start with rebranded product name")
            assertFalse(frame.iconImages.isEmpty(), "Frame iconImages must be populated with Jörmungandr icons")
            assertEquals("Welcome to Jörmungandr", label1.text)
            assertEquals("Copyright Indoctrinated Recluse", label2.text)
        }
    }

    @Test
    fun `test patchConsentOptions prevents NPE and returns safe permission`() {
        JormungandrBranding.patchConsentOptions()

        val consentOptionsClass = Class.forName("com.intellij.ide.gdpr.ConsentOptions")
        val getInstance = consentOptionsClass.getMethod("getInstance")
        val instance = getInstance.invoke(null)
        assertNotNull(instance, "ConsentOptions.getInstance() must not be null")

        val isSendingAllowedMethod = consentOptionsClass.getMethod("isSendingUsageStatsAllowed")
        val permission = isSendingAllowedMethod.invoke(instance)
        assertNotNull(permission, "isSendingUsageStatsAllowed() must return valid Permission enum without NPE")
    }

    @Test
    fun `test dynamic rebranding catches lazily added Welcome Screen components`() {
        val frame = JFrame("Welcome")
        frame.contentPane.layout = java.awt.BorderLayout()

        SwingUtilities.invokeAndWait {
            JormungandrBranding.brandWindow(frame)
        }

        val lazyPanel = JPanel()
        val lazyTitle = JLabel("Welcome to IntelliJ IDEA")
        val lazyEditor = javax.swing.JEditorPane()
        lazyEditor.text = "Open existing project in IntelliJ IDEA"
        lazyPanel.add(lazyTitle)
        lazyPanel.add(lazyEditor)

        SwingUtilities.invokeAndWait {
            frame.contentPane.add(lazyPanel, java.awt.BorderLayout.CENTER)
            frame.contentPane.revalidate()
        }

        SwingUtilities.invokeAndWait {
            assertEquals("Welcome to Jörmungandr", lazyTitle.text, "Lazily added title label must be dynamically rebranded")
            assertEquals("Open existing project in Jörmungandr", lazyEditor.text, "Lazily added editor text must be dynamically rebranded")
        }
    }

    @Test
    fun `test rebrandSingleComponent replaces welcome logo icon`() {
        val logoLabel = JLabel()
        logoLabel.name = "productIcon"
        assertNull(logoLabel.icon)

        JormungandrBranding.rebrandSingleComponent(logoLabel)
        assertNotNull(logoLabel.icon, "Logo label icon must be populated with Jörmungandr icon")
    }
}
