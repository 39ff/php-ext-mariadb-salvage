package com.mariadbprofiler.plugin.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `short SQL shows bound values instead of placeholders`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE id = ?",
            params = listOf("42")
        )
        assertEquals("SELECT * FROM users WHERE id = '42'", entry.shortSql)
    }

    @Test
    fun `short SQL shows bound values and truncates long bound query`() {
        val entry = QueryEntry(
            query = "SELECT * FROM users WHERE name = ? AND email = ? AND status = ?",
            params = listOf("John", "john@example.com", "active")
        )
        assertTrue(entry.shortSql.length <= 80)
        assertTrue(entry.shortSql.endsWith("..."))
        assertTrue(entry.shortSql.contains("'John'"))
    }

    @Test
    fun `short SQL falls back to query when no params`() {
        val entry = QueryEntry(query = "SELECT * FROM users WHERE id = ?")
        assertEquals("SELECT * FROM users WHERE id = ?", entry.shortSql)
    }

    // ---- boundQuery tests ----

    @Test
    fun `boundQuery returns null when no params`() {
        val entry = QueryEntry(query = "SELECT 1")
        assertNull(entry.boundQuery)
    }

    @Test
    fun `boundQuery replaces single placeholder`() {
        val entry = QueryEntry(query = "SELECT * FROM users WHERE id = ?", params = listOf("42"))
        assertEquals("SELECT * FROM users WHERE id = '42'", entry.boundQuery)
    }

    @Test
    fun `boundQuery replaces multiple placeholders`() {
        val entry = QueryEntry(
            query = "INSERT INTO t (a, b, c) VALUES (?, ?, ?)",
            params = listOf("x", "y", "z")
        )
        assertEquals("INSERT INTO t (a, b, c) VALUES ('x', 'y', 'z')", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles NULL params`() {
        val entry = QueryEntry(
            query = "INSERT INTO t (a, b) VALUES (?, ?)",
            params = listOf(null, "val")
        )
        assertEquals("INSERT INTO t (a, b) VALUES (NULL, 'val')", entry.boundQuery)
    }

    @Test
    fun `boundQuery keeps extra placeholders when fewer params than placeholders`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE a = ? AND b = ? AND c = ?",
            params = listOf("1")
        )
        assertEquals("SELECT * FROM t WHERE a = '1' AND b = ? AND c = ?", entry.boundQuery)
    }

    @Test
    fun `boundQuery ignores extra params when more params than placeholders`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE a = ?",
            params = listOf("1", "2", "3")
        )
        assertEquals("SELECT * FROM t WHERE a = '1'", entry.boundQuery)
    }

    @Test
    fun `boundQuery does not replace placeholder inside single-quoted string`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE a = '?' AND b = ?",
            params = listOf("val")
        )
        assertEquals("SELECT * FROM t WHERE a = '?' AND b = 'val'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles escaped single quotes in SQL literal`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE a = 'it''s' AND b = ?",
            params = listOf("x")
        )
        assertEquals("SELECT * FROM t WHERE a = 'it''s' AND b = 'x'", entry.boundQuery)
    }

    @Test
    fun `boundQuery escapes single quotes in param values`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE name = ?",
            params = listOf("O'Brien")
        )
        assertEquals("SELECT * FROM t WHERE name = 'O''Brien'", entry.boundQuery)
    }

    @Test
    fun `boundQuery escapes backslashes in param values`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE path = ?",
            params = listOf("C:\\tmp")
        )
        assertEquals("SELECT * FROM t WHERE path = 'C:\\\\tmp'", entry.boundQuery)
    }

    @Test
    fun `boundQuery escapes backslash and quote together`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t WHERE v = ?",
            params = listOf("a\\nb's")
        )
        assertEquals("SELECT * FROM t WHERE v = 'a\\\\nb''s'", entry.boundQuery)
    }

    @Test
    fun `boundQuery does not replace placeholder inside double-quoted string`() {
        val entry = QueryEntry(
            query = """SELECT * FROM t WHERE a = "?" AND b = ?""",
            params = listOf("val")
        )
        assertEquals("""SELECT * FROM t WHERE a = "?" AND b = 'val'""", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles escaped double quotes inside double-quoted string`() {
        val entry = QueryEntry(
            query = """SELECT * FROM t WHERE a = "say""what" AND b = ?""",
            params = listOf("x")
        )
        assertEquals("""SELECT * FROM t WHERE a = "say""what" AND b = 'x'""", entry.boundQuery)
    }

    @Test
    fun `boundQuery does not replace placeholder inside backtick-quoted identifier`() {
        val entry = QueryEntry(
            query = "SELECT `?` FROM t WHERE id = ?",
            params = listOf("1")
        )
        assertEquals("SELECT `?` FROM t WHERE id = '1'", entry.boundQuery)
    }

    @Test
    fun `boundQuery skips placeholder in line comment`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t -- where id = ?\nWHERE name = ?",
            params = listOf("test")
        )
        assertEquals("SELECT * FROM t -- where id = ?\nWHERE name = 'test'", entry.boundQuery)
    }

    @Test
    fun `boundQuery skips placeholder in block comment`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t /* ? */ WHERE id = ?",
            params = listOf("42")
        )
        assertEquals("SELECT * FROM t /* ? */ WHERE id = '42'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles block comment with quote inside`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t /* it's ? */ WHERE id = ?",
            params = listOf("1")
        )
        assertEquals("SELECT * FROM t /* it's ? */ WHERE id = '1'", entry.boundQuery)
    }

    @Test
    fun `boundQuery handles unclosed block comment`() {
        val entry = QueryEntry(
            query = "SELECT * /* unclosed ? ",
            params = listOf("x")
        )
        // ? is inside unclosed comment, no replacement
        assertEquals("SELECT * /* unclosed ? ", entry.boundQuery)
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

    // ---- status field tests ----

    @Test
    fun `parse entry with status ok`() {
        val jsonStr = """{"k":"job1","q":"SELECT 1","s":"ok","ts":1705970401.0}"""
        val entry = json.decodeFromString<QueryEntry>(jsonStr)
        assertEquals("ok", entry.status)
    }

    @Test
    fun `parse entry with status err`() {
        val jsonStr = """{"k":"job1","q":"SELECT bad","s":"err","ts":1705970401.0}"""
        val entry = json.decodeFromString<QueryEntry>(jsonStr)
        assertEquals("err", entry.status)
    }

    @Test
    fun `parse entry without status defaults to null`() {
        val jsonStr = """{"k":"job1","q":"SELECT 1","ts":1705970401.0}"""
        val entry = json.decodeFromString<QueryEntry>(jsonStr)
        assertNull(entry.status)
    }

    // ---- hash comment tests ----

    @Test
    fun `boundQuery skips placeholder in hash comment`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t # where id = ?\nWHERE name = ?",
            params = listOf("test")
        )
        assertEquals("SELECT * FROM t # where id = ?\nWHERE name = 'test'", entry.boundQuery)
    }

    @Test
    fun `boundQuery skips placeholder in hash comment at end of query`() {
        val entry = QueryEntry(
            query = "SELECT 1 # comment ?",
            params = listOf("unused")
        )
        assertEquals("SELECT 1 # comment ?", entry.boundQuery)
    }

    // ---- double-dash comment spec tests ----

    @Test
    fun `boundQuery does not treat double-dash without trailing space as comment`() {
        // MySQL/MariaDB requires whitespace after -- for it to be a line comment.
        // "1--?" is arithmetic, not a comment.
        val entry = QueryEntry(
            query = "SELECT 1--?",
            params = listOf("2")
        )
        assertEquals("SELECT 1--'2'", entry.boundQuery)
    }

    @Test
    fun `boundQuery treats double-dash with tab as comment`() {
        val entry = QueryEntry(
            query = "SELECT * FROM t --\twhere id = ?\nWHERE name = ?",
            params = listOf("test")
        )
        assertEquals("SELECT * FROM t --\twhere id = ?\nWHERE name = 'test'", entry.boundQuery)
    }

    @Test
    fun `boundQuery treats double-dash with CRLF as comment`() {
        // --\r\n is a comment that ends at the newline; "where id = ?" is on the
        // next line so its ? is outside the comment and consumes the param.
        val entry = QueryEntry(
            query = "SELECT * FROM t --\r\nwhere id = ?\r\nAND name = ?",
            params = listOf("test")
        )
        assertEquals("SELECT * FROM t --\r\nwhere id = 'test'\r\nAND name = ?", entry.boundQuery)
    }
}
