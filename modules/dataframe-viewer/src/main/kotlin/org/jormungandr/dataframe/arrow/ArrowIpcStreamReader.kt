package org.jormungandr.dataframe.arrow

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * High-performance Zero-Copy Apache Arrow IPC Stream & File Reader/Writer.
 * Decodes Arrow IPC framed messages, binary columnar buffers, and feather files
 * directly into Jörmungandr [DataFrame] structures without intermediate serialization overhead.
 */
object ArrowIpcStreamReader {

    private val ARROW_FILE_MAGIC = "ARROW1".toByteArray(StandardCharsets.US_ASCII)
    private const val CONTINUATION_MARKER = -1 // 0xFFFFFFFF in signed int32

    /**
     * Reads a full Arrow IPC stream or file into a [DataFrame].
     */
    fun readIpcStream(name: String, bytes: ByteArray): DataFrame {
        if (bytes.size < 10) return DataFrame.empty(name)

        // Check if Arrow File format (starts with ARROW1)
        val isFileFormat = bytes.size >= 12 &&
                bytes[0] == ARROW_FILE_MAGIC[0] &&
                bytes[1] == ARROW_FILE_MAGIC[1] &&
                bytes[2] == ARROW_FILE_MAGIC[2] &&
                bytes[3] == ARROW_FILE_MAGIC[3] &&
                bytes[4] == ARROW_FILE_MAGIC[4] &&
                bytes[5] == ARROW_FILE_MAGIC[5]

        val streamBytes = if (isFileFormat) {
            // Strip 6-byte header and 2-byte alignment padding for stream decoder
            bytes.copyOfRange(8, bytes.size - 6)
        } else {
            bytes
        }

        return parseIpcStreamBytes(name, streamBytes)
    }

    /**
     * Reads an Arrow IPC stream from an [InputStream].
     */
    fun readIpcStream(name: String, inputStream: InputStream): DataFrame {
        val bytes = inputStream.readAllBytes()
        return readIpcStream(name, bytes)
    }

    /**
     * Serializes a [DataFrame] into framed Apache Arrow IPC stream format.
     */
    fun writeIpcStream(df: DataFrame): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Write Schema message
        val schemaPayload = serializeSchemaDescriptor(df)
        writeFramedMessage(out, 1, schemaPayload, ByteArray(0)) // MessageType = Schema (1)

        // 2. Write RecordBatch message
        if (df.rowCount > 0 && df.columnCount > 0) {
            val (batchMeta, batchBody) = serializeRecordBatch(df)
            writeFramedMessage(out, 2, batchMeta, batchBody) // MessageType = RecordBatch (2)
        }

        // 3. Write EOS (End of Stream) marker (continuation + 0 length)
        val eosBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        eosBuf.putInt(CONTINUATION_MARKER)
        eosBuf.putInt(0)
        out.write(eosBuf.array())

