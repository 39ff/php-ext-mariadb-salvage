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

@Service(Service.Level.PROJECT)
class JobManagerService(private val project: Project) {

    private val log = Logger.getInstance(JobManagerService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getLogDirectory(): String {
        return service<ProfilerState>().logDir
    }

    fun loadJobs(): List<JobInfo> {
        val jobsFilePath = File(getLogDirectory(), "jobs.json")
        if (!jobsFilePath.exists()) {
            log.info("jobs.json not found at: ${jobsFilePath.absolutePath}")
            return emptyList()
        }

        return try {
            val content = jobsFilePath.readText()
            val jobsFile = json.decodeFromString<JobsFile>(content)
            val jobs = mutableListOf<JobInfo>()

            jobsFile.activeJobs.forEach { (key, data) ->
                jobs.add(data.toJobInfo(key, isActive = true))
            }
            jobsFile.completedJobs.forEach { (key, data) ->
                jobs.add(data.toJobInfo(key, isActive = false))
            }

            jobs.sortedByDescending { it.startedAt }
        } catch (e: Exception) {
            log.error("Failed to parse jobs.json: ${e.message}")
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
