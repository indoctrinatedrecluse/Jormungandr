package org.jormungandr.database.engine

import org.jormungandr.database.model.DatabaseDialect
import java.sql.Connection
import java.sql.SQLException

data class ExplainPlanNode(
    val id: Int,
    val title: String,
    val detail: String,
    val isScanWarning: Boolean = false,
    val operatorType: String = "OPERATOR",
    val estimatedCost: Double? = null,
    val estimatedRows: Long? = null,
    val filterPushdown: String? = null,
    val children: MutableList<ExplainPlanNode> = mutableListOf()
)

data class ExplainPlanResult(
    val dialect: String,
    val sql: String,
    val rawText: String,
    val rootNodes: List<ExplainPlanNode>,
    val durationMs: Long
)

/**
 * Executes and structures EXPLAIN & EXPLAIN ANALYZE query execution plans across database dialects.
 */
object ExplainPlanEngine {

    fun explain(
        connection: Connection,
        dialect: DatabaseDialect,
        sql: String,
        analyze: Boolean = false
    ): ExplainPlanResult {
        val trimmed = sql.trim().trimEnd(';')
        val explainQuery = if (analyze) {
            when (dialect) {
                DatabaseDialect.SQLITE -> "EXPLAIN QUERY PLAN $trimmed"
                DatabaseDialect.DUCKDB -> "EXPLAIN ANALYZE $trimmed"
                DatabaseDialect.POSTGRESQL -> "EXPLAIN (ANALYZE, BUFFERS, VERBOSE) $trimmed"
                DatabaseDialect.MYSQL -> "EXPLAIN ANALYZE $trimmed"
                else -> "EXPLAIN ANALYZE $trimmed"
            }
        } else {
            when (dialect) {
                DatabaseDialect.SQLITE -> "EXPLAIN QUERY PLAN $trimmed"
                DatabaseDialect.DUCKDB -> "EXPLAIN $trimmed"
                DatabaseDialect.POSTGRESQL -> "EXPLAIN $trimmed"
                DatabaseDialect.MYSQL -> "EXPLAIN $trimmed"
                DatabaseDialect.ORACLE_PLSQL -> "EXPLAIN PLAN FOR $trimmed"
                else -> "EXPLAIN $trimmed"
            }
        }

        val startTime = System.currentTimeMillis()
        val rawLines = mutableListOf<String>()
        val rootNodes = mutableListOf<ExplainPlanNode>()

        try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery(explainQuery).use { rs ->
                    val meta = rs.metaData
                    val colCount = meta.columnCount

                    when (dialect) {
                        DatabaseDialect.SQLITE -> {
                            val nodeMap = mutableMapOf<Int, ExplainPlanNode>()
                            val parentMap = mutableMapOf<Int, Int>()

                            while (rs.next()) {
                                val id = rs.getInt(1)
                                val parent = rs.getInt(2)
                                val detail = rs.getString(4) ?: ""
                                rawLines.add("[$id -> $parent] $detail")

                                val isWarning = detail.contains("SCAN", ignoreCase = true) &&
                                        !detail.contains("INDEX", ignoreCase = true)
                                val title = extractOperationTitle(detail)
                                val opType = detectOperatorType(detail)
                                val node = ExplainPlanNode(
                                    id = id,
                                    title = title,
                                    detail = detail,
                                    isScanWarning = isWarning,
                                    operatorType = opType
                                )
                                nodeMap[id] = node
                                parentMap[id] = parent
                            }

                            for ((id, node) in nodeMap) {
                                val parentId = parentMap[id] ?: 0
                                if (parentId == 0 || !nodeMap.containsKey(parentId)) {
                                    rootNodes.add(node)
                                } else {
                                    nodeMap[parentId]?.children?.add(node)
                                }
                            }
                        }
                        else -> {
                            val collectedLines = mutableListOf<String>()
                            while (rs.next()) {
                                val line = (1..colCount).joinToString(" | ") { i -> rs.getString(i) ?: "" }
                                rawLines.add(line)
                                if (line.isNotBlank()) collectedLines.add(line)
                            }

                            var counter = 1
                            val stack = mutableListOf<Pair<Int, ExplainPlanNode>>()

                            for (line in collectedLines) {
                                val indent = line.takeWhile { it == ' ' || it == '\t' || it == '|' || it == '-' || it == '>' }.length
                                val isWarning = (line.contains("SCAN", ignoreCase = true) || line.contains("Seq Scan", ignoreCase = true)) &&
                                        !line.contains("Index", ignoreCase = true)
                                val title = extractOperationTitle(line)
                                val opType = detectOperatorType(line)

                                val cost = Regex("""cost=([0-9.]+)\.\.([0-9.]+)""").find(line)?.groupValues?.get(2)?.toDoubleOrNull()
                                val rows = (Regex("""rows=([0-9]+)""").find(line) ?: Regex("""EC:\s*([0-9]+)""").find(line))?.groupValues?.get(1)?.toLongOrNull()
                                val filter = Regex("""Filter:\s*(\([^)]+\)|[^\n|]+)""").find(line)?.groupValues?.get(1)?.trim()

                                val node = ExplainPlanNode(
                                    id = counter++,
                                    title = title,
                                    detail = line.trim(),
                                    isScanWarning = isWarning,
                                    operatorType = opType,
                                    estimatedCost = cost,
                                    estimatedRows = rows,
                                    filterPushdown = filter
                                )

                                while (stack.isNotEmpty() && stack.last().first >= indent) {
                                    stack.removeAt(stack.size - 1)
                                }

                                if (stack.isNotEmpty()) {
                                    stack.last().second.children.add(node)
                                } else {
                                    rootNodes.add(node)
                                }
                                stack.add(Pair(indent, node))
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            val errMsg = "EXPLAIN execution failed: ${e.message}"
            rawLines.add(errMsg)
            rootNodes.add(ExplainPlanNode(0, "Execution Error", errMsg, true, "ERROR"))
        }

        val duration = System.currentTimeMillis() - startTime
        return ExplainPlanResult(
            dialect = dialect.displayName,
            sql = sql,
            rawText = rawLines.joinToString("\n"),
            rootNodes = rootNodes,
            durationMs = duration
        )
    }

    private fun detectOperatorType(detail: String): String {
        val lower = detail.lowercase()
        return when {
            lower.contains("join") -> "JOIN"
            lower.contains("scan") -> "SCAN"
            lower.contains("filter") -> "FILTER"
            lower.contains("aggregate") || lower.contains("group") -> "AGGREGATE"
            lower.contains("sort") || lower.contains("order") -> "SORT"
            lower.contains("projection") -> "PROJECTION"
            lower.contains("limit") -> "LIMIT"
            else -> "OPERATOR"
        }
    }

    private fun extractOperationTitle(detail: String): String {
        val trimmed = detail.trim().removePrefix("->").trim()
        val lower = trimmed.lowercase()
        return when {
            lower.contains("scan") && lower.contains("index") -> "🔍 Index Scan"
            lower.contains("scan") -> "⚠️ Full Table Scan"
            lower.contains("search") -> "🎯 Index Search"
            lower.contains("join") -> "🔗 Join Operation"
            lower.contains("aggregate") -> "∑ Aggregation"
            lower.contains("filter") -> "⚡ Filter"
            lower.contains("sort") || lower.contains("order") -> "🔃 Sort"
            lower.contains("group") -> "📊 Group By"
            lower.contains("projection") -> "📋 Projection"
            lower.contains("limit") -> "✂️ Limit"
            else -> trimmed.take(32)
        }
    }
}
