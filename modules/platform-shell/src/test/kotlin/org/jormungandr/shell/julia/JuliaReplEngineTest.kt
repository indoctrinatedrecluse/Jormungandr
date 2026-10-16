package org.jormungandr.shell.julia

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JuliaReplEngineTest {

    private lateinit var engine: JuliaReplEngine

    @BeforeEach
    fun setUp() {
        engine = JuliaReplEngine(JuliaInstallationInfo.simulated())
    }

    @Test
    fun testJuliaEvaluation() {
        val result = engine.evaluate("1 + 1")
        assertFalse(result.isError)
        assertEquals("2", result.output)
        assertEquals(JuliaReplMode.JULIA, result.mode)
        assertEquals(1, engine.getHistory().size)
    }

    @Test
    fun testPkgModeEvaluation() {
        val addResult = engine.evaluate("] add DataFrames")
        assertFalse(addResult.isError)
        assertTrue(addResult.output.contains("Installed DataFrames"))
        assertEquals(JuliaReplMode.PKG, addResult.mode)

        val statusResult = engine.evaluate("] status")
        assertFalse(statusResult.isError)
        assertTrue(statusResult.output.contains("Project.toml"))
    }

    @Test
    fun testShellModeEvaluation() {
        val result = engine.evaluate("; ls -la")
        assertFalse(result.isError)
        assertEquals(JuliaReplMode.SHELL, result.mode)
        assertTrue(result.output.contains("Shell command executed"))
    }

    @Test
    fun testHelpModeEvaluation() {
        val result = engine.evaluate("? sin")
        assertFalse(result.isError)
        assertEquals(JuliaReplMode.HELP, result.mode)
        assertTrue(result.output.contains("help?> sin"))
    }

    @Test
    fun testScientificSnippetsAvailability() {
        val snippets = JuliaReplEngine.getScientificSnippets()
        assertTrue(snippets.containsKey("Matrix Factorization (SVD)"))
        assertTrue(snippets.containsKey("DataFrames GroupBy & Combine"))
        assertTrue(snippets.containsKey("Differential Equation Solver (ODE)"))
    }

    @Test
    fun testClearHistory() {
        engine.evaluate("2 * 3")
        assertEquals(1, engine.getHistory().size)
        engine.clearHistory()
        assertEquals(0, engine.getHistory().size)
    }
}
