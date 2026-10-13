package org.jormungandr.dataframe.contract

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataContractEngineTest {

    private fun createTestDataFrame(): DataFrame {
        val colId = ColumnMetadata("id", "Long", DataTypeCategory.INTEGER, totalCount = 5)
        val colPrice = ColumnMetadata("price", "Double", DataTypeCategory.FLOAT, totalCount = 5)
        val colEmail = ColumnMetadata("email", "String", DataTypeCategory.STRING, totalCount = 5)
        val colStatus = ColumnMetadata("status", "String", DataTypeCategory.STRING, totalCount = 5)
        val rows = listOf(
            listOf(1L, 10.5, "alice@test.com", "active"),
            listOf(2L, 25.0, "bob@test.com", "pending"),
            listOf(3L, -5.0, "invalid-email", "active"),
            listOf(4L, 100.0, "carol@test.com", "completed"),
            listOf(5L, 45.0, "dan@test.com", "unknown")
        )
        return DataFrame("test_table", listOf(colId, colPrice, colEmail, colStatus), rows)
    }

    @Test
    fun `test evaluate detects passed and failed expectations accurately`() {
        val df = createTestDataFrame()
        val expectations = listOf(
            DataExpectation("1", "id", ExpectationType.NOT_NULL),
            DataExpectation("2", "id", ExpectationType.UNIQUE_RATIO, minUniqueRatio = 0.95),
            DataExpectation("3", "price", ExpectationType.VALUE_RANGE, minVal = 0.0, maxVal = 200.0), // -5.0 fails
            DataExpectation("4", "email", ExpectationType.REGEX_MATCH, regexPattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$"), // invalid-email fails
            DataExpectation("5", "status", ExpectationType.ALLOWED_SET, allowedValues = listOf("active", "pending", "completed")) // unknown fails
        )
        val suite = DataContractSuite("TestContract", "test_table", expectations)
        val results = DataContractEngine.evaluate(df, suite)

        assertEquals(5, results.size)

        // Expectation 1 (NOT_NULL): Passes
        assertEquals(EvaluationStatus.PASSED, results[0].status)
        assertEquals(0, results[0].violationCount)

        // Expectation 2 (UNIQUE_RATIO): Passes (5/5 = 100%)
        assertEquals(EvaluationStatus.PASSED, results[1].status)

        // Expectation 3 (VALUE_RANGE): Fails due to -5.0
        assertEquals(EvaluationStatus.FAILED, results[2].status)
        assertEquals(1, results[2].violationCount)

        // Expectation 4 (REGEX_MATCH): Fails due to invalid-email
        assertEquals(EvaluationStatus.FAILED, results[3].status)
        assertEquals(1, results[3].violationCount)

        // Expectation 5 (ALLOWED_SET): Fails due to unknown
        assertEquals(EvaluationStatus.FAILED, results[4].status)
        assertEquals(1, results[4].violationCount)
    }

    @Test
    fun `test exportToPandera produces valid python schema syntax`() {
        val expectations = listOf(
            DataExpectation("1", "price", ExpectationType.VALUE_RANGE, minVal = 0.0, maxVal = 100.0),
            DataExpectation("2", "id", ExpectationType.NOT_NULL)
        )
        val suite = DataContractSuite("TestContract", "test_table", expectations)
        val code = DataContractEngine.exportToPandera(suite)

        assertTrue(code.contains("import pandera as pa"))
        assertTrue(code.contains("pa.DataFrameSchema"))
        assertTrue(code.contains("Check.in_range(0.0, 100.0)"))
    }

    @Test
    fun `test exportToGreatExpectations produces valid expectation suite syntax`() {
        val expectations = listOf(
            DataExpectation("1", "id", ExpectationType.NOT_NULL),
            DataExpectation("2", "price", ExpectationType.VALUE_RANGE, minVal = 0.0, maxVal = 100.0)
        )
        val suite = DataContractSuite("TestContract", "test_table", expectations)
        val code = DataContractEngine.exportToGreatExpectations(suite)

        assertTrue(code.contains("import great_expectations as gx"))
        assertTrue(code.contains("ExpectColumnValuesToNotBeNull"))
        assertTrue(code.contains("ExpectColumnValuesToBeBetween"))
    }
}
