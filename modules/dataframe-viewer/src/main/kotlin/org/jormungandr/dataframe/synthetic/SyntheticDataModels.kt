package org.jormungandr.dataframe.synthetic

import org.jormungandr.dataframe.model.DataFrame

/**
 * Mathematical differential privacy mechanisms.
 */
enum class PrivacyMechanism(val displayName: String) {
    LAPLACE("Laplace Mechanism (Pure DP)"),
    GAUSSIAN("Gaussian Mechanism (Approximate DP)"),
    EXPONENTIAL("Exponential Categorical Mechanism")
}

/**
 * Marginal distribution summary for a dataset column.
 */
data class ColumnDistribution(
    val columnName: String,
    val isNumeric: Boolean,
    val mean: Double = 0.0,
    val stdDev: Double = 1.0,
    val minVal: Double = 0.0,
    val maxVal: Double = 1.0,
    val categoricalFrequencies: Map<String, Double> = emptyMap()
)

/**
 * Parameter configuration for synthetic data synthesis.
 */
data class SyntheticGenerationConfig(
    val rowCount: Int = 100,
    val epsilon: Double = 1.0, // Privacy budget parameter (lower = higher privacy / higher noise)
    val delta: Double = 1e-5,
    val mechanism: PrivacyMechanism = PrivacyMechanism.LAPLACE
)

/**
 * Result of the synthetic generation process.
 */
data class SyntheticDatasetResult(
    val syntheticDataFrame: DataFrame,
    val privacyScore: Double, // 0.0 .. 100.0%
    val fidelityScore: Double, // 0.0 .. 100.0%
    val columnDistributions: List<ColumnDistribution>
)
