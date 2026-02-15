package com.mariadbprofiler.plugin.service

import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.model.QueryType
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parse JSONL content with actual PHP field names`() {
        val lines = listOf(
            """{"k":"job1","q":"SELECT * FROM users","ts":1705970401.0,"tag":"api"}""",
            """{"k":"job1","q":"INSERT INTO logs (msg) VALUES ('test')","ts":1705970402.0}""",
            """{"k":"job1","q":"UPDATE users SET name = 'foo'","ts":1705970403.0,"tag":"web"}"""
        )

        val entries = lines.map { json.decodeFromString<QueryEntry>(it) }

        assertEquals(3, entries.size)
        assertEquals(QueryType.SELECT, entries[0].queryType)
        assertEquals(QueryType.INSERT, entries[1].queryType)
        assertEquals(QueryType.UPDATE, entries[2].queryType)
        assertEquals("SELECT * FROM users", entries[0].query)
        assertEquals(listOf("api"), entries[0].tags)
        assertEquals(emptyList(), entries[1].tags)
    }

    @Test
    fun `parse JSONL file from temp file`() {
        val tempFile = File.createTempFile("profiler_test", ".jsonl")
        try {
            tempFile.writeText(
                """{"k":"job1","q":"SELECT 1","ts":1.0}
{"k":"job1","q":"SELECT 2","ts":2.0}
"""
            )

            val entries = mutableListOf<QueryEntry>()
            tempFile.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        entries.add(json.decodeFromString<QueryEntry>(trimmed))
                    }
                }
            }

            assertEquals(2, entries.size)
            assertEquals("SELECT 1", entries[0].query)
            assertEquals("SELECT 2", entries[1].query)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `skip malformed lines gracefully`() {
        val lines = listOf(
            """{"k":"job1","q":"SELECT 1","ts":1.0}""",
            """not valid json""",
            """{"k":"job1","q":"SELECT 2","ts":2.0}"""
        )

        val entries = mutableListOf<QueryEntry>()
        for (line in lines) {
            try {
                entries.add(json.decodeFromString<QueryEntry>(line.trim()))
            } catch (_: Exception) {
                // skip
            }
        }

        assertEquals(2, entries.size)
    }

    @Test
    fun `parse entry with backtrace frames`() {
        val line = """{"k":"job1","q":"SELECT * FROM users","ts":1.0,"tag":"api","trace":[{"call":"UserController->index","file":"/app/UserController.php","line":42}]}"""
        val entry = json.decodeFromString<QueryEntry>(line)

        assertEquals(1, entry.backtrace.size)
        assertEquals("/app/UserController.php", entry.backtrace[0].file)
        assertEquals(42, entry.backtrace[0].line)
        assertEquals("UserController->index", entry.backtrace[0].call)
    }

    @Test
    fun `statistics computation from entries`() {
        val entries = listOf(
            QueryEntry(query = "SELECT * FROM users", timestamp = 1.0, tag = "api"),
            QueryEntry(query = "SELECT * FROM posts", timestamp = 2.0, tag = "api"),
            QueryEntry(query = "INSERT INTO logs (msg) VALUES ('x')", timestamp = 3.0, tag = "web"),
            QueryEntry(query = "UPDATE users SET name='y'", timestamp = 4.0, tag = "api")
        )

        val byType = entries.groupBy { it.queryType }.mapValues { it.value.size }
        assertEquals(2, byType[QueryType.SELECT])
        assertEquals(1, byType[QueryType.INSERT])
        assertEquals(1, byType[QueryType.UPDATE])

        val byTable = mutableMapOf<String, Int>()
        entries.forEach { entry ->
            entry.tables.forEach { table ->
                byTable[table] = (byTable[table] ?: 0) + 1
            }
        }
        assertEquals(2, byTable["users"])  // SELECT + UPDATE from 'users'
        assertTrue((byTable["posts"] ?: 0) > 0)
        assertTrue((byTable["logs"] ?: 0) > 0)
    }
}
