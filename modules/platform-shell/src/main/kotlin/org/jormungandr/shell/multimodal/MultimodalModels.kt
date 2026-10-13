package org.jormungandr.shell.multimodal

import java.awt.Color

/**
 * Normalized 2D Bounding Box for Computer Vision object detection and annotation.
 * Coordinates are normalized to [0.0 .. 1.0].
 */
data class BoundingBox(
    val id: String,
    val label: String,
    val confidence: Double,
    val xMin: Double,
    val yMin: Double,
    val xMax: Double,
    val yMax: Double,
    val color: Color = Color(38, 139, 210)
) {
    val width: Double get() = (xMax - xMin).coerceAtLeast(0.0)
    val height: Double get() = (yMax - yMin).coerceAtLeast(0.0)
    val xCenter: Double get() = xMin + width / 2.0
    val yCenter: Double get() = yMin + height / 2.0
}

/**
 * Timestamped transcription segment for speech-to-text models (e.g., Whisper).
 */
data class TranscriptCue(
    val startTime: Double, // in seconds
    val endTime: Double,   // in seconds
    val text: String,
    val speaker: String = "Speaker 1"
)

/**
 * Audio waveform and spectrogram telemetry data.
 */
data class AudioWaveformData(
    val title: String,
    val sampleRate: Int = 44100,
    val durationSeconds: Double = 10.0,
    val leftChannel: FloatArray,
    val rightChannel: FloatArray,
    val transcriptCues: List<TranscriptCue> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioWaveformData) return false
        return title == other.title
    }

    override fun hashCode(): Int = title.hashCode()
}
