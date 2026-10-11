package org.jormungandr.shell.copilot.local

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FimCompletionServiceTest {

    @Test
    fun testPythonImportHeuristics() {
        val npCompletion = FimCompletionService.generateHeuristicCompletion("import numpy", "py")
        assertEquals(" as np", npCompletion)

        val pdCompletion = FimCompletionService.generateHeuristicCompletion("import pandas", "py")
        assertEquals(" as pd", pdCompletion)

        val plCompletion = FimCompletionService.generateHeuristicCompletion("import polars", "py")
        assertEquals(" as pl", plCompletion)

        val pltCompletion = FimCompletionService.generateHeuristicCompletion("import matplotlib.pyplot", "py")
        assertEquals(" as plt", pltCompletion)
    }

    @Test
    fun testDataScienceChainHeuristics() {
        val groupby = FimCompletionService.generateHeuristicCompletion("df.groupby(", "py")
        assertNotNull(groupby)
        assertTrue(groupby!!.contains("reset_index()"))

        val readCsv = FimCompletionService.generateHeuristicCompletion("data = pd.read_csv(", "py")
        assertEquals("'data/dataset.csv')", readCsv)

        val readParquet = FimCompletionService.generateHeuristicCompletion("table = pl.read_parquet(", "py")
        assertEquals("'data/dataset.parquet')", readParquet)
    }

    @Test
    fun testSqlHeuristics() {
        val sql = FimCompletionService.generateHeuristicCompletion("SELECT", "sql")
        assertNotNull(sql)
        assertTrue(sql!!.contains("read_parquet"))
    }
}
