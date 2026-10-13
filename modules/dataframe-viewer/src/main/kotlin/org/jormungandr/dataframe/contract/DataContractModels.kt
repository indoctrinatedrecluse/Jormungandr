package org.jormungandr.dataframe.contract

/**
 * Supported data quality and schema expectation types.
 */
enum class ExpectationType(val displayName: String) {
    NOT_NULL("Values Must Not Be Null"),
    VALUE_RANGE("Values Must Be In Range [Min, Max]"),
    REGEX_MATCH("Values Must Match Regex Pattern"),
    ALLOWED_SET("Values Must Be In Allowed Set"),
    UNIQUE_RATIO("Uniqueness Ratio Must Exceed Threshold"),
    DATA_TYPE("Column Must Match Expected Type")
}

/**
 * Status of an expectation evaluation.
 */
enum class EvaluationStatus {
    PASSED,
    FAILED,
    WARNING
}

/**
 * A declarative data expectation rule.
 */
data class DataExpectation(
    val id: String,
    val column: String,
    val type: ExpectationType,
    val minVal: Double? = null,
    val maxVal: Double? = null,
    val regexPattern: String? = null,
    val allowedValues: List<String>? = null,
    val minUniqueRatio: Double? = null,
    val expectedType: String? = null
)

/**
 * Evaluation outcome for a single expectation against a dataset.
 */
data class ExpectationEvaluationResult(
    val expectation: DataExpectation,
    val status: EvaluationStatus,
    val observedSummary: String,
    val violationCount: Int,
    val totalCount: Int,
    val failureRate: Double, // 0.0 .. 1.0
    val message: String
)

/**
 * A suite of data contract expectations.
 */
data class DataContractSuite(
    val suiteName: String,
    val datasetName: String,
    val expectations: List<DataExpectation>
)
