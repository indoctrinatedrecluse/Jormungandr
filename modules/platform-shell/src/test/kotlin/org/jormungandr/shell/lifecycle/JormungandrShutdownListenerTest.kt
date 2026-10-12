package org.jormungandr.shell.lifecycle

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class JormungandrShutdownListenerTest {

    @Test
    fun testAppClosingDoesNotThrow() {
        val listener = JormungandrShutdownListener()
        assertDoesNotThrow {
            listener.appClosing()
        }
    }

    @Test
    fun testAppWillBeClosedDoesNotThrow() {
        val listener = JormungandrShutdownListener()
        assertDoesNotThrow {
            listener.appWillBeClosed(false)
            listener.appWillBeClosed(true)
        }
    }

    @Test
    fun testWelcomeScreenDisplayedDoesNotThrow() {
        val listener = JormungandrShutdownListener()
        assertDoesNotThrow {
            listener.welcomeScreenDisplayed()
        }
    }
}
