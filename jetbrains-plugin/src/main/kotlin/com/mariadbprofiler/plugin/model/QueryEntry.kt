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

    /**
     * Query with `?` placeholders replaced by bound values.
     *
     * Only replaces `?` characters that appear **outside** single-quoted SQL
     * string literals.  Handles both doubled single-quotes (`''`) and backslash
     * escapes (`\'`, `\\`, etc.) inside literals, matching MySQL/MariaDB default
     * behavior (when `NO_BACKSLASH_ESCAPES` is not set).
     *
     * Param values are wrapped in single quotes with internal single-quotes
     * doubled (`O'Brien` → `'O''Brien'`); NULL params are emitted bare.
     */
    val boundQuery: String?
        get() {
            if (params.isEmpty()) return null

            val sb = StringBuilder(query.length + params.size * 8)
            var paramIdx = 0
            var inString = false
            var i = 0

            while (i < query.length) {
                val ch = query[i]

                if (inString) {
                    if (ch == '\\') {
                        /* backslash escape: \' \\ etc. – copy both chars, stay in string */
                        if (i + 1 < query.length) {
                            sb.append(ch)
                            sb.append(query[i + 1])
                            i += 2
                            continue
                        }
                        /* trailing backslash – just append it */
                        sb.append(ch)
                    } else if (ch == '\'') {
                        /* '' inside a literal is an escaped quote – stay in string */
                        if (i + 1 < query.length && query[i + 1] == '\'') {
                            sb.append("''")
                            i += 2
                            continue
                        }
                        /* closing quote */
                        inString = false
                        sb.append(ch)
                    } else {
                        sb.append(ch)
                    }
                } else {
                    when (ch) {
                        '\'' -> {
                            inString = true
                            sb.append(ch)
                        }
                        '?' -> {
                            if (paramIdx < params.size) {
                                val v = params[paramIdx++]
                                if (v == null) {
                                    sb.append("NULL")
                                } else {
                                    sb.append('\'')
                                    sb.append(v.replace("'", "''"))
                                    sb.append('\'')
                                }
                            } else {
                                sb.append(ch) // more ?'s than params – keep as-is
                            }
                        }
                        else -> sb.append(ch)
                    }
                }
                i++
            }
            return sb.toString()
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
