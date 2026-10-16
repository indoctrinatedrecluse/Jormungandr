package org.jormungandr.dataframe.arrow

import org.jormungandr.dataframe.model.DataTypeCategory

/**
 * Arrow Primitive & Nested Data Types supported by Jörmungandr's Zero-Copy IPC Engine.
 */
enum class ArrowTypeId(val displayName: String, val category: DataTypeCategory, val bitWidth: Int) {
    INT8("Int8", DataTypeCategory.INTEGER, 8),
    INT16("Int16", DataTypeCategory.INTEGER, 16),
    INT32("Int32", DataTypeCategory.INTEGER, 32),
    INT64("Int64", DataTypeCategory.INTEGER, 64),
    UINT8("UInt8", DataTypeCategory.INTEGER, 8),
    UINT16("UInt16", DataTypeCategory.INTEGER, 16),
    UINT32("UInt32", DataTypeCategory.INTEGER, 32),
    UINT64("UInt64", DataTypeCategory.INTEGER, 64),
    FLOAT16("Float16", DataTypeCategory.FLOAT, 16),
    FLOAT32("Float32", DataTypeCategory.FLOAT, 32),
    FLOAT64("Float64", DataTypeCategory.FLOAT, 64),
    UTF8("Utf8", DataTypeCategory.STRING, 0),
    LARGE_UTF8("LargeUtf8", DataTypeCategory.STRING, 0),
    BINARY("Binary", DataTypeCategory.BINARY, 0),
    BOOL("Boolean", DataTypeCategory.BOOLEAN, 1),
    DATE32("Date32", DataTypeCategory.DATETIME, 32),
    TIMESTAMP_MILLI("TimestampMillis", DataTypeCategory.DATETIME, 64),
    DECIMAL128("Decimal128", DataTypeCategory.FLOAT, 128)
}

/**
 * Field metadata in an Arrow IPC schema.
 */
data class ArrowField(
    val name: String,
    val type: ArrowTypeId,
    val nullable: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Arrow Schema definition.
 */
data class ArrowSchema(
    val fields: List<ArrowField>,
    val customMetadata: Map<String, String> = emptyMap()
)

/**
 * Column chunk buffer within an Arrow IPC RecordBatch.
 */
data class ArrowColumnBuffer(
    val field: ArrowField,
    val length: Int,
    val nullCount: Int,
    val values: List<Any?>
)

/**
 * Decoded RecordBatch containing multiple columnar buffers.
 */
data class ArrowRecordBatch(
    val rowCount: Int,
    val columns: List<ArrowColumnBuffer>,
    val byteSize: Long = 0L
)

/**
 * Endpoint descriptor for Apache Arrow Flight services.
 */
data class FlightEndpoint(
    val host: String,
    val port: Int,
    val path: String = "",
    val isSecure: Boolean = false
) {
    val uriString: String
        get() = "${if (isSecure) "grpc+tls" else "grpc"}://$host:$port/$path"
}

/**
 * Arrow Flight Ticket used to initiate DoGet batch streams.
 */
data class FlightTicket(
    val ticketBytes: ByteArray,
    val description: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlightTicket) return false
        return ticketBytes.contentEquals(other.ticketBytes) && description == other.description
    }

    override fun hashCode(): Int = 31 * ticketBytes.contentHashCode() + description.hashCode()
}

/**
 * Flight Info metadata containing schema and available endpoints.
 */
data class FlightInfo(
    val schema: ArrowSchema,
    val endpoints: List<FlightEndpoint>,
    val totalRecords: Long = -1L,
    val totalBytes: Long = -1L
)

/**
 * Real-time streaming metrics for active Arrow Flight sessions.
 */
data class FlightStreamMetrics(
    val sessionName: String,
    val batchesReceived: Int,
    val rowsStreamed: Long,
    val bytesTransferred: Long,
    val throughputMbPerSec: Double,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)
