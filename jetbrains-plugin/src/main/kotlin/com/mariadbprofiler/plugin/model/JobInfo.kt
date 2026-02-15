package com.mariadbprofiler.plugin.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class JobInfo(
    val key: String = "",
    @SerialName("started_at")
    val startedAt: Double = 0.0,
    @SerialName("ended_at")
    val endedAt: Double? = null,
    @SerialName("query_count")
    val queryCount: Int? = null,
    val parent: String? = null,
    @Transient
    val isActive: Boolean = true
) {
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

/**
 * PHP's json_encode turns empty associative arrays into [] (JSON array)
 * but non-empty ones into {} (JSON object). This serializer handles both.
 */
object PhpMapSerializer : KSerializer<Map<String, JobData>> {
    private val mapSerializer = MapSerializer(String.serializer(), JobData.serializer())

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, JobData>) {
        mapSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, JobData> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return mapSerializer.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> emptyMap() // PHP empty array []
            is JsonObject -> jsonDecoder.json.decodeFromJsonElement(mapSerializer, element)
            else -> emptyMap()
        }
    }
}

@Serializable
data class JobsFile(
    @SerialName("active_jobs")
    @Serializable(with = PhpMapSerializer::class)
    val activeJobs: Map<String, JobData> = emptyMap(),
    @SerialName("completed_jobs")
    @Serializable(with = PhpMapSerializer::class)
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
