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

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EdaReportGeneratorTest {

    @Test
    fun `test generateHtmlReport contains core EDA sections`() {
        val col1 = ColumnMetadata(
            name = "feature_a",
            typeName = "DOUBLE",
            category = DataTypeCategory.FLOAT,
            nullCount = 2,
            totalCount = 10,
            distinctCount = 8,
            minVal = "1.0",
            maxVal = "10.0",
            meanVal = 5.5,
            stdDev = 2.8,
            medianVal = 5.0,
            iqr = 4.0,
            skewness = 0.12
        )
        val col2 = ColumnMetadata(
            name = "category_b",
            typeName = "STRING",
            category = DataTypeCategory.STRING,
            nullCount = 0,
            totalCount = 10,
            distinctCount = 3,
            topValues = listOf("Alpha" to 5, "Beta" to 3, "Gamma" to 2)
        )
        val rows = listOf(
            listOf(1.0, "Alpha"),
            listOf(2.0, "Beta"),
            listOf(null, "Alpha"),
            listOf(4.0, "Gamma"),
            listOf(5.0, "Alpha"),
            listOf(6.0, "Beta"),
            listOf(7.0, "Alpha"),
            listOf(8.0, "Beta"),
            listOf(null, "Gamma"),
            listOf(10.0, "Alpha")
        )
        val df = DataFrame("test_dataset", listOf(col1, col2), rows)

        val html = EdaReportGenerator.generateHtmlReport(df)

        assertNotNull(html)
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("Jörmungandr Automated EDA Report"))
        assertTrue(html.contains("test_dataset"))
        assertTrue(html.contains("feature_a"))
        assertTrue(html.contains("category_b"))
        assertTrue(html.contains("Total Rows"))
        assertTrue(html.contains("Total Columns"))
        assertTrue(html.contains("Dataset Completeness"))
        assertTrue(html.contains("Inferred Pandera Schema Validation Code"))
    }
}
