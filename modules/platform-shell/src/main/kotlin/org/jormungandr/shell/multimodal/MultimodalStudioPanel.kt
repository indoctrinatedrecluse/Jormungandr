package org.jormungandr.shell.multimodal

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel
import kotlin.math.sin

/**
 * Flagship Multimodal AI Annotation & Inspection Studio Panel.
 * Unifies Computer Vision (YOLO/COCO bounding box overlays) and Audio/Speech Analytics
 * (dual-channel waveforms, frequency spectrograms, and timestamped Whisper transcriptions).
 */
class MultimodalStudioPanel : JPanel(BorderLayout()) {

    // Vision Studio components
    private val visionCanvas = VisionAnnotationCanvas()
    private val visionModel = DefaultTableModel(arrayOf("ID", "Class", "Confidence", "xMin, yMin", "Width, Height"), 0)
    private val visionTable = JBTable(visionModel)
    private val visionScenes = listOf(createAutonomousDrivingScene(), createRoboticsWarehouseScene())
    private val visionSceneCombo = ComboBox(visionScenes.map { it.first }.toTypedArray())

    // Audio Studio components
    private val audioCanvas = AudioWaveformCanvas()
    private val audioSamples = listOf(createSpeechPromptSample(), createAcousticTelemetrySample())
    private val audioSampleCombo = ComboBox(audioSamples.map { it.title }.toTypedArray())
    private val playButton = JButton("▶ Play Audio")
    private var isPlaying = false
    private var playbackTimer: Timer? = null

    init {
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("👁️ Computer Vision & Annotation", createVisionPanel())
        tabbedPane.addTab("🎙️ Audio & Speech Analytics", createAudioPanel())
        add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createVisionPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Top Toolbar
        val topToolbar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>Scene:</b>"))
        leftHeader.add(visionSceneCombo)

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val exportYoloBtn = JButton("📋 Export YOLO Labels").apply {
            isFocusPainted = false
            addActionListener { exportYolo() }
        }
        val exportCocoBtn = JButton("📋 Export COCO JSON").apply {
            isFocusPainted = false
            addActionListener { exportCoco() }
        }
        rightHeader.add(exportYoloBtn)
        rightHeader.add(exportCocoBtn)

        topToolbar.add(leftHeader, BorderLayout.WEST)
        topToolbar.add(rightHeader, BorderLayout.EAST)
        panel.add(topToolbar, BorderLayout.NORTH)

        // Center Split
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            resizeWeight = 0.70
            dividerSize = 4
        }
        split.leftComponent = visionCanvas

        val sidebar = JPanel(BorderLayout(4, 4)).apply {
            border = EmptyBorder(6, 6, 6, 6)
            add(JBLabel("<b>Detected Objects / Annotations:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(visionTable), BorderLayout.CENTER)
        }
        split.rightComponent = sidebar
        panel.add(split, BorderLayout.CENTER)

        // Listeners
        visionSceneCombo.addActionListener {
            val idx = visionSceneCombo.selectedIndex.coerceIn(0, visionScenes.size - 1)
            loadVisionScene(visionScenes[idx].second)
        }

        loadVisionScene(visionScenes.first().second)
        return panel
    }

    private fun loadVisionScene(boxes: List<BoundingBox>) {
        visionCanvas.boundingBoxes = boxes
        visionModel.rowCount = 0
        for (b in boxes) {
            visionModel.addRow(arrayOf(
                b.id,
                b.label,
                String.format("%.2f", b.confidence),
                String.format("%.2f, %.2f", b.xMin, b.yMin),
                String.format("%.2f, %.2f", b.width, b.height)
            ))
        }
    }

    private fun createAudioPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Top Toolbar
        val topToolbar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>Audio Source:</b>"))
        leftHeader.add(audioSampleCombo)
        leftHeader.add(playButton.apply {
            isFocusPainted = false
            addActionListener { togglePlay() }
        })

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val exportWhisperBtn = JButton("📋 Export Whisper JSON").apply {
            isFocusPainted = false
            addActionListener { exportWhisper() }
        }
        rightHeader.add(exportWhisperBtn)

        topToolbar.add(leftHeader, BorderLayout.WEST)
        topToolbar.add(rightHeader, BorderLayout.EAST)
        panel.add(topToolbar, BorderLayout.NORTH)

        panel.add(audioCanvas, BorderLayout.CENTER)

        // Listeners
        audioSampleCombo.addActionListener {
            val idx = audioSampleCombo.selectedIndex.coerceIn(0, audioSamples.size - 1)
            audioCanvas.audioData = audioSamples[idx]
            pauseAudio()
        }

        audioCanvas.audioData = audioSamples.first()
        return panel
    }

    private fun togglePlay() {
        if (isPlaying) {
            pauseAudio()
        } else {
            playAudio()
        }
    }

    private fun playAudio() {
        isPlaying = true
        playButton.text = "⏸ Pause Audio"
        val totalSec = audioCanvas.audioData?.durationSeconds ?: 10.0

        playbackTimer?.stop()
        playbackTimer = Timer(50) {
            var curr = audioCanvas.playbackPositionSeconds + 0.05
            if (curr >= totalSec) {
                curr = 0.0
                pauseAudio()
            }
            audioCanvas.playbackPositionSeconds = curr
        }
        playbackTimer?.start()
    }

