package org.jormungandr.dataframe.synthetic

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyntheticDataEngineTest {

    @Test
    fun `test analyzeDistributions correctly computes numerical and categorical moments`() {
        val colNum = ColumnMetadata("values", "Double", DataTypeCategory.FLOAT, totalCount = 5)
        val colCat = ColumnMetadata("label", "String", DataTypeCategory.STRING, totalCount = 5)
        val rows = listOf(
            listOf(10.0, "A"),
            listOf(20.0, "B"),
            listOf(30.0, "A"),
            listOf(40.0, "C"),
            listOf(50.0, "A")
        )
        val df = DataFrame("sample", listOf(colNum, colCat), rows)

        val dists = SyntheticDataEngine.analyzeDistributions(df)
        assertEquals(2, dists.size)

        val numDist = dists[0]
        assertTrue(numDist.isNumeric)
        assertEquals(30.0, numDist.mean, 1e-4)
        assertEquals(10.0, numDist.minVal, 1e-4)
        assertEquals(50.0, numDist.maxVal, 1e-4)

        val catDist = dists[1]
        assertFalse(catDist.isNumeric)
        assertEquals(0.6, catDist.categoricalFrequencies["A"] ?: 0.0, 1e-4)
    }

    @Test
    fun `test generateSyntheticDataset produces expected row count and valid bounds`() {
        val colNum = ColumnMetadata("score", "Double", DataTypeCategory.FLOAT, totalCount = 3)
        val colCat = ColumnMetadata("cat", "String", DataTypeCategory.STRING, totalCount = 3)
        val rows = listOf(
            listOf(100.0, "X"),
            listOf(200.0, "Y"),
            listOf(300.0, "X")
        )
        val df = DataFrame("input", listOf(colNum, colCat), rows)

        val config = SyntheticGenerationConfig(rowCount = 50, epsilon = 2.0, mechanism = PrivacyMechanism.LAPLACE)
        val result = SyntheticDataEngine.generateSyntheticDataset(df, config)

        val synth = result.syntheticDataFrame
        assertEquals(50, synth.rowCount, "Synthesized DataFrame must match target row count")
        assertEquals(2, synth.columnCount)
        assertTrue(result.privacyScore in 0.0..100.0)
        assertTrue(result.fidelityScore in 0.0..100.0)
    }
}
