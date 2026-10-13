package org.jormungandr.dataframe.contract

import org.jormungandr.dataframe.model.DataFrame

/**
 * Engine for evaluating Data Contracts and Schema Expectations against DataFrames,
 * and generating executable Pandera and Great Expectations suites.
 */
object DataContractEngine {

    /**
     * Evaluates a suite of expectations against a DataFrame.
     */
    fun evaluate(df: DataFrame, suite: DataContractSuite): List<ExpectationEvaluationResult> {
        val totalRows = df.rowCount
        if (totalRows == 0) {
            return suite.expectations.map { exp ->
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = EvaluationStatus.WARNING,
                    observedSummary = "Empty dataset (0 rows)",
                    violationCount = 0,
                    totalCount = 0,
                    failureRate = 0.0,
                    message = "Dataset is empty; expectation could not be verified."
                )
            }
        }

        return suite.expectations.map { exp ->
            evaluateSingle(df, exp, totalRows)
        }
    }

    private fun evaluateSingle(df: DataFrame, exp: DataExpectation, totalRows: Int): ExpectationEvaluationResult {
        val col = df.columns.firstOrNull { it.name.equals(exp.column, ignoreCase = true) }
        if (col == null) {
            return ExpectationEvaluationResult(
                expectation = exp,
                status = EvaluationStatus.FAILED,
                observedSummary = "Column missing",
                violationCount = totalRows,
                totalCount = totalRows,
                failureRate = 1.0,
                message = "Column '${exp.column}' does not exist in dataset."
            )
        }

        val values = df.getColumnValues(exp.column)

        return when (exp.type) {
            ExpectationType.NOT_NULL -> {
                val nullCount = values.count { it == null || it.toString().trim().isEmpty() || it.toString() == "null" }
                val rate = nullCount.toDouble() / totalRows
                val passed = (nullCount == 0)
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = if (passed) EvaluationStatus.PASSED else EvaluationStatus.FAILED,
                    observedSummary = "$nullCount nulls (${String.format("%.1f%%", rate * 100)})",
                    violationCount = nullCount,
                    totalCount = totalRows,
                    failureRate = rate,
                    message = if (passed) "Zero null values detected." else "$nullCount null values violate NOT_NULL constraint."
                )
            }
            ExpectationType.VALUE_RANGE -> {
                val min = exp.minVal ?: Double.NEGATIVE_INFINITY
                val max = exp.maxVal ?: Double.POSITIVE_INFINITY
                var violations = 0
                for (v in values) {
                    val num = v?.toString()?.toDoubleOrNull()
                    if (num == null || num < min || num > max) {
                        violations++
                    }
                }
                val rate = violations.toDouble() / totalRows
                val passed = (violations == 0)
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = if (passed) EvaluationStatus.PASSED else EvaluationStatus.FAILED,
                    observedSummary = "$violations out of range [${min.takeIf { it != Double.NEGATIVE_INFINITY } ?: "-inf"}, ${max.takeIf { it != Double.POSITIVE_INFINITY } ?: "+inf"}]",
                    violationCount = violations,
                    totalCount = totalRows,
                    failureRate = rate,
                    message = if (passed) "All values fall within expected numeric range." else "$violations values outside range."
                )
            }
            ExpectationType.REGEX_MATCH -> {
                val regex = runCatching { Regex(exp.regexPattern ?: ".*") }.getOrElse { Regex(".*") }
                val violations = values.count { v ->
                    val s = v?.toString() ?: ""
                    !regex.matches(s)
                }
                val rate = violations.toDouble() / totalRows
                val passed = (violations == 0)
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = if (passed) EvaluationStatus.PASSED else EvaluationStatus.FAILED,
                    observedSummary = "$violations pattern mismatches",
                    violationCount = violations,
                    totalCount = totalRows,
                    failureRate = rate,
                    message = if (passed) "All values match regex pattern '${exp.regexPattern}'." else "$violations values do not match pattern."
                )
            }
            ExpectationType.ALLOWED_SET -> {
                val allowed = exp.allowedValues?.toSet() ?: emptySet()
                val violations = values.count { v ->
                    val s = v?.toString() ?: ""
                    !allowed.contains(s)
                }
                val rate = violations.toDouble() / totalRows
                val passed = (violations == 0)
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = if (passed) EvaluationStatus.PASSED else EvaluationStatus.FAILED,
                    observedSummary = "$violations values outside allowed set",
                    violationCount = violations,
                    totalCount = totalRows,
                    failureRate = rate,
                    message = if (passed) "All values belong to allowed set." else "$violations unexpected categorical values found."
                )
            }
            ExpectationType.UNIQUE_RATIO -> {
                val uniqueCount = values.map { it?.toString() ?: "" }.distinct().size
                val ratio = uniqueCount.toDouble() / totalRows
                val target = exp.minUniqueRatio ?: 0.9
                val passed = (ratio >= target)
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = if (passed) EvaluationStatus.PASSED else EvaluationStatus.FAILED,
                    observedSummary = String.format("%.1f%% unique (Threshold: %.1f%%)", ratio * 100, target * 100),
                    violationCount = if (passed) 0 else (totalRows - uniqueCount),
                    totalCount = totalRows,
                    failureRate = (1.0 - ratio).coerceAtLeast(0.0),
                    message = if (passed) "Uniqueness ratio exceeds threshold." else "Uniqueness ratio is below threshold."
                )
            }
            ExpectationType.DATA_TYPE -> {
                val expected = exp.expectedType?.uppercase() ?: "STRING"
                val actual = col.category.name
                val passed = actual.contains(expected) || (expected == "NUMERIC" && col.isNumeric)
                ExpectationEvaluationResult(
                    expectation = exp,
                    status = if (passed) EvaluationStatus.PASSED else EvaluationStatus.FAILED,
                    observedSummary = "Actual type: ${col.category.displayName}",
                    violationCount = if (passed) 0 else totalRows,
                    totalCount = totalRows,
                    failureRate = if (passed) 0.0 else 1.0,
                    message = if (passed) "Column type matches expected category." else "Column type mismatch: expected $expected, found ${col.category.displayName}."
                )
            }
        }
    }

    /**
     * Generates executable Pandera schema code from a contract suite.
     */
    fun exportToPandera(suite: DataContractSuite): String {
        val sb = StringBuilder()
        sb.append("# ==============================================================================\n")
        sb.append("# Jörmungandr Data Contract Suite - Pandera Schema Export\n")
        sb.append("# ==============================================================================\n")
        sb.append("import pandera as pa\nfrom pandera import Check\n\n")
        sb.append("schema = pa.DataFrameSchema({\n")

        val byCol = suite.expectations.groupBy { it.column }
        for ((colName, exps) in byCol) {
            sb.append("    \"$colName\": pa.Column(\n")
            val checks = mutableListOf<String>()
            var nullable = true
            for (e in exps) {
                when (e.type) {
                    ExpectationType.NOT_NULL -> nullable = false
                    ExpectationType.VALUE_RANGE -> {
                        val min = e.minVal ?: "float('-inf')"
                        val max = e.maxVal ?: "float('inf')"
                        checks.add("Check.in_range($min, $max)")
                    }
                    ExpectationType.REGEX_MATCH -> checks.add("Check.str_matches(r\"${e.regexPattern}\")")
                    ExpectationType.ALLOWED_SET -> checks.add("Check.isin([${e.allowedValues?.joinToString(", ") { "\"$it\"" }}])")
                    ExpectationType.UNIQUE_RATIO -> checks.add("# Check: unique_ratio >= ${e.minUniqueRatio}")
                    ExpectationType.DATA_TYPE -> {}
                }
            }
            sb.append("        checks=[${checks.joinToString(", ")}],\n")
            sb.append("        nullable=$nullable\n")
            sb.append("    ),\n")
        }
        sb.append("})\n\n")
        sb.append("# Audit execution:\n")
        sb.append("# validated_df = schema.validate(df, lazy=True)\n")
        return sb.toString()
    }

    /**
     * Generates executable Great Expectations suite code from a contract suite.
     */
    fun exportToGreatExpectations(suite: DataContractSuite): String {
        val sb = StringBuilder()
        sb.append("# ==============================================================================\n")
        sb.append("# Jörmungandr Data Contract Suite - Great Expectations Export\n")
        sb.append("# ==============================================================================\n")
        sb.append("import great_expectations as gx\n\n")
        sb.append("context = gx.get_context()\n")
        sb.append("suite = context.suites.add(gx.ExpectationSuite(name=\"${suite.suiteName}\"))\n\n")

        for (e in suite.expectations) {
            when (e.type) {
                ExpectationType.NOT_NULL -> {
                    sb.append("suite.add_expectation(gx.expectations.ExpectColumnValuesToNotBeNull(column=\"${e.column}\"))\n")
                }
                ExpectationType.VALUE_RANGE -> {
                    sb.append("suite.add_expectation(gx.expectations.ExpectColumnValuesToBeBetween(column=\"${e.column}\", min_value=${e.minVal}, max_value=${e.maxVal}))\n")
                }
                ExpectationType.REGEX_MATCH -> {
                    sb.append("suite.add_expectation(gx.expectations.ExpectColumnValuesToMatchRegex(column=\"${e.column}\", regex=r\"${e.regexPattern}\"))\n")
                }
                ExpectationType.ALLOWED_SET -> {
                    val allowedList = e.allowedValues?.joinToString(", ") { "\"$it\"" } ?: ""
                    sb.append("suite.add_expectation(gx.expectations.ExpectColumnValuesToBeInSet(column=\"${e.column}\", value_set=[$allowedList]))\n")
                }
                ExpectationType.UNIQUE_RATIO -> {
                    sb.append("suite.add_expectation(gx.expectations.ExpectColumnUniqueValueRatioToBeBetween(column=\"${e.column}\", min_value=${e.minUniqueRatio}))\n")
                }
                ExpectationType.DATA_TYPE -> {
                    sb.append("suite.add_expectation(gx.expectations.ExpectColumnValuesToBeOfType(column=\"${e.column}\", type_=\"${e.expectedType}\"))\n")
                }
            }
        }
        sb.append("\nprint(f\"Configured {len(suite.expectations)} expectations in suite '{suite.name}'.\")\n")
        return sb.toString()
    }
}
