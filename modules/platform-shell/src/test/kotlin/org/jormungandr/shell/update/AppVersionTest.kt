package org.jormungandr.shell.update

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AppVersionTest {

    @AfterEach
    fun tearDown() {
        AppVersion.overrideVersionForTesting = null
        System.clearProperty("jormungandr.version")
    }

    @Test
    fun `test cleanVersion strips v prefix`() {
        assertEquals("1.3.0", AppVersion.cleanVersion("v1.3.0"))
        assertEquals("1.3.0", AppVersion.cleanVersion("V1.3.0"))
        assertEquals("1.3.0", AppVersion.cleanVersion(" 1.3.0 "))
    }

    @Test
    fun `test isNewer correctly detects newer releases`() {
        assertTrue(AppVersion.isNewer("1.3.1", "1.3.0"))
        assertTrue(AppVersion.isNewer("1.4.0", "1.3.0"))
        assertTrue(AppVersion.isNewer("2.0.0", "1.3.0"))
        assertTrue(AppVersion.isNewer("v1.3.1", "v1.3.0"))

        assertFalse(AppVersion.isNewer("1.3.0", "1.3.0"))
        assertFalse(AppVersion.isNewer("1.2.0", "1.3.0"))
        assertFalse(AppVersion.isNewer("1.1.0", "1.3.0"))
        assertFalse(AppVersion.isNewer("v1.2.0", "v1.3.0"))
    }

    @Test
    fun `test dynamic version override for testing`() {
        assertEquals("1.6.0", AppVersion.CURRENT)
        AppVersion.overrideVersionForTesting = "1.7.0"
        assertEquals("1.7.0", AppVersion.CURRENT)
        assertFalse(AppVersion.isNewer("1.7.0", AppVersion.CURRENT))
        assertTrue(AppVersion.isNewer("1.8.0", AppVersion.CURRENT))
    }

    @Test
    fun `test dynamic version resolution from system property`() {
        System.setProperty("jormungandr.version", "v1.5.2")
        assertEquals("1.5.2", AppVersion.CURRENT)
    }
}
