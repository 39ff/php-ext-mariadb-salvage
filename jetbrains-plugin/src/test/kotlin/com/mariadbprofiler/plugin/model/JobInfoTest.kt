package com.mariadbprofiler.plugin.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobInfoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `active job has no end time`() {
        val job = JobInfo(key = "test-key", startedAt = 1705970401.0)
        assertTrue(job.isActive)
        assertNull(job.endedAt)
        assertEquals("running...", job.formattedDuration)
    }

    @Test
    fun `completed job has end time and duration`() {
        val job = JobInfo(
            key = "test-key",
            startedAt = 1705970401.0,
            endedAt = 1705970411.0,
            queryCount = 42
        )
        assertFalse(job.isActive)
        assertEquals(10.0, job.durationSeconds)
        assertEquals("10.0 s", job.formattedDuration)
    }

    @Test
    fun `short duration displays as milliseconds`() {
        val job = JobInfo(
            key = "test",
            startedAt = 1705970401.0,
            endedAt = 1705970401.5
        )
        assertEquals("500 ms", job.formattedDuration)
    }

    @Test
    fun `long duration displays as minutes`() {
        val job = JobInfo(
            key = "test",
            startedAt = 1705970401.0,
            endedAt = 1705970581.0 // 180 seconds
        )
        assertEquals("3.0 min", job.formattedDuration)
    }

    @Test
    fun `parse jobs file`() {
        val content = """
        {
            "active_jobs": {
                "abc123": {"started_at": 1705970401.0, "parent": null}
            },
            "completed_jobs": {
                "def456": {"started_at": 1705970300.0, "ended_at": 1705970401.0, "query_count": 42, "parent": null}
            }
        }
        """.trimIndent()

        val jobsFile = json.decodeFromString<JobsFile>(content)
        assertEquals(1, jobsFile.activeJobs.size)
        assertEquals(1, jobsFile.completedJobs.size)
        assertEquals(1705970401.0, jobsFile.activeJobs["abc123"]?.startedAt)
        assertEquals(42, jobsFile.completedJobs["def456"]?.queryCount)
    }
}