    private fun pauseAudio() {
        isPlaying = false
        playButton.text = "▶ Play Audio"
        playbackTimer?.stop()
    }

    private fun exportYolo() {
        val boxes = visionCanvas.boundingBoxes
        val sb = StringBuilder()
        for (b in boxes) {
            sb.append(String.format("0 %.6f %.6f %.6f %.6f # %s\n", b.xCenter, b.yCenter, b.width, b.height, b.label))
        }
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(sb.toString()), null)
        Messages.showInfoMessage("YOLO normalized annotations format copied to clipboard!", "YOLO Labels Exported")
    }

    private fun exportCoco() {
        val boxes = visionCanvas.boundingBoxes
        val sb = StringBuilder()
        sb.append("{\n  \"annotations\": [\n")
        for ((idx, b) in boxes.withIndex()) {
            sb.append(String.format("    {\"id\": %d, \"category\": \"%s\", \"bbox\": [%.4f, %.4f, %.4f, %.4f], \"score\": %.4f}%s\n",
                idx + 1, b.label, b.xMin, b.yMin, b.width, b.height, b.confidence, if (idx == boxes.size - 1) "" else ","))
        }
        sb.append("  ]\n}")
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(sb.toString()), null)
        Messages.showInfoMessage("COCO JSON format copied to clipboard!", "COCO JSON Exported")
    }

    private fun exportWhisper() {
        val cues = audioCanvas.audioData?.transcriptCues ?: emptyList()
        val sb = StringBuilder()
        sb.append("{\n  \"segments\": [\n")
        for ((idx, c) in cues.withIndex()) {
            sb.append(String.format("    {\"id\": %d, \"start\": %.2f, \"end\": %.2f, \"text\": \"%s\"}%s\n",
                idx + 1, c.startTime, c.endTime, c.text, if (idx == cues.size - 1) "" else ","))
        }
        sb.append("  ]\n}")
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(sb.toString()), null)
        Messages.showInfoMessage("Whisper ASR transcription JSON copied to clipboard!", "Whisper JSON Exported")
    }

    private fun createAutonomousDrivingScene(): Pair<String, List<BoundingBox>> {
        val boxes = listOf(
            BoundingBox("box-1", "vehicle", 0.96, 0.42, 0.48, 0.58, 0.76, Color(38, 139, 210)),
            BoundingBox("box-2", "pedestrian", 0.89, 0.22, 0.44, 0.28, 0.68, Color(211, 54, 130)),
            BoundingBox("box-3", "traffic_light", 0.94, 0.65, 0.25, 0.70, 0.38, Color(133, 153, 0)),
            BoundingBox("box-4", "vehicle", 0.82, 0.72, 0.52, 0.88, 0.70, Color(38, 139, 210))
        )
        return "Autonomous Driving / Urban Traffic" to boxes
    }

    private fun createRoboticsWarehouseScene(): Pair<String, List<BoundingBox>> {
        val boxes = listOf(
            BoundingBox("box-w1", "forklift_robot", 0.98, 0.30, 0.45, 0.50, 0.80, Color(203, 75, 22)),
            BoundingBox("box-w2", "pallet_cargo", 0.93, 0.60, 0.55, 0.82, 0.85, Color(181, 137, 0)),
            BoundingBox("box-w3", "barcode_station", 0.87, 0.15, 0.35, 0.26, 0.58, Color(108, 113, 196))
        )
        return "Robotics Warehouse & Automation" to boxes
    }

    private fun createSpeechPromptSample(): AudioWaveformData {
        val n = 400
        val chL = FloatArray(n)
        val chR = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            chL[i] = (sin(t * 40.0) * 0.7 + sin(t * 120.0) * 0.3).toFloat()
            chR[i] = (sin(t * 38.0 + 0.3) * 0.65 + sin(t * 115.0) * 0.35).toFloat()
        }
        val cues = listOf(
            TranscriptCue(0.5, 2.5, "Welcome to Jörmungandr Multimodal AI.", "Speaker 1"),
            TranscriptCue(3.0, 5.8, "Processing dual-channel speech and spectral telemetry.", "Speaker 1"),
            TranscriptCue(6.2, 9.4, "Whisper transcription synchronized with playback.", "Speaker 1")
        )
        return AudioWaveformData("Interactive Speech & Whisper Transcription", 44100, 10.0, chL, chR, cues)
    }

    private fun createAcousticTelemetrySample(): AudioWaveformData {
        val n = 400
        val chL = FloatArray(n)
        val chR = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            chL[i] = (sin(t * 80.0) * 0.5 + sin(t * 220.0) * 0.5).toFloat()
            chR[i] = (sin(t * 82.0) * 0.55 + sin(t * 215.0) * 0.45).toFloat()
        }
        val cues = listOf(
            TranscriptCue(1.0, 4.0, "Turbine sensor harmonic resonance detected at 82Hz.", "Telemetry Bot"),
            TranscriptCue(5.0, 8.5, "Acoustic baseline stable within nominal parameters.", "Telemetry Bot")
        )
        return AudioWaveformData("Industrial Turbine Acoustic Vibration", 48000, 10.0, chL, chR, cues)
    }
}
