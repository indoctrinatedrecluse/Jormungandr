package org.jormungandr.shell.license

import org.jormungandr.core.license.LicenseInfo
import org.jormungandr.core.license.LicenseStatus
import org.jormungandr.core.license.LicenseType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class LicenseBadgeFactoryTest {

    @Test
    fun `test badge components created for all license tiers`() {
        for (tier in LicenseType.values()) {
            val info = LicenseInfo(
                licenseKey = "${tier.prefix}-TEST-KEY",
                licenseType = tier,
                username = "Tester",
                hwid = "TEST-HWID-1234",
                status = LicenseStatus.ACTIVE,
                activatedAt = System.currentTimeMillis(),
                expiresAt = null,
                trialDaysRemaining = 45,
                graceDaysLeft = null,
                isActivated = tier != LicenseType.TRIAL,
                message = "Active"
            )

            val badgeSmall = LicenseBadgeFactory.createBadgeComponent(info, large = false)
            assertNotNull(badgeSmall)
            assertTrue(badgeSmall.preferredSize.width > 20)
            assertTrue(badgeSmall.preferredSize.height >= 20)

            val badgeLarge = LicenseBadgeFactory.createBadgeComponent(info, large = true)
            assertNotNull(badgeLarge)
            assertTrue(badgeLarge.preferredSize.width > badgeSmall.preferredSize.width)
            assertTrue(badgeLarge.preferredSize.height >= 24)

            // Test painting into an image without throwing any exceptions
            val img = BufferedImage(badgeLarge.preferredSize.width, badgeLarge.preferredSize.height, BufferedImage.TYPE_INT_ARGB)
            val g2 = img.createGraphics()
            badgeLarge.setSize(badgeLarge.preferredSize.width, badgeLarge.preferredSize.height)
            badgeLarge.paint(g2)
            g2.dispose()

            // Verify icon creation
            val icon = LicenseBadgeFactory.createBadgeIcon(tier, 16)
            assertEquals(16, icon.iconWidth)
            assertEquals(16, icon.iconHeight)
            val iconImg = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val iconG2 = iconImg.createGraphics()
            icon.paintIcon(null, iconG2, 0, 0)
            iconG2.dispose()
        }
    }
}
