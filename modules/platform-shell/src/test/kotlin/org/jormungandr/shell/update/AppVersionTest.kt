package org.jormungandr.shell.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AppVersionTest {

    @Test
    fun `test cleanVersion strips v prefix`() {
        assertEquals("1.2.0", AppVersion.cleanVersion("v1.2.0"))
        assertEquals("1.2.0", AppVersion.cleanVersion("V1.2.0"))
        assertEquals("1.2.0", AppVersion.cleanVersion(" 1.2.0 "))
    }

    @Test
    fun `test isNewer correctly detects newer releases`() {
        assertTrue(AppVersion.isNewer("1.2.1", "1.2.0"))
        assertTrue(AppVersion.isNewer("1.3.0", "1.2.0"))
        assertTrue(AppVersion.isNewer("2.0.0", "1.2.0"))
        assertTrue(AppVersion.isNewer("v1.2.1", "v1.2.0"))

        assertFalse(AppVersion.isNewer("1.2.0", "1.2.0"))
        assertFalse(AppVersion.isNewer("1.1.0", "1.2.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.2.0"))
        assertFalse(AppVersion.isNewer("v1.1.0", "v1.2.0"))
    }
}
