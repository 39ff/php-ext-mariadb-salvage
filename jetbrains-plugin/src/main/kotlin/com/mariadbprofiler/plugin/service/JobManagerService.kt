package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.JobData
import com.mariadbprofiler.plugin.model.JobInfo
import com.mariadbprofiler.plugin.model.JobsFile
import com.mariadbprofiler.plugin.settings.ProfilerState
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.UUID

@Service(Service.Level.PROJECT)
class JobManagerService(private val project: Project) {

    private val log = Logger.getInstance(JobManagerService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonPretty = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    fun getLogDirectory(): String {
        return service<ProfilerState>().logDir
    }

    private fun getJobsFile(): File = File(getLogDirectory(), "jobs.json")

    /**
     * Read jobs.json with shared (read) lock, matching PHP's LOCK_SH behavior.
     */
    private fun readJobsFile(): JobsFile {
        val file = getJobsFile()
        if (!file.exists()) {
            return JobsFile()
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.channel.lock(0L, Long.MAX_VALUE, true).use {
                    val bytes = ByteArray(raf.length().toInt())
                    raf.readFully(bytes)
                    val content = String(bytes, Charsets.UTF_8)
                    if (content.isBlank()) JobsFile()
                    else json.decodeFromString<JobsFile>(content)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to read jobs.json: ${e.message}")
            JobsFile()
        }
    }

    /**
     * Write jobs.json with exclusive lock, matching PHP's LOCK_EX behavior.
     */
    private fun writeJobsFile(jobsFile: JobsFile) {
        val dir = File(getLogDirectory())
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val file = getJobsFile()
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.lock(0L, Long.MAX_VALUE, false).use {
                raf.setLength(0)
                raf.seek(0)
                val content = jsonPretty.encodeToString(JobsFile.serializer(), jobsFile)
                raf.write(content.toByteArray(Charsets.UTF_8))
                raf.fd.sync()
            }
        }
    }

    /**
     * Start a new profiling job. Returns the generated job key.
     */
    fun startJob(): String {
        val key = UUID.randomUUID().toString()
        val current = readJobsFile()

        // Find parent (most recently started active job)
        val parent = current.activeJobs.maxByOrNull { it.value.startedAt }?.key

        val newActive = current.activeJobs.toMutableMap()
        newActive[key] = JobData(
            startedAt = System.currentTimeMillis() / 1000.0,
            parent = parent
        )

        writeJobsFile(JobsFile(activeJobs = newActive, completedJobs = current.completedJobs))
        return key
    }

    /**
     * Stop an active profiling job. Returns query count.
     */
    fun stopJob(key: String): Int {
        val current = readJobsFile()

        val jobData = current.activeJobs[key]
            ?: throw IllegalStateException("Job '$key' is not active")

        val queryCount = countQueriesInJsonl(key)

        val newActive = current.activeJobs.toMutableMap()
        newActive.remove(key)

        val newCompleted = current.completedJobs.toMutableMap()
        newCompleted[key] = JobData(
            startedAt = jobData.startedAt,
            endedAt = System.currentTimeMillis() / 1000.0,
            parent = jobData.parent,
            queryCount = queryCount
        )

        writeJobsFile(JobsFile(activeJobs = newActive, completedJobs = newCompleted))
        return queryCount
    }

    private fun countQueriesInJsonl(key: String): Int {
        val file = File(getLogDirectory(), "$key.jsonl")
        if (!file.exists()) return 0
        return file.useLines { lines -> lines.count { it.isNotBlank() } }
    }

    fun loadJobs(): List<JobInfo> {
        return try {
            val jobsFile = readJobsFile()
            val jobs = mutableListOf<JobInfo>()

            jobsFile.activeJobs.forEach { (key, data) ->
                jobs.add(data.toJobInfo(key, isActive = true))
            }
            jobsFile.completedJobs.forEach { (key, data) ->
                jobs.add(data.toJobInfo(key, isActive = false))
            }

            jobs.sortedByDescending { it.startedAt }
        } catch (e: Exception) {
            log.error("Failed to load jobs: ${e.message}")
            val errorLog = project.getService(ErrorLogService::class.java)
            errorLog.addError("JobManager", "Failed to load jobs: ${e.message}")
            emptyList()
        }
    }

    fun getActiveJobs(): List<JobInfo> = loadJobs().filter { it.isActive }

    fun getCompletedJobs(): List<JobInfo> = loadJobs().filter { !it.isActive }

    fun getJsonlPath(jobKey: String): String {
        return File(getLogDirectory(), "$jobKey.jsonl").absolutePath
    }

    fun getRawLogPath(jobKey: String): String {
        return File(getLogDirectory(), "$jobKey.raw.log").absolutePath
    }

    fun getAvailableLogFiles(): List<String> {
        val dir = File(getLogDirectory())
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "jsonl" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    private fun JobData.toJobInfo(key: String, isActive: Boolean): JobInfo {
        return JobInfo(
            key = key,
            startedAt = startedAt,
            endedAt = endedAt,
            queryCount = queryCount,
            parent = parent,
            isActive = isActive
        )
    }
}
