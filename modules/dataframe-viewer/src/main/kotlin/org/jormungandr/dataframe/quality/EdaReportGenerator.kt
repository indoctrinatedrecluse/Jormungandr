/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.dataframe.quality

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Generates an automated, self-contained, responsive HTML Exploratory Data Analysis (EDA) report.
 * Provides deep distributional statistics, correlation heatmaps, missing value analyses,
 * outlier profiles, and Pandera schemas with zero external web dependencies.
 */
object EdaReportGenerator {

    fun generateHtmlReport(df: DataFrame): String {
        val scorecard = DataQualityProfilerService.analyzeQuality(df)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val numericCols = df.columns.filter { it.isNumeric }
        val categoricalCols = df.columns.filter { it.category == DataTypeCategory.STRING || it.category == DataTypeCategory.OBJECT }
        val dateCols = df.columns.filter { it.category == DataTypeCategory.DATETIME }

        val corrMatrix = if (numericCols.size >= 2) {
            CorrelationMatrixCalculator.compute(df, CorrelationMethod.PEARSON)
        } else null

        val sb = StringBuilder()
        sb.append(
            """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Jörmungandr EDA Report: ${escapeHtml(df.name)}</title>
    <style>
        :root {
            --bg: #0f141c;
            --surface: #18202c;
            --surface-card: #202b3b;
            --text-primary: #e6edf3;
            --text-secondary: #8b949e;
            --accent: #58a6ff;
            --accent-green: #3fb950;
            --accent-amber: #d29922;
            --accent-red: #f85149;
            --accent-purple: #bc8cff;
            --border: #30363d;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
        body { background: var(--bg); color: var(--text-primary); padding: 24px; line-height: 1.5; }
        .container { max-width: 1280px; margin: 0 auto; }
        header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 20px; margin-bottom: 24px; }
        .brand { display: flex; align-items: center; gap: 12px; }
        .brand h1 { font-size: 24px; font-weight: 700; color: var(--accent); }
        .meta-tag { background: var(--surface); border: 1px solid var(--border); padding: 4px 12px; border-radius: 16px; font-size: 13px; color: var(--text-secondary); }
        .grid-kpi { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 28px; }
        .kpi-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 16px; text-align: center; }
        .kpi-title { font-size: 13px; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px; }
        .kpi-value { font-size: 26px; font-weight: 700; color: var(--text-primary); }
        .kpi-sub { font-size: 12px; margin-top: 4px; color: var(--text-secondary); }
        .section { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 24px; margin-bottom: 28px; }
        .section-header { font-size: 18px; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); padding-bottom: 10px; }
        table { width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; }
        th { background: var(--surface-card); padding: 10px 14px; font-weight: 600; color: var(--text-secondary); border-bottom: 1px solid var(--border); }
        td { padding: 10px 14px; border-bottom: 1px solid var(--border); color: var(--text-primary); }
        tr:hover td { background: rgba(255, 255, 255, 0.02); }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; }
        .badge-int { background: #1f3d5c; color: #58a6ff; }
        .badge-float { background: #2b3a1a; color: #7ee787; }
        .badge-str { background: #422b16; color: #d29922; }
        .badge-date { background: #3b234a; color: #d2a8ff; }
        .bar-container { background: var(--surface-card); height: 8px; border-radius: 4px; overflow: hidden; width: 100%; margin-top: 4px; }
        .bar-fill { height: 100%; background: var(--accent); border-radius: 4px; }
        .bar-fill-null { background: var(--accent-red); }
        .col-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 20px; }
        .col-card { background: var(--surface-card); border: 1px solid var(--border); border-radius: 8px; padding: 16px; }
        .col-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
        .col-card-title { font-weight: 600; font-size: 15px; }
        .stats-kv { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 12px; margin-bottom: 12px; }
        .stats-kv div { display: flex; justify-content: space-between; border-bottom: 1px dotted var(--border); padding-bottom: 2px; }
        .stats-kv span:first-child { color: var(--text-secondary); }
        .stats-kv span:last-child { font-weight: 600; }
        .chart-svg { width: 100%; height: 80px; display: block; margin-top: 8px; }
        .corr-matrix-wrapper { overflow-x: auto; margin-top: 12px; }
        .corr-table th, .corr-table td { text-align: center; padding: 8px; font-size: 12px; min-width: 80px; }
        pre.code-block { background: #090d13; border: 1px solid var(--border); border-radius: 6px; padding: 16px; overflow-x: auto; font-family: monospace; font-size: 12px; color: #a5d6ff; }
    </style>
</head>
<body>
<div class="container">
    <header>
        <div class="brand">
            <h1>🐍 Jörmungandr Automated EDA Report</h1>
            <span class="meta-tag">Dataset: <b>${escapeHtml(df.name)}</b></span>
            <span class="meta-tag">Generated: ${timestamp}</span>
        </div>
        <div class="meta-tag" style="background: ${scorecardColor(scorecard.overallHealthScore)}; color: #fff; font-weight: 700;">
            Health Grade: ${scorecard.healthGrade} (${"%.1f".format(scorecard.overallHealthScore)}%)
        </div>
    </header>

    <!-- Top KPI Grid -->
    <div class="grid-kpi">
        <div class="kpi-card">
            <div class="kpi-title">Total Rows</div>
            <div class="kpi-value">${df.rowCount}</div>
            <div class="kpi-sub">Observations</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-title">Total Columns</div>
            <div class="kpi-value">${df.columnCount}</div>
            <div class="kpi-sub">${numericCols.size} Numeric, ${categoricalCols.size} Text, ${dateCols.size} Time</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-title">Dataset Completeness</div>
            <div class="kpi-value">${"%.1f".format(scorecard.sparsity.completenessPercentage)}%</div>
            <div class="kpi-sub">${scorecard.sparsity.missingCells} null cells across matrix</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-title">Detected Outliers</div>
            <div class="kpi-value">${scorecard.outliers.sumOf { it.iqrOutliersCount }}</div>
            <div class="kpi-sub">Across ${scorecard.outliers.count { it.iqrOutliersCount > 0 }} numeric fields</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-title">Pandera Rule Pass</div>
            <div class="kpi-value">${scorecard.validationResults.count { it.passed }} / ${scorecard.validationResults.size}</div>
            <div class="kpi-sub">Automated validation rules</div>
        </div>
    </div>
"""
        )

        // Section 1: Column Overview Table
        sb.append(
            """
    <div class="section">
        <div class="section-header">
            <span>📋 Dataset Column Summary</span>
            <span style="font-size: 13px; color: var(--text-secondary);">${df.columns.size} Total Features</span>
        </div>
        <table>
            <thead>
                <tr>
                    <th>Column Name</th>
                    <th>Type</th>
                    <th>Missing</th>
                    <th>Distinct</th>
                    <th>Min</th>
                    <th>Max</th>
                    <th>Mean</th>
                    <th>Std Dev</th>
                    <th>IQR</th>
                    <th>Skewness</th>
                </tr>
            </thead>
            <tbody>
"""
        )
        for (col in df.columns) {
            val badgeClass = when (col.category) {
                DataTypeCategory.INTEGER -> "badge-int"
                DataTypeCategory.FLOAT -> "badge-float"
                DataTypeCategory.STRING, DataTypeCategory.OBJECT -> "badge-str"
                DataTypeCategory.DATETIME -> "badge-date"
                else -> "badge-int"
            }
            sb.append(
                """
                <tr>
                    <td><b>${escapeHtml(col.name)}</b></td>
                    <td><span class="badge $badgeClass">${col.typeName}</span></td>
                    <td>
                        ${col.nullCount} (${"%.1f".format(col.nullPercentage)}%)
                        <div class="bar-container"><div class="bar-fill bar-fill-null" style="width: ${col.nullPercentage}%;"></div></div>
                    </td>
                    <td>${col.distinctCount}</td>
                    <td>${col.minVal ?: "-"}</td>
                    <td>${col.maxVal ?: "-"}</td>
                    <td>${col.meanVal?.let { "%.2f".format(it) } ?: "-"}</td>
                    <td>${col.stdDev?.let { "%.2f".format(it) } ?: "-"}</td>
                    <td>${col.iqr?.let { "%.2f".format(it) } ?: "-"}</td>
                    <td>${col.skewness?.let { "%.2f".format(it) } ?: "-"}</td>
                </tr>
"""
            )
        }
        sb.append(
            """
            </tbody>
        </table>
    </div>
"""
        )

        // Section 2: Detailed Feature Cards with SVG Histograms
        sb.append(
            """
    <div class="section">
        <div class="section-header">
            <span>📊 Detailed Feature Profiling & Distribution Sparklines</span>
        </div>
        <div class="col-grid">
"""
        )
        for (col in df.columns) {
            sb.append(
                """
            <div class="col-card">
                <div class="col-card-header">
                    <span class="col-card-title">${escapeHtml(col.name)}</span>
                    <span class="meta-tag">${col.typeName}</span>
                </div>
                <div class="stats-kv">
                    <div><span>Total Count</span><span>${col.totalCount}</span></div>
                    <div><span>Distinct</span><span>${col.distinctCount}</span></div>
                    <div><span>Missing Rate</span><span>${"%.1f".format(col.nullPercentage)}%</span></div>
                    <div><span>Min Value</span><span>${col.minVal ?: "-"}</span></div>
                    <div><span>Max Value</span><span>${col.maxVal ?: "-"}</span></div>
                    <div><span>Mean &plusmn; Std</span><span>${col.meanVal?.let { "%.2f".format(it) } ?: "-"}</span></div>
                    <div><span>Median (Q2)</span><span>${col.medianVal?.let { "%.2f".format(it) } ?: "-"}</span></div>
                    <div><span>IQR (Q3 - Q1)</span><span>${col.iqr?.let { "%.2f".format(it) } ?: "-"}</span></div>
                </div>
"""
            )

            if (col.histogramBins.isNotEmpty()) {
                sb.append(generateSvgHistogram(col.histogramBins))
            } else if (col.topValues.isNotEmpty()) {
                sb.append(generateTopValuesHtml(col.topValues, col.totalCount))
            }

            sb.append("</div>\n")
        }
        sb.append("</div></div>\n")

        // Section 3: Pearson Correlation Matrix Heatmap
        if (corrMatrix != null && corrMatrix.columnNames.isNotEmpty()) {
            sb.append(
                """
    <div class="section">
        <div class="section-header">
            <span>🌡️ Pairwise Correlation Heatmap (Pearson)</span>
            <span style="font-size: 13px; color: var(--text-secondary);">${corrMatrix.columnNames.size} Numeric Columns</span>
        </div>
        <div class="corr-matrix-wrapper">
            <table class="corr-table">
                <thead>
                    <tr>
                        <th></th>
"""
            )
            for (colName in corrMatrix.columnNames) {
                sb.append("<th>${escapeHtml(colName)}</th>")
            }
            sb.append("</tr></thead><tbody>")
            for (rowName in corrMatrix.columnNames) {
                sb.append("<tr><td><b>${escapeHtml(rowName)}</b></td>")
                for (colName in corrMatrix.columnNames) {
                    val r = corrMatrix.getCorrelation(rowName, colName)
                    val bgColor = correlationColor(r)
                    val textCol = if (abs(r) > 0.45) "#ffffff" else "#e6edf3"
                    sb.append(
                        """<td style="background: $bgColor; color: $textCol; font-weight: 600;">${"%.2f".format(r)}</td>"""
                    )
                }
                sb.append("</tr>")
            }
            sb.append("</tbody></table></div></div>")
        }

        // Section 4: Pandera Validation Schema
        sb.append(
            """
    <div class="section">
        <div class="section-header">
            <span>🐍 Inferred Pandera Schema Validation Code</span>
            <span style="font-size: 13px; color: var(--text-secondary);">Production Python Contract</span>
        </div>
        <pre class="code-block">${escapeHtml(scorecard.panderaCode)}</pre>
    </div>
</div>
</body>
</html>
"""
        )
        return sb.toString()
    }

