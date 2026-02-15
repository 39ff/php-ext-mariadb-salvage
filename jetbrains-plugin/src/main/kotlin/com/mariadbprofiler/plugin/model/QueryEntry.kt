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
    @SerialName("s")
    val status: String? = null,
    @SerialName("tag")
    val tag: String? = null,
    @SerialName("params")
    val params: List<String?> = emptyList(),
    @SerialName("trace")
    val trace: List<BacktraceFrame> = emptyList()
) {
    /** Whether this query has bound parameters (prepared statement) */
    val hasParams: Boolean
        get() = params.isNotEmpty()

    /**
     * Query with `?` placeholders replaced by bound values.
     *
     * Replaces `?` characters that appear **outside** quoted contexts and
     * SQL comments.  The parser recognises:
     *  - Single-quoted string literals (`'...'`) with `''` and backslash
     *    escapes (`\'`, `\\`, etc.), matching MySQL/MariaDB default behavior
     *    (when `NO_BACKSLASH_ESCAPES` is not set)
     *  - Double-quoted string literals (`"..."`) with `""` as escaped quote
     *  - Backtick-quoted identifiers (`` `...` ``)
     *  - Line comments (`-- ...` where `--` is followed by whitespace)
     *  - Hash comments (`# ...`)
     *  - Block comments (`/* ... */`)
     *
     * Param values are wrapped in single quotes with internal backslashes
     * doubled and single-quotes doubled; NULL params are emitted bare.
     */
    val boundQuery: String?
        get() {
            if (params.isEmpty()) return null

            val sb = StringBuilder(query.length + params.size * 8)
            var paramIdx = 0
            var i = 0

            while (i < query.length) {
                val ch = query[i]

                // -- line comment (MySQL/MariaDB requires space/tab/newline after --)
                if (ch == '-' && i + 1 < query.length && query[i + 1] == '-'
                    && i + 2 < query.length && (query[i + 2] == ' ' || query[i + 2] == '\t' || query[i + 2] == '\n')) {
                    val eol = query.indexOf('\n', i)
                    if (eol == -1) {
                        sb.append(query, i, query.length)
                        i = query.length
                    } else {
                        sb.append(query, i, eol + 1)
                        i = eol + 1
                    }
                    continue
                }

                // # line comment (MySQL/MariaDB): copy through to end of line
                if (ch == '#') {
                    val eol = query.indexOf('\n', i)
                    if (eol == -1) {
                        sb.append(query, i, query.length)
                        i = query.length
                    } else {
                        sb.append(query, i, eol + 1)
                        i = eol + 1
                    }
                    continue
                }

                // /* block comment */: copy through to closing */
                if (ch == '/' && i + 1 < query.length && query[i + 1] == '*') {
                    val close = query.indexOf("*/", i + 2)
                    if (close == -1) {
                        sb.append(query, i, query.length)
                        i = query.length
                    } else {
                        sb.append(query, i, close + 2)
                        i = close + 2
                    }
                    continue
                }

                // Quoted context: single-quote, double-quote, or backtick
                if (ch == '\'' || ch == '"' || ch == '`') {
                    val quote = ch
                    sb.append(ch)
                    i++
                    while (i < query.length) {
                        val qch = query[i]
                        // Backslash escape inside single- and double-quoted strings
                        if (qch == '\\' && (quote == '\'' || quote == '"')) {
                            sb.append(qch)
                            if (i + 1 < query.length) {
                                sb.append(query[i + 1])
                                i += 2
                            } else {
                                i++
                            }
                            continue
                        }
                        if (qch == quote) {
                            // doubled-quote escape ('' / "" inside their respective literals)
                            if (i + 1 < query.length && query[i + 1] == quote) {
                                sb.append(quote)
                                sb.append(quote)
                                i += 2
                                continue
                            }
                            // closing quote
                            sb.append(quote)
                            i++
                            break
                        }
                        sb.append(qch)
                        i++
                    }
                    continue
                }

                // Parameter placeholder
                if (ch == '?') {
                    if (paramIdx < params.size) {
                        val v = params[paramIdx++]
                        if (v == null) {
                            sb.append("NULL")
                        } else {
                            sb.append('\'')
                            sb.append(v.replace("\\", "\\\\").replace("'", "''"))
                            sb.append('\'')
                        }
                    } else {
                        sb.append(ch) // more ?'s than params – keep as-is
                    }
                    i++
                    continue
                }

                sb.append(ch)
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
