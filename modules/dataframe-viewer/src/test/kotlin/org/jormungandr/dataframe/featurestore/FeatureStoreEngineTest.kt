package org.jormungandr.dataframe.featurestore

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureStoreEngineTest {

    @Test
    fun testPointInTimeJoinEnrichment() {
        val colId = ColumnMetadata("user_id", "Long", DataTypeCategory.INTEGER, totalCount = 3)
        val colTs = ColumnMetadata("event_timestamp", "Long", DataTypeCategory.INTEGER, totalCount = 3)

        val observationDf = DataFrame(
            name = "UserEvents",
            columns = listOf(colId, colTs),
            rows = listOf(
                listOf(101L, 1600000000L),
                listOf(102L, 1600000100L),
                listOf(103L, 1600000200L)
            )
        )

        val config = PointInTimeJoinConfig(
            entityJoinKey = "user_id",
            timestampColumn = "event_timestamp",
            selectedFeatures = listOf("avg_transaction_amount_30d", "login_count_7d", "is_premium_subscriber")
        )

        val enriched = FeatureStoreEngine.executePointInTimeJoin(
            observationDf,
            "customer_behavior_features",
            config
        )

        assertEquals(3, enriched.rowCount)
        assertEquals(5, enriched.columnCount, "Observation columns (2) + selected features (3) should equal 5 columns")
        assertTrue(enriched.getColumnIndex("avg_transaction_amount_30d") >= 0)
        assertTrue(enriched.getColumnIndex("login_count_7d") >= 0)
        assertTrue(enriched.getColumnIndex("is_premium_subscriber") >= 0)

        assertNotNull(enriched.rows[0][2])
        assertNotNull(enriched.rows[0][3])
        assertNotNull(enriched.rows[0][4])
    }

    @Test
    fun testTimeTravelSnapshotsAndRestore() {
        val col = ColumnMetadata("x", "Long", DataTypeCategory.INTEGER, totalCount = 2)
        val dfV0 = DataFrame("V0", listOf(col), listOf(listOf(1L), listOf(2L)))
        val dfV1 = DataFrame("V1", listOf(col), listOf(listOf(10L), listOf(20L)))

        val snap0 = FeatureStoreEngine.recordSnapshot(dfV0, "INIT", "Initial version")
        val snap1 = FeatureStoreEngine.recordSnapshot(dfV1, "UPDATE", "Updated rows")

        assertEquals(dfV0.rowCount, snap0.rowCount)
        assertEquals(dfV1.rowCount, snap1.rowCount)

        val restored0 = FeatureStoreEngine.restoreSnapshot(snap0.version)
        assertNotNull(restored0)
        assertEquals(1L, restored0?.rows?.get(0)?.get(0))

        val restored1 = FeatureStoreEngine.restoreSnapshot(snap1.version)
        assertNotNull(restored1)
        assertEquals(10L, restored1?.rows?.get(0)?.get(0))
    }

    @Test
    fun testFeastAndHopsworksCodeGeneration() {
        val feastCode = FeatureStoreEngine.generateFeastPythonConfig("customer_behavior_features")
        assertTrue(feastCode.contains("from feast import Entity, FeatureView"))
        assertTrue(feastCode.contains("customer_behavior_features"))
        assertTrue(feastCode.contains("avg_transaction_amount_30d"))

        val hopsworksCode = FeatureStoreEngine.generateHopsworksPythonConfig("customer_behavior_features")
        assertTrue(hopsworksCode.contains("import hopsworks"))
        assertTrue(hopsworksCode.contains("customer_behavior_features"))
    }
}
