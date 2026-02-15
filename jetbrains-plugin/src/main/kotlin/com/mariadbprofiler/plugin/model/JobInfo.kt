package com.mariadbprofiler.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JobInfo(
    val key: String = "",
    @SerialName("started_at")
    val startedAt: Double = 0.0,
    @SerialName("ended_at")
    val endedAt: Double? = null,
    @SerialName("query_count")
    val queryCount: Int? = null,
    val parent: String? = null
) {
    val isActive: Boolean
        get() = endedAt == null

    val formattedStartedAt: String
        get() = formatTimestamp(startedAt)

    val formattedEndedAt: String
        get() = endedAt?.let { formatTimestamp(it) } ?: "-"

    val durationSeconds: Double?
        get() = endedAt?.let { it - startedAt }

    val formattedDuration: String
        get() {
            val dur = durationSeconds ?: return "running..."
            return when {
                dur < 1 -> "${(dur * 1000).toInt()} ms"
                dur < 60 -> "%.1f s".format(dur)
                else -> "%.1f min".format(dur / 60)
            }
        }

    private fun formatTimestamp(ts: Double): String {
        val millis = (ts * 1000).toLong()
        val date = java.util.Date(millis)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return fmt.format(date)
    }
}

@Serializable
data class JobsFile(
    @SerialName("active_jobs")
    val activeJobs: Map<String, JobData> = emptyMap(),
    @SerialName("completed_jobs")
    val completedJobs: Map<String, JobData> = emptyMap()
)

@Serializable
data class JobData(
    @SerialName("started_at")
    val startedAt: Double = 0.0,
    @SerialName("ended_at")
    val endedAt: Double? = null,
    @SerialName("query_count")
    val queryCount: Int? = null,
    val parent: String? = null
)
