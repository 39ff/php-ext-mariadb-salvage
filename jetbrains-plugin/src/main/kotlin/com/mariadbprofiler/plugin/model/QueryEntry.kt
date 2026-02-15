package com.mariadbprofiler.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryEntry(
    @SerialName("q")
    val query: String = "",
    @SerialName("ts")
    val timestamp: Double = 0.0,
    @SerialName("k")
    val jobKey: String = "",
    val tag: String? = null,
    val params: List<String?> = emptyList(),
    val trace: List<BacktraceFrame> = emptyList()
) {
    /** Whether this query has bound parameters (prepared statement) */
    val hasParams: Boolean
        get() = params.isNotEmpty()

    /** Query with ? placeholders replaced by bound values */
    val boundQuery: String?
        get() {
            if (params.isEmpty()) return null
            var result = query
            for (param in params) {
                val replacement = if (param == null) "NULL" else "'$param'"
                result = result.replaceFirst("?", replacement)
            }
            return result
        }
    /** Tag as list for UI display compatibility */
    val tags: List<String>
        get() = if (tag != null) listOf(tag) else emptyList()

    /** Backtrace alias */
    val backtrace: List<BacktraceFrame>
        get() = trace

    val queryType: QueryType
        get() {
            val trimmed = query.trimStart().uppercase()
            return when {
                trimmed.startsWith("SELECT") -> QueryType.SELECT
                trimmed.startsWith("INSERT") -> QueryType.INSERT
                trimmed.startsWith("UPDATE") -> QueryType.UPDATE
                trimmed.startsWith("DELETE") -> QueryType.DELETE
                else -> QueryType.OTHER
            }
        }

    val shortSql: String
        get() {
            val oneLine = query.replace(Regex("\\s+"), " ").trim()
            return if (oneLine.length > 80) oneLine.take(77) + "..." else oneLine
        }

    val formattedTimestamp: String
        get() {
            val millis = (timestamp * 1000).toLong()
            val date = java.util.Date(millis)
            val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS")
            return fmt.format(date)
        }

    val sourceFile: String
        get() = backtrace.firstOrNull()?.let {
            "${java.io.File(it.file).name}:${it.line}"
        } ?: ""

    val tables: List<String>
        get() {
            val tableNames = mutableListOf<String>()
            val patterns = listOf(
                Regex("\\bFROM\\s+`?(\\w+)`?", RegexOption.IGNORE_CASE),
                Regex("\\bJOIN\\s+`?(\\w+)`?", RegexOption.IGNORE_CASE),
                Regex("\\bINTO\\s+`?(\\w+)`?", RegexOption.IGNORE_CASE),
                Regex("\\bUPDATE\\s+`?(\\w+)`?", RegexOption.IGNORE_CASE)
            )
            for (pattern in patterns) {
                pattern.findAll(query).forEach { match ->
                    val tableName = match.groupValues[1]
                    if (tableName.isNotEmpty() && tableName !in tableNames) {
                        tableNames.add(tableName)
                    }
                }
            }
            return tableNames
        }
}

enum class QueryType(val label: String) {
    SELECT("SELECT"),
    INSERT("INSERT"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    OTHER("OTHER")
}