    private fun generateSvgHistogram(bins: List<org.jormungandr.dataframe.model.HistogramBin>): String {
        if (bins.isEmpty()) return ""
        val maxCount = bins.maxOfOrNull { it.count } ?: 1
        val width = 320
        val height = 70
        val barWidth = width.toDouble() / bins.size

        val svg = StringBuilder()
        svg.append("""<svg class="chart-svg" viewBox="0 0 $width $height">""")
        for ((idx, bin) in bins.withIndex()) {
            val barHeight = if (maxCount > 0) (bin.count.toDouble() / maxCount * (height - 10)) else 0.0
            val x = idx * barWidth
            val y = height - barHeight
            svg.append(
                """<rect x="${"%.1f".format(x)}" y="${"%.1f".format(y)}" width="${"%.1f".format(barWidth - 1)}" height="${"%.1f".format(barHeight)}" fill="#58a6ff" opacity="0.85"><title>${escapeHtml(bin.label)}: ${bin.count}</title></rect>"""
            )
        }
        svg.append("</svg>")
        return svg.toString()
    }

    private fun generateTopValuesHtml(topValues: List<Pair<String, Int>>, totalCount: Long): String {
        val sb = StringBuilder("<div style='font-size: 11px; margin-top: 6px;'>")
        for ((value, count) in topValues.take(5)) {
            val pct = if (totalCount > 0) (count.toDouble() / totalCount * 100.0) else 0.0
            sb.append(
                """
                <div style="display: flex; justify-content: space-between; margin-bottom: 2px;">
                    <span style="color: #8b949e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px;">${escapeHtml(value)}</span>
                    <span><b>$count</b> (${"%.1f".format(pct)}%)</span>
                </div>
                <div class="bar-container" style="height: 4px; margin-bottom: 4px;"><div class="bar-fill" style="width: ${pct}%;"></div></div>
"""
            )
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun correlationColor(r: Double): String {
        // Red-White-Blue style or Blue-Green gradient
        return when {
            r >= 0.7 -> "rgba(63, 185, 80, 0.85)"
            r >= 0.4 -> "rgba(63, 185, 80, 0.45)"
            r >= 0.1 -> "rgba(88, 166, 255, 0.25)"
            r > -0.1 -> "rgba(255, 255, 255, 0.05)"
            r > -0.4 -> "rgba(248, 81, 73, 0.25)"
            r > -0.7 -> "rgba(248, 81, 73, 0.50)"
            else -> "rgba(248, 81, 73, 0.85)"
        }
    }

    private fun scorecardColor(score: Double): String {
        return when {
            score >= 85.0 -> "#3fb950"
            score >= 70.0 -> "#d29922"
            else -> "#f85149"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
