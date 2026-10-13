package org.jormungandr.shell.multimodal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Color

class MultimodalStudioTest {

    @Test
    fun `test BoundingBox computes center and dimensions correctly`() {
        val box = BoundingBox("b1", "vehicle", 0.95, 0.2, 0.3, 0.6, 0.7, Color.BLUE)
        assertEquals(0.4, box.width, 1e-4)
        assertEquals(0.4, box.height, 1e-4)
        assertEquals(0.4, box.xCenter, 1e-4)
        assertEquals(0.5, box.yCenter, 1e-4)
    }

    @Test
    fun `test AudioWaveformData holds channels and duration`() {
        val chL = floatArrayOf(0.1f, 0.5f, -0.3f)
        val chR = floatArrayOf(0.2f, 0.4f, -0.2f)
        val audio = AudioWaveformData("Test", 44100, 5.0, chL, chR)
        assertEquals(5.0, audio.durationSeconds)
        assertEquals(3, audio.leftChannel.size)
        assertEquals(3, audio.rightChannel.size)
    }

    @Test
    fun `test MultimodalStudioPanel initializes successfully`() {
        val panel = MultimodalStudioPanel()
        assertNotNull(panel)
        assertEquals(1, panel.componentCount)
    }
}
