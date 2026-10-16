package org.jormungandr.dataframe.arrow

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ArrowIpcTest {

    @Test
    fun testArrowIpcSerializationAndDeserialization() {
        val colId = ColumnMetadata("id", "Long", DataTypeCategory.INTEGER, totalCount = 3)
        val colVal = ColumnMetadata("score", "Double", DataTypeCategory.FLOAT, totalCount = 3)
        val colTag = ColumnMetadata("tag", "String", DataTypeCategory.STRING, totalCount = 3)
        val colFlag = ColumnMetadata("active", "Boolean", DataTypeCategory.BOOLEAN, totalCount = 3)

        val originalDf = DataFrame(
            name = "TestMetrics",
            columns = listOf(colId, colVal, colTag, colFlag),
            rows = listOf(
                listOf(1L, 98.5, "sensor_a", true),
                listOf(2L, 77.2, "sensor_b", false),
                listOf(3L, 42.0, "sensor_c", true)
            )
        )

        // Serialize to Arrow IPC Stream bytes
        val bytes = ArrowIpcStreamReader.writeIpcStream(originalDf)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty(), "Serialized Arrow IPC stream should not be empty")

        // Deserialize back from bytes
        val deserializedDf = ArrowIpcStreamReader.readIpcStream("RoundTripDf", bytes)
        assertEquals("RoundTripDf", deserializedDf.name)
        assertEquals(4, deserializedDf.columns.size)
        assertEquals(3, deserializedDf.rowCount)

        assertEquals("id", deserializedDf.columns[0].name)
        assertEquals("score", deserializedDf.columns[1].name)
        assertEquals("tag", deserializedDf.columns[2].name)
        assertEquals("active", deserializedDf.columns[3].name)

        assertEquals(1L, (deserializedDf.rows[0][0] as Number).toLong())
        assertEquals(98.5, (deserializedDf.rows[0][1] as Number).toDouble(), 0.001)
        assertEquals("sensor_a", deserializedDf.rows[0][2])
        assertEquals(true, deserializedDf.rows[0][3])

        assertEquals(2L, (deserializedDf.rows[1][0] as Number).toLong())
        assertEquals(false, deserializedDf.rows[1][3])
    }

    @Test
    fun testArrowIpcWithNullValues() {
        val colA = ColumnMetadata("name", "String", DataTypeCategory.STRING, totalCount = 2)
        val colB = ColumnMetadata("num", "Long", DataTypeCategory.INTEGER, totalCount = 2)

        val dfWithNulls = DataFrame(
            name = "NullDf",
            columns = listOf(colA, colB),
            rows = listOf(
                listOf("alpha", null),
                listOf(null, 100L)
            )
        )

        val bytes = ArrowIpcStreamReader.writeIpcStream(dfWithNulls)
        val restored = ArrowIpcStreamReader.readIpcStream("RestoredNulls", bytes)

        assertEquals(2, restored.rowCount)
        assertEquals("alpha", restored.rows[0][0])
        assertNull(restored.rows[0][1])
        assertNull(restored.rows[1][0])
        assertEquals(100L, (restored.rows[1][1] as Number).toLong())
    }

    @Test
    fun testArrowFlightConnectorSimulatedStream() {
        val endpoint = FlightEndpoint("127.0.0.1", 8815)
        val ticket = FlightTicket(byteArrayOf(1, 2, 3), "test_ticket")

        var lastMetrics: FlightStreamMetrics? = null
        val df = ArrowFlightConnector.fetchStream(endpoint, ticket) { metrics ->
            lastMetrics = metrics
        }

        assertNotNull(df)
        assertTrue(df.rowCount > 0, "Simulated flight stream should yield rows")
        assertEquals(4, df.columns.size)
        assertNotNull(lastMetrics)
        assertEquals(df.rowCount.toLong(), lastMetrics?.rowsStreamed)
        assertTrue(lastMetrics?.isCompleted == true)
    }

    @Test
    fun testPythonFlightServerCodeGeneration() {
        val code = ArrowFlightConnector.generatePythonFlightServerCode(8815, "my_stream")
        assertTrue(code.contains("pyarrow.flight"))
        assertTrue(code.contains("8815"))
        assertTrue(code.contains("my_stream"))
    }
}
