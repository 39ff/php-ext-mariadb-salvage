package com.mariadbprofiler.plugin.service

import com.mariadbprofiler.plugin.model.JobData
import com.mariadbprofiler.plugin.model.JobsFile
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobsFileRoundtripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Test
    fun `serialize and deserialize JobsFile roundtrip`() {
        val original = JobsFile(
            activeJobs = mapOf(
                "abc-123" to JobData(startedAt = 1705970401.0, parent = null)
            ),
            completedJobs = mapOf(
                "def-456" to JobData(startedAt = 1705970300.0, endedAt = 1705970400.0, queryCount = 42, parent = null)
            )
        )

        val encoded = json.encodeToString(JobsFile.serializer(), original)
        val decoded = json.decodeFromString<JobsFile>(encoded)

        assertEquals(1, decoded.activeJobs.size)
        assertEquals(1, decoded.completedJobs.size)
        assertEquals(1705970401.0, decoded.activeJobs["abc-123"]?.startedAt)
        assertEquals(42, decoded.completedJobs["def-456"]?.queryCount)
    }

    @Test
    fun `serialize empty JobsFile`() {
        val empty = JobsFile()
        val encoded = json.encodeToString(JobsFile.serializer(), empty)
        val decoded = json.decodeFromString<JobsFile>(encoded)

        assertTrue(decoded.activeJobs.isEmpty())
        assertTrue(decoded.completedJobs.isEmpty())
    }

    @Test
    fun `deserialize PHP-format jobs json with empty arrays`() {
        // PHP encodes empty associative arrays as [] not {}
        val phpJson = """{"active_jobs":[],"completed_jobs":[]}"""
        val decoded = json.decodeFromString<JobsFile>(phpJson)

        assertTrue(decoded.activeJobs.isEmpty())
        assertTrue(decoded.completedJobs.isEmpty())
    }

    @Test
    fun `add job then stop job roundtrip`() {
        // Simulate startJob
        val initial = JobsFile()
        val key = "test-job-key"
        val startTime = 1705970500.0

        val afterStart = JobsFile(
            activeJobs = initial.activeJobs.toMutableMap().also {
                it[key] = JobData(startedAt = startTime, parent = null)
            },
            completedJobs = initial.completedJobs
        )

        val encoded1 = json.encodeToString(JobsFile.serializer(), afterStart)
        val decoded1 = json.decodeFromString<JobsFile>(encoded1)
        assertEquals(1, decoded1.activeJobs.size)
        assertTrue(decoded1.activeJobs.containsKey(key))

        // Simulate stopJob
        val jobData = decoded1.activeJobs[key]!!
        val endTime = 1705970600.0
        val afterStop = JobsFile(
            activeJobs = decoded1.activeJobs.toMutableMap().also { it.remove(key) },
            completedJobs = decoded1.completedJobs.toMutableMap().also {
                it[key] = JobData(
                    startedAt = jobData.startedAt,
                    endedAt = endTime,
                    parent = jobData.parent,
                    queryCount = 10
                )
            }
        )

        val encoded2 = json.encodeToString(JobsFile.serializer(), afterStop)
        val decoded2 = json.decodeFromString<JobsFile>(encoded2)

        assertTrue(decoded2.activeJobs.isEmpty())
        assertEquals(1, decoded2.completedJobs.size)
        assertEquals(startTime, decoded2.completedJobs[key]?.startedAt)
        assertEquals(endTime, decoded2.completedJobs[key]?.endedAt)
        assertEquals(10, decoded2.completedJobs[key]?.queryCount)
    }
}
