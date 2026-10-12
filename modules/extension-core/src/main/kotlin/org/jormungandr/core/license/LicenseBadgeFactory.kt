/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.core.license

import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Creates bespoke, highly stylized badges for USER, DEV, ADMIN, and TRIAL license tiers.
 * Uses dedicated, pure-vector emblems (Royal Crown, Electric Bolt, Verified Shield, Hourglass)
 * rather than font-dependent Unicode emojis to guarantee 100% flawless rendering without
 * missing-glyph square outlines.
 */
object LicenseBadgeFactory {

    /**
     * Creates a custom painted Swing badge component with vector emblem and metallic pill.
     */
    fun createBadgeComponent(info: LicenseInfo, large: Boolean = false): JComponent {
        return object : JComponent() {
            private val labelText = when (info.licenseType) {
                LicenseType.ADMIN -> if (large) "ADMIN PRIVILEGED" else "ADMIN"
                LicenseType.DEVELOPER -> if (large) "DEV EDITION" else "DEV"
                LicenseType.USER -> if (large) "USER LICENSE" else "USER"
                LicenseType.TRIAL -> if (large) "TRIAL (${info.trialDaysRemaining}d LEFT)" else "TRIAL"
            }

            private val badgeFont = Font("Segoe UI", Font.BOLD, if (large) 11 else 10)
            private val iconDim = if (large) 13.0 else 10.5
            private val iconGap = if (large) 6 else 5
            private val padLeft = if (large) 10 else 7
            private val padRight = if (large) 12 else 8
            private val badgeHeight = if (large) 26 else 20

            init {
                isOpaque = false
                font = badgeFont
                val fm = getFontMetrics(badgeFont)
                val textWidth = fm.stringWidth(labelText)
                val totalWidth = (padLeft + iconDim + iconGap + textWidth + padRight).toInt()
                preferredSize = Dimension(totalWidth, badgeHeight)
                maximumSize = Dimension(totalWidth, badgeHeight)
                minimumSize = Dimension(totalWidth, badgeHeight)
                toolTipText = "${info.licenseType.displayName} - ${info.message}"
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as? Graphics2D ?: return
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

                val w = width.toDouble()
                val h = height.toDouble()
                val arc = h

                val rect = RoundRectangle2D.Double(1.0, 1.0, w - 2.0, h - 2.0, arc, arc)

                val iconX = padLeft.toDouble()
                val iconY = (h - iconDim) / 2.0

                when (info.licenseType) {
                    // TIER 3: ADMIN (Royal Gold holographic sheen & glowing dual border)
                    LicenseType.ADMIN -> {
                        // Outer subtle glow bloom
                        g2.color = Color(251, 191, 36, 50)
                        g2.fill(RoundRectangle2D.Double(0.0, 0.0, w, h, arc, arc))

                        // Gold metallic gradient
                        val gradient = GradientPaint(
                            0f, 0f, Color(245, 158, 11),
                            w.toFloat(), h.toFloat(), Color(180, 83, 9)
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        // Top sheen
                        val sheen = GradientPaint(
                            0f, 0f, Color(254, 240, 138, 140),
                            0f, (h / 2).toFloat(), Color(254, 240, 138, 0)
                        )
                        g2.paint = sheen
                        g2.fill(rect)

                        // Border
                        g2.stroke = BasicStroke(1.4f)
                        g2.color = Color(254, 240, 138)
                        g2.draw(rect)

                        // Inner contour
                        g2.stroke = BasicStroke(0.7f)
                        g2.color = Color(251, 191, 36, 180)
                        g2.draw(RoundRectangle2D.Double(2.5, 2.5, w - 5.0, h - 5.0, arc - 2, arc - 2))

                        // Vector Crown Emblem
                        drawCrown(g2, iconX, iconY, iconDim, iconDim)

                        // Text
                        drawBadgeText(g2, labelText, (iconX + iconDim + iconGap).toInt(), h, Color(69, 26, 3, 220), Color(255, 255, 255))
                    }

                    // TIER 2: DEVELOPER (Electric Violet & Neon Fuchsia Cyber gradient)
                    LicenseType.DEVELOPER -> {
                        val gradient = GradientPaint(
                            0f, 0f, Color(124, 58, 237),
                            w.toFloat(), h.toFloat(), Color(217, 70, 239)
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        // Sheen
                        val sheen = GradientPaint(
                            0f, 0f, Color(243, 232, 255, 120),
                            0f, (h / 2).toFloat(), Color(243, 232, 255, 0)
                        )
                        g2.paint = sheen
                        g2.fill(rect)

                        g2.stroke = BasicStroke(1.2f)
                        g2.color = Color(216, 180, 254)
                        g2.draw(rect)

                        // Vector Lightning Bolt Emblem
                        drawLightningBolt(g2, iconX, iconY, iconDim, iconDim)

                        // Text
                        drawBadgeText(g2, labelText, (iconX + iconDim + iconGap).toInt(), h, Color(59, 7, 100, 200), Color(255, 255, 255))
                    }

                    // TIER 1: USER (Emerald & Cyan Verified Pro pill)
                    LicenseType.USER -> {
                        val gradient = GradientPaint(
                            0f, 0f, Color(6, 182, 212),
                            w.toFloat(), h.toFloat(), Color(16, 185, 129)
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        // Sheen
                        val sheen = GradientPaint(
                            0f, 0f, Color(207, 250, 254, 120),
                            0f, (h / 2).toFloat(), Color(207, 250, 254, 0)
                        )
                        g2.paint = sheen
                        g2.fill(rect)

                        g2.stroke = BasicStroke(1.1f)
                        g2.color = Color(167, 243, 208)
                        g2.draw(rect)

                        // Vector Verified Shield Emblem
                        drawShield(g2, iconX, iconY, iconDim, iconDim)

                        // Text
                        drawBadgeText(g2, labelText, (iconX + iconDim + iconGap).toInt(), h, Color(4, 47, 46, 200), Color(255, 255, 255))
                    }

                    // TIER 0: TRIAL (Slate & Amber Evaluation pill)
                    LicenseType.TRIAL -> {
                        val gradient = GradientPaint(
                            0f, 0f, Color(71, 85, 105),
                            w.toFloat(), h.toFloat(), Color(100, 116, 139)
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        g2.stroke = BasicStroke(1.0f)
                        g2.color = Color(203, 213, 225)
                        g2.draw(rect)

                        // Vector Hourglass Emblem
                        drawHourglass(g2, iconX, iconY, iconDim, iconDim)

                        // Text
                        drawBadgeText(g2, labelText, (iconX + iconDim + iconGap).toInt(), h, Color(15, 23, 42, 180), Color(254, 215, 170))
                    }
                }
            }

            private fun drawBadgeText(g2: Graphics2D, text: String, startX: Int, totalH: Double, shadowColor: Color, textColor: Color) {
                g2.font = badgeFont
                val fm = g2.fontMetrics
                val textY = (((totalH - fm.height) / 2) + fm.ascent).toInt()

                // Shadow for readability
                g2.color = shadowColor
                g2.drawString(text, startX + 1, textY + 1)

                // Foreground text
                g2.color = textColor
                g2.drawString(text, startX, textY)
            }
        }
    }

    /**
     * Draws a royal gold crown emblem using vector paths.
     */
    fun drawCrown(g2: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        val crown = Path2D.Double().apply {
            moveTo(x + w * 0.08, y + h * 0.88)
            lineTo(x + w * 0.92, y + h * 0.88)
            lineTo(x + w, y + h * 0.32)
            lineTo(x + w * 0.73, y + h * 0.58)
            lineTo(x + w * 0.50, y + h * 0.12)
            lineTo(x + w * 0.27, y + h * 0.58)
            lineTo(x, y + h * 0.32)
            closePath()
        }

        // Fill crown body
        val goldGrad = GradientPaint(
            x.toFloat(), y.toFloat(), Color(254, 240, 138),
            (x + w).toFloat(), (y + h).toFloat(), Color(251, 191, 36)
        )
        g2.paint = goldGrad
        g2.fill(crown)

        // Outline
        g2.stroke = BasicStroke(0.9f)
        g2.color = Color(255, 255, 255, 230)
        g2.draw(crown)

        // Top jewels (3 dots on crown tips)
        val r = (w * 0.13).coerceAtLeast(1.5)
        g2.color = Color(255, 255, 255)
        g2.fillOval((x - r / 2).toInt(), (y + h * 0.32 - r / 2).toInt(), (r * 1.6).toInt(), (r * 1.6).toInt())
        g2.fillOval((x + w * 0.50 - r * 0.8).toInt(), (y + h * 0.12 - r * 0.8).toInt(), (r * 1.6).toInt(), (r * 1.6).toInt())
        g2.fillOval((x + w - r / 2 - 1).toInt(), (y + h * 0.32 - r / 2).toInt(), (r * 1.6).toInt(), (r * 1.6).toInt())
    }

    /**
     * Draws an electric lightning bolt emblem using vector paths.
     */
    fun drawLightningBolt(g2: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        val bolt = Path2D.Double().apply {
            moveTo(x + w * 0.62, y)
            lineTo(x + w * 0.14, y + h * 0.54)
            lineTo(x + w * 0.50, y + h * 0.54)
            lineTo(x + w * 0.36, y + h)
            lineTo(x + w * 0.88, y + h * 0.44)
            lineTo(x + w * 0.52, y + h * 0.44)
            closePath()
        }

        val boltGrad = GradientPaint(
            x.toFloat(), y.toFloat(), Color(255, 255, 255),
            (x + w).toFloat(), (y + h).toFloat(), Color(245, 208, 254)
        )
        g2.paint = boltGrad
        g2.fill(bolt)

        g2.stroke = BasicStroke(0.8f)
        g2.color = Color(255, 255, 255)
        g2.draw(bolt)
    }

    /**
     * Draws a verified shield emblem using vector paths.
     */
    fun drawShield(g2: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        val shield = Path2D.Double().apply {
            moveTo(x + w * 0.5, y)
            lineTo(x + w * 0.95, y + h * 0.22)
            lineTo(x + w * 0.95, y + h * 0.55)
            quadTo(x + w * 0.5, y + h * 0.88, x + w * 0.5, y + h)
            quadTo(x + w * 0.5, y + h * 0.88, x + w * 0.05, y + h * 0.55)
            lineTo(x + w * 0.05, y + h * 0.22)
            closePath()
        }

        g2.color = Color(255, 255, 255, 220)
        g2.stroke = BasicStroke(1.0f)
        g2.draw(shield)

        // Inner fill
        val shieldGrad = GradientPaint(
            x.toFloat(), y.toFloat(), Color(167, 243, 208),
            (x + w).toFloat(), (y + h).toFloat(), Color(6, 182, 212)
        )
        g2.paint = shieldGrad
        g2.fill(shield)

        // Checkmark inside shield
        val check = Path2D.Double().apply {
            moveTo(x + w * 0.26, y + h * 0.52)
            lineTo(x + w * 0.44, y + h * 0.70)
            lineTo(x + w * 0.74, y + h * 0.34)
        }
        g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = Color(255, 255, 255)
        g2.draw(check)
    }

    /**
     * Draws an evaluation hourglass emblem using vector paths.
     */
    fun drawHourglass(g2: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        val hourglass = Path2D.Double().apply {
            moveTo(x + w * 0.15, y + h * 0.05)
            lineTo(x + w * 0.85, y + h * 0.05)
            lineTo(x + w * 0.50, y + h * 0.50)
            lineTo(x + w * 0.85, y + h * 0.95)
            lineTo(x + w * 0.15, y + h * 0.95)
            lineTo(x + w * 0.50, y + h * 0.50)
            closePath()
        }

        val hgGrad = GradientPaint(
            x.toFloat(), y.toFloat(), Color(254, 215, 170),
            (x + w).toFloat(), (y + h).toFloat(), Color(251, 146, 60)
        )
        g2.paint = hgGrad
        g2.fill(hourglass)

        g2.stroke = BasicStroke(0.9f)
        g2.color = Color(255, 255, 255, 210)
        g2.draw(hourglass)
    }

    /**
     * Creates a reusable standalone Swing [Icon] rendering the vector emblem for the given tier.
     */
    fun createBadgeIcon(type: LicenseType, size: Int = 14): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = size
            override fun getIconHeight(): Int = size
            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2 = g as? Graphics2D ?: return
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val d = size.toDouble()
                when (type) {
                    LicenseType.ADMIN -> drawCrown(g2, x.toDouble(), y.toDouble(), d, d)
                    LicenseType.DEVELOPER -> drawLightningBolt(g2, x.toDouble(), y.toDouble(), d, d)
                    LicenseType.USER -> drawShield(g2, x.toDouble(), y.toDouble(), d, d)
                    LicenseType.TRIAL -> drawHourglass(g2, x.toDouble(), y.toDouble(), d, d)
                }
            }
        }
    }
}
