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

    @Test
    fun `boundQuery replaces placeholders with params`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = ? AND age = ?",
            params = listOf("John", "25")
        )
        assertEquals("SELECT * FROM users WHERE name = 'John' AND age = '25'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles NULL params`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = ? AND email = ?",
            params = listOf("John", null)
        )
        assertEquals("SELECT * FROM users WHERE name = 'John' AND email = NULL", entry.boundQuery)
    }

    @Test
    fun `boundQuery escapes single quotes in params`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = ?",
            params = listOf("O'Brien")
        )
        assertEquals("SELECT * FROM users WHERE name = 'O''Brien'", entry.boundQuery)
    }

    @Test
    fun `boundQuery does not replace placeholders inside single-quoted strings`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = 'test?' AND age = ?",
            params = listOf("25")
        )
        assertEquals("SELECT * FROM users WHERE name = 'test?' AND age = '25'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles doubled single-quotes inside strings`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = 'O''Brien' AND age = ?",
            params = listOf("25")
        )
        assertEquals("SELECT * FROM users WHERE name = 'O''Brien' AND age = '25'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles backslash-escaped single quotes`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = 'O\\'Brien' AND age = ?",
            params = listOf("25")
        )
        assertEquals("SELECT * FROM users WHERE name = 'O\\'Brien' AND age = '25'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles backslash-escaped backslashes`() {
        val entry = QueryEntry(
            query = "SELECT * FROM paths WHERE path = 'C:\\\\Users\\\\test' AND id = ?",
            params = listOf("1")
        )
        assertEquals("SELECT * FROM paths WHERE path = 'C:\\\\Users\\\\test' AND id = '1'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles mixed escape sequences`() {
        val entry = QueryEntry(
            query = "SELECT * FROM data WHERE value = 'test\\nline' AND name = ?",
            params = listOf("John")
        )
        assertEquals("SELECT * FROM data WHERE value = 'test\\nline' AND name = 'John'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles placeholder at end of backslash-escaped string`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = 'can\\'t' AND status = ?",
            params = listOf("active")
        )
        assertEquals("SELECT * FROM users WHERE name = 'can\\'t' AND status = 'active'", entry.boundQuery)
    }

    @Test
    fun `boundQuery returns null when no params`() {
        val entry = QueryEntry(query = "SELECT * FROM users")
        assertEquals(null, entry.boundQuery)
    }

    @Test
    fun `boundQuery handles more placeholders than params`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = ? AND age = ? AND email = ?",
            params = listOf("John", "25")
        )
        // Should replace first two, leave third as-is
        assertEquals("SELECT * FROM users WHERE name = 'John' AND age = '25' AND email = ?", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles complex nested strings`() {
        val entry = QueryEntry(
            query = "SELECT * FROM logs WHERE msg = 'User said \\'hello\\'' AND id = ?",
            params = listOf("123")
        )
        assertEquals("SELECT * FROM logs WHERE msg = 'User said \\'hello\\'' AND id = '123'", entry.boundQuery)
    }
}
