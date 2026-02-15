package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.model.QueryType

@Service(Service.Level.PROJECT)
class StatisticsService(private val project: Project) {

    data class QueryStats(
        val totalQueries: Int,
        val byType: Map<QueryType, Int>,
        val byTable: Map<String, Int>,
        val byTag: Map<String, Int>,
        val timelinePoints: List<TimelinePoint>
    )

    data class TimelinePoint(
        val timestamp: Double,
        val queryCount: Int,
        val queryType: QueryType
    )

    fun computeStats(entries: List<QueryEntry>): QueryStats {
        val byType = entries.groupBy { it.queryType }
            .mapValues { it.value.size }

        val byTable = mutableMapOf<String, Int>()
        entries.forEach { entry ->
            entry.tables.forEach { table ->
                byTable[table] = (byTable[table] ?: 0) + 1
            }
        }

        val byTag = mutableMapOf<String, Int>()
        entries.forEach { entry ->
            entry.tags.forEach { tag ->
                byTag[tag] = (byTag[tag] ?: 0) + 1
            }
        }

        val timeline = entries.mapIndexed { index, entry ->
            TimelinePoint(
                timestamp = entry.timestamp,
                queryCount = index + 1,
                queryType = entry.queryType
            )
        }

        return QueryStats(
            totalQueries = entries.size,
            byType = byType,
            byTable = byTable.toMap(),
            byTag = byTag.toMap(),
            timelinePoints = timeline
        )
    }
}
