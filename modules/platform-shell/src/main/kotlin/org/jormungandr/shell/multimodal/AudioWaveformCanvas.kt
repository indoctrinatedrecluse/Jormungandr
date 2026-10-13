package org.jormungandr.shell.multimodal

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import kotlin.math.*

/**
 * Interactive Audio Waveform, Spectrogram, and Speech Transcript Timeline Canvas.
 */
class AudioWaveformCanvas : JComponent() {

    var audioData: AudioWaveformData? = null
        set(value) {
            field = value
            playbackPositionSeconds = 0.0
            repaint()
        }

    var playbackPositionSeconds: Double = 0.0
        set(value) {
            field = value.coerceIn(0.0, audioData?.durationSeconds ?: 10.0)
            repaint()
        }

    var onSeek: ((Double) -> Unit)? = null

    init {
        background = Color(20, 22, 28)

        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                seekTo(e.x)
            }

            override fun mouseDragged(e: MouseEvent) {
                seekTo(e.x)
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    private fun seekTo(mx: Int) {
        val totalSec = audioData?.durationSeconds ?: 10.0
        val frac = (mx.toDouble() / width).coerceIn(0.0, 1.0)
        playbackPositionSeconds = frac * totalSec
        onSeek?.invoke(playbackPositionSeconds)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // 1. Dark Background
        g2.color = Color(18, 20, 26)
        g2.fillRect(0, 0, w, h)

        val data = audioData
        if (data == null) {
            g2.color = Color(150, 150, 150)
            g2.drawString("No audio file loaded", w / 2 - 50, h / 2)
            g2.dispose()
            return
        }

        // Partition vertical space:
        // Top 50%: Dual-channel Waveform (L & R)
        // Mid 30%: Spectrogram Heatmap
        // Bottom 20%: Transcript Timeline
        val waveformHeight = (h * 0.48).toInt()
        val specHeight = (h * 0.32).toInt()
        val transcriptHeight = h - waveformHeight - specHeight

        // 2. Waveform Rendering (Channel 1: Green, Channel 2: Cyan)
        drawWaveform(g2, data, w, waveformHeight)

        // 3. Frequency Spectrogram Rendering
        drawSpectrogram(g2, data, w, waveformHeight, specHeight)

        // 4. Transcript Cues
        drawTranscript(g2, data, w, waveformHeight + specHeight, transcriptHeight)

        // 5. Playback Cursor Line
        val totalSec = data.durationSeconds.coerceAtLeast(0.1)
        val cursorX = ((playbackPositionSeconds / totalSec) * w).toInt()

        g2.color = Color(255, 215, 0) // Gold scrubber
        g2.stroke = BasicStroke(2.0f)
        g2.drawLine(cursorX, 0, cursorX, h)

        // Timecode badge
        val timecode = String.format("%02d:%05.2f / %02d:%05.2f",
            (playbackPositionSeconds / 60).toInt(), playbackPositionSeconds % 60,
            (totalSec / 60).toInt(), totalSec % 60
        )
        g2.font = Font("Segoe UI", Font.BOLD, 10)
        g2.color = Color(255, 215, 0)
        g2.drawString(timecode, cursorX + 6, 14)

        g2.dispose()
    }

    private fun drawWaveform(g2: Graphics2D, data: AudioWaveformData, w: Int, h: Int) {
        val midL = h / 4.0
        val midR = h * 3.0 / 4.0
        val chL = data.leftChannel
        val chR = data.rightChannel

        g2.color = Color(35, 40, 50)
        g2.drawLine(0, midL.toInt(), w, midL.toInt())
        g2.drawLine(0, midR.toInt(), w, midR.toInt())

        // Channel Labels
        g2.color = Color(100, 110, 130)
        g2.font = Font("Segoe UI", Font.PLAIN, 9)
        g2.drawString("CH-L", 4, (midL - 4).toInt())
        g2.drawString("CH-R", 4, (midR - 4).toInt())

        val step = maxOf(1, chL.size / w)
        g2.stroke = BasicStroke(1.2f)

        for (x in 0 until w) {
            val idx = (x * step).coerceIn(0, chL.size - 1)
            val ampL = (chL[idx] * (h / 4.5)).toDouble()
            val ampR = (chR[idx] * (h / 4.5)).toDouble()

            g2.color = Color(133, 153, 0) // Solarized green
            g2.draw(Line2D.Double(x.toDouble(), midL - ampL, x.toDouble(), midL + ampL))

            g2.color = Color(42, 161, 152) // Solarized cyan
            g2.draw(Line2D.Double(x.toDouble(), midR - ampR, x.toDouble(), midR + ampR))
        }
    }

    private fun drawSpectrogram(g2: Graphics2D, data: AudioWaveformData, w: Int, yOffset: Int, h: Int) {
        g2.color = Color(12, 14, 18)
        g2.fillRect(0, yOffset, w, h)

        val bins = 16
        val binHeight = h / bins.toDouble()

        for (x in 0 until w step 2) {
            val normX = x.toDouble() / w
            for (b in 0 until bins) {
                val freqFactor = (b + 1) / bins.toDouble()
                val intensity = (sin(normX * 18.0 + b * 0.7) * 0.5 + 0.5) * (1.0 - freqFactor * 0.4)
                val c = getHeatmapColor(intensity.coerceIn(0.0, 1.0))
                g2.color = c
                g2.fill(Rectangle2D.Double(x.toDouble(), yOffset + (bins - 1 - b) * binHeight, 2.0, binHeight))
            }
        }

        g2.color = Color(80, 90, 105)
        g2.font = Font("Segoe UI", Font.PLAIN, 9)
        g2.drawString("Spectrogram (0 - 22kHz)", 4, yOffset + 12)
    }

    private fun drawTranscript(g2: Graphics2D, data: AudioWaveformData, w: Int, yOffset: Int, h: Int) {
        g2.color = Color(24, 28, 36)
        g2.fillRect(0, yOffset, w, h)

        val totalSec = data.durationSeconds.coerceAtLeast(0.1)

        for (cue in data.transcriptCues) {
            val startX = ((cue.startTime / totalSec) * w).toInt()
            val endX = ((cue.endTime / totalSec) * w).toInt()
            val cueW = maxOf(4, endX - startX)

            val isActive = (playbackPositionSeconds >= cue.startTime && playbackPositionSeconds <= cue.endTime)

            g2.color = if (isActive) Color(38, 139, 210, 140) else Color(45, 52, 65)
            g2.fillRoundRect(startX, yOffset + 2, cueW, h - 4, 4, 4)

            g2.color = if (isActive) Color(38, 139, 210) else Color(70, 80, 100)
            g2.stroke = BasicStroke(1.0f)
            g2.drawRoundRect(startX, yOffset + 2, cueW, h - 4, 4, 4)

            g2.color = if (isActive) Color.WHITE else Color(180, 190, 205)
            g2.font = Font("Segoe UI", if (isActive) Font.BOLD else Font.PLAIN, 10)
            val textToDraw = if (g2.fontMetrics.stringWidth(cue.text) > cueW - 4) cue.text.take(15) + "..." else cue.text
            g2.drawString(textToDraw, startX + 4, yOffset + (h / 2) + 4)
        }
    }

    private fun getHeatmapColor(intensity: Double): Color {
        // Deep Violet -> Blue -> Cyan -> Yellow -> Red
        val r = (255 * intensity.pow(1.5)).toInt().coerceIn(0, 255)
        val g = (255 * (1.0 - abs(intensity - 0.5) * 2.0)).toInt().coerceIn(0, 255)
        val b = (255 * (1.0 - intensity)).toInt().coerceIn(0, 255)
        return Color(r, g, b, 200)
    }
}
