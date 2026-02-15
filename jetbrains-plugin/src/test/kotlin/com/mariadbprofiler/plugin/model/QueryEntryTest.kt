package com.mariadbprofiler.plugin.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryEntryTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parse basic query entry from JSON`() {
        val jsonStr = """{"k":"job1","q":"SELECT * FROM users WHERE id = 1","ts":1705970401.123,"tag":"api"}"""
        val entry = json.decodeFromString<QueryEntry>(jsonStr)

        assertEquals("SELECT * FROM users WHERE id = 1", entry.query)
        assertEquals(1705970401.123, entry.timestamp)
        assertEquals(listOf("api"), entry.tags)
        assertEquals(QueryType.SELECT, entry.queryType)
    }

    @Test
    fun `detect query types correctly`() {
        assertEquals(QueryType.SELECT, QueryEntry(query = "SELECT * FROM users").queryType)
        assertEquals(QueryType.INSERT, QueryEntry(query = "INSERT INTO users (name) VALUES ('test')").queryType)
        assertEquals(QueryType.UPDATE, QueryEntry(query = "UPDATE users SET name = 'test'").queryType)
        assertEquals(QueryType.DELETE, QueryEntry(query = "DELETE FROM users WHERE id = 1").queryType)
        assertEquals(QueryType.OTHER, QueryEntry(query = "SHOW TABLES").queryType)
    }

    @Test
    fun `detect query type case insensitive`() {
        assertEquals(QueryType.SELECT, QueryEntry(query = "  select * from users").queryType)
        assertEquals(QueryType.INSERT, QueryEntry(query = "  insert into users values (1)").queryType)
    }

    @Test
    fun `extract table names from SELECT`() {
        val entry = QueryEntry(query = "SELECT u.*, p.title FROM users u JOIN posts p ON p.user_id = u.id")
        val tables = entry.tables
        assertTrue("users" in tables)
        assertTrue("posts" in tables)
    }

    @Test
    fun `extract table names from INSERT`() {
        val entry = QueryEntry(query = "INSERT INTO users (name, email) VALUES ('test', 'test@test.com')")
        assertTrue("users" in entry.tables)
    }

    @Test
    fun `extract table names from UPDATE`() {
        val entry = QueryEntry(query = "UPDATE users SET name = 'test' WHERE id = 1")
        assertTrue("users" in entry.tables)
    }

    @Test
    fun `short SQL truncates long queries`() {
        val longQuery = "SELECT " + "a, ".repeat(50) + "b FROM users"
        val entry = QueryEntry(query = longQuery)
        assertTrue(entry.shortSql.length <= 80)
        assertTrue(entry.shortSql.endsWith("..."))
    }

    @Test
    fun `short SQL preserves short queries`() {
        val entry = QueryEntry(query = "SELECT * FROM users")
        assertEquals("SELECT * FROM users", entry.shortSql)
    }

    @Test
    fun `parse entry with backtrace`() {
        val jsonStr = """
        {
            "k": "job1",
            "q": "SELECT * FROM users",
            "ts": 1705970401.0,
            "trace": [
                {"call": "UserController->index", "file": "/app/Controllers/UserController.php", "line": 42}
            ]
        }
        """.trimIndent()
        val entry = json.decodeFromString<QueryEntry>(jsonStr)

        assertEquals(1, entry.backtrace.size)
        assertEquals("/app/Controllers/UserController.php", entry.backtrace[0].file)
        assertEquals(42, entry.backtrace[0].line)
        assertEquals("UserController.php:42", entry.sourceFile)
    }
}