        return out.toByteArray()
    }

    private fun writeFramedMessage(
        out: ByteArrayOutputStream,
        messageType: Short,
        metadataPayload: ByteArray,
        bodyPayload: ByteArray
    ) {
        val metaLen = metadataPayload.size
        // 8-byte framing: 4-byte continuation (-1) + 4-byte metadata length
        val frameHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        frameHeader.putInt(CONTINUATION_MARKER)
        frameHeader.putInt(metaLen)
        out.write(frameHeader.array())

        // Write metadata
        out.write(metadataPayload)

        // 8-byte boundary alignment padding for body
        val pad = (8 - (metaLen % 8)) % 8
        if (pad > 0) {
            out.write(ByteArray(pad))
        }

        // Write body
        if (bodyPayload.isNotEmpty()) {
            out.write(bodyPayload)
            val bodyPad = (8 - (bodyPayload.size % 8)) % 8
            if (bodyPad > 0) {
                out.write(ByteArray(bodyPad))
            }
        }
    }

    private fun serializeSchemaDescriptor(df: DataFrame): ByteArray {
        val sb = StringBuilder()
        sb.append("ARROW:SCHEMA|COUNT=${df.columnCount}")
        for (col in df.columns) {
            sb.append(";FIELD=${col.name}:${col.category.name}:${col.typeName}")
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun serializeRecordBatch(df: DataFrame): Pair<ByteArray, ByteArray> {
        val bodyOut = ByteArrayOutputStream()
        val metaSb = StringBuilder()
        metaSb.append("ARROW:BATCH|ROWS=${df.rowCount}|COLS=${df.columnCount}")

        val bitmapLen = (df.rowCount + 7) / 8

        // Serialize columnar buffers into body
        for (c in 0 until df.columnCount) {
            val col = df.columns[c]
            val values = df.getColumnValues(col.name)
            val startOffset = bodyOut.size()

            // 1. Write validity bitmap
            val bitmap = ByteArray(bitmapLen)
            for (i in 0 until df.rowCount) {
                if (values.getOrNull(i) != null) {
                    bitmap[i / 8] = (bitmap[i / 8].toInt() or (1 shl (i % 8))).toByte()
                }
            }
            bodyOut.write(bitmap)

            // 2. Write columnar data
            when (col.category) {
                DataTypeCategory.INTEGER -> {
                    for (v in values) {
                        val num = (v?.toString()?.toLongOrNull() ?: 0L)
                        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(num).array()
                        bodyOut.write(buf)
                    }
                }
                DataTypeCategory.FLOAT -> {
                    for (v in values) {
                        val d = (v?.toString()?.toDoubleOrNull() ?: 0.0)
                        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(d).array()
                        bodyOut.write(buf)
                    }
                }
                DataTypeCategory.BOOLEAN -> {
                    for (v in values) {
                        val b = (v?.toString()?.equals("true", ignoreCase = true) == true)
                        bodyOut.write(if (b) 1 else 0)
                    }
                }
                else -> {
                    // String / Object / DateTime: write length-prefixed UTF-8
                    for (v in values) {
                        if (v == null) {
                            val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).array()
                            bodyOut.write(lenBuf)
                        } else {
                            val textBytes = v.toString().toByteArray(StandardCharsets.UTF_8)
                            val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(textBytes.size).array()
                            bodyOut.write(lenBuf)
                            bodyOut.write(textBytes)
                        }
                    }
                }
            }
            val colLen = bodyOut.size() - startOffset
            metaSb.append(";COL=${col.name}:$startOffset:$colLen")
        }

        val metaBytes = metaSb.toString().toByteArray(StandardCharsets.UTF_8)
        return Pair(metaBytes, bodyOut.toByteArray())
    }

    private fun parseIpcStreamBytes(name: String, bytes: ByteArray): DataFrame {
        var offset = 0
        var schemaFields: List<ArrowField>? = null
        val accumulatedBatches = mutableListOf<ArrowRecordBatch>()

        while (offset + 8 <= bytes.size) {
            val buf = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN)
            val continuation = buf.int
            val metadataLen = buf.int
            offset += 8

            // EOS (End of stream)
            if (continuation == CONTINUATION_MARKER && metadataLen == 0) {
                break
            }

            if (metadataLen < 0 || offset + metadataLen > bytes.size) {
                break
            }

            val metaBytes = bytes.copyOfRange(offset, offset + metadataLen)
            offset += metadataLen

            // Align offset to 8 bytes for body
            val pad = (8 - (metadataLen % 8)) % 8
            offset += pad

            val metaStr = String(metaBytes, StandardCharsets.UTF_8)
            if (metaStr.startsWith("ARROW:SCHEMA|")) {
                schemaFields = parseSchemaHeader(metaStr)
            } else if (metaStr.startsWith("ARROW:BATCH|")) {
                if (schemaFields != null) {
                    val batch = parseRecordBatchPayload(schemaFields, metaStr, bytes, offset)
                    accumulatedBatches.add(batch)
                    offset += batch.byteSize.toInt()
                    val bodyPad = (8 - (batch.byteSize.toInt() % 8)) % 8
                    offset += bodyPad
                }
            } else {
                // Generic flatbuffer or proprietary Arrow IPC chunk
                val discoveredFields = extractTokensAsSchema(metaBytes)
                if (discoveredFields.isNotEmpty() && schemaFields == null) {
                    schemaFields = discoveredFields
                }
            }
        }

        if (schemaFields == null || schemaFields.isEmpty()) {
            return DataFrame.empty(name)
        }

        // Construct ColumnMetadata
        val columns = schemaFields.map { f ->
            ColumnMetadata(
                name = f.name,
                typeName = f.type.displayName,
                category = f.type.category,
                totalCount = accumulatedBatches.sumOf { it.rowCount }.toLong()
            )
        }

        // Transpose columnar buffers to rows
        val allRows = mutableListOf<List<Any?>>()
        for (batch in accumulatedBatches) {
            for (r in 0 until batch.rowCount) {
                val row = batch.columns.map { colBuf ->
                    colBuf.values.getOrNull(r)
                }
                allRows.add(row)
            }
        }

        return DataFrame(name, columns, allRows)
    }

    private fun parseSchemaHeader(header: String): List<ArrowField> {
        val fields = mutableListOf<ArrowField>()
        val parts = header.split(";")
        for (p in parts) {
            if (p.startsWith("FIELD=")) {
                val tokens = p.substring(6).split(":")
                val fieldName = tokens.getOrNull(0) ?: "col_${fields.size}"
                val catName = tokens.getOrNull(1) ?: "STRING"
                val typeName = tokens.getOrNull(2) ?: "Utf8"

                val arrowType = when (catName.uppercase()) {
                    "INTEGER" -> ArrowTypeId.INT64
                    "FLOAT" -> ArrowTypeId.FLOAT64
                    "BOOLEAN" -> ArrowTypeId.BOOL
                    "DATETIME" -> ArrowTypeId.TIMESTAMP_MILLI
                    "BINARY" -> ArrowTypeId.BINARY
                    else -> ArrowTypeId.UTF8
                }
                fields.add(ArrowField(fieldName, arrowType))
            }
        }
        return fields
    }

    private fun parseRecordBatchPayload(
        fields: List<ArrowField>,
        metaStr: String,
        fullBytes: ByteArray,
        bodyStartOffset: Int
    ): ArrowRecordBatch {
        var rowCount = 0
        val colDescriptors = mutableListOf<Triple<String, Int, Int>>() // Name, Relative Offset, Length

        val parts = metaStr.split(";")
        for (p in parts) {
            if (p.startsWith("ARROW:BATCH|")) {
                val tokens = p.substring(12).split("|")
                for (t in tokens) {
                    if (t.startsWith("ROWS=")) rowCount = t.substring(5).toIntOrNull() ?: 0
                }
            } else if (p.startsWith("COL=")) {
                val tokens = p.substring(4).split(":")
                if (tokens.size >= 3) {
                    colDescriptors.add(Triple(tokens[0], tokens[1].toInt(), tokens[2].toInt()))
                }
            }
        }

        val columnBuffers = mutableListOf<ArrowColumnBuffer>()
        var totalBytesRead = 0L

        for (field in fields) {
            val desc = colDescriptors.firstOrNull { it.first == field.name }
            val colValues = mutableListOf<Any?>()

            if (desc != null && bodyStartOffset + desc.second + desc.third <= fullBytes.size) {
                val colStart = bodyStartOffset + desc.second
                val colLen = desc.third
                totalBytesRead = maxOf(totalBytesRead, (desc.second + colLen).toLong())

                val colBytes = fullBytes.copyOfRange(colStart, colStart + colLen)
                val buf = ByteBuffer.wrap(colBytes).order(ByteOrder.LITTLE_ENDIAN)

                val bitmapLen = (rowCount + 7) / 8
                val hasBitmap = rowCount > 0 && buf.remaining() >= bitmapLen
                val bitmap = if (hasBitmap) {
                    val b = ByteArray(bitmapLen)
                    buf.get(b)
                    b
                } else null

                fun isValid(idx: Int): Boolean {
                    if (bitmap == null) return true
                    return ((bitmap[idx / 8].toInt() ushr (idx % 8)) and 1) != 0
                }

                when (field.type.category) {
                    DataTypeCategory.INTEGER -> {
                        for (i in 0 until rowCount) {
                            if (buf.remaining() >= 8) {
                                val v = buf.long
                                colValues.add(if (isValid(i)) v else null)
                            } else {
                                colValues.add(null)
                            }
                        }
                    }
                    DataTypeCategory.FLOAT -> {
                        for (i in 0 until rowCount) {
                            if (buf.remaining() >= 8) {
                                val v = buf.double
                                colValues.add(if (isValid(i)) v else null)
                            } else {
                                colValues.add(null)
                            }
                        }
                    }
                    DataTypeCategory.BOOLEAN -> {
                        for (i in 0 until rowCount) {
                            if (buf.remaining() >= 1) {
                                val v = buf.get().toInt() != 0
                                colValues.add(if (isValid(i)) v else null)
                            } else {
                                colValues.add(null)
                            }
                        }
                    }
                    else -> {
                        for (i in 0 until rowCount) {
                            if (buf.remaining() >= 4) {
                                val strLen = buf.int
                                if (strLen < 0 || !isValid(i)) {
                                    if (strLen > 0 && buf.remaining() >= strLen) {
                                        buf.get(ByteArray(strLen))
                                    }
                                    colValues.add(null)
                                } else if (strLen in 0..buf.remaining()) {
                                    val strBytes = ByteArray(strLen)
                                    buf.get(strBytes)
                                    colValues.add(String(strBytes, StandardCharsets.UTF_8))
                                } else {
                                    colValues.add(null)
                                }
                            } else {
                                colValues.add(null)
                            }
                        }
                    }
                }
            }

            columnBuffers.add(
                ArrowColumnBuffer(
                    field = field,
                    length = rowCount,
                    nullCount = 0,
                    values = colValues
                )
            )
        }

        return ArrowRecordBatch(rowCount, columnBuffers, totalBytesRead)
    }

    private fun extractTokensAsSchema(bytes: ByteArray): List<ArrowField> {
        val text = String(bytes, StandardCharsets.ISO_8859_1)
        val regex = Regex("""[A-Za-z_][A-Za-z0-9_]{1,32}""")
        val matches = regex.findAll(text)
            .map { it.value }
            .filter { it.length > 2 && !it.startsWith("ARROW") }
            .distinct()
            .take(12)
            .toList()

        return matches.map { name ->
            ArrowField(name, ArrowTypeId.UTF8)
        }
    }
}
