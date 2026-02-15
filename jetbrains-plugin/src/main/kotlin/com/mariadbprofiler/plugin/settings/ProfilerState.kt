package com.mariadbprofiler.plugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "MariaDbProfilerSettings",
    storages = [Storage("MariaDbProfiler.xml")]
)
class ProfilerState : PersistentStateComponent<ProfilerState.State> {

    data class State(
        var logDir: String = "/tmp/mariadb_profiler",
        var phpPath: String = "php",
        var cliScriptPath: String = "",
        var maxQueries: Int = 10000,
        var refreshInterval: Int = 5,
        var tailBufferSize: Int = 500,
        /** Tag-to-depth mapping, e.g. "laravel=8,symfony=7,default=0" */
        var tagDepthMapping: String = "default=0"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var logDir: String
        get() = myState.logDir
        set(value) { myState.logDir = value }

    var phpPath: String
        get() = myState.phpPath
        set(value) { myState.phpPath = value }

    var cliScriptPath: String
        get() = myState.cliScriptPath
        set(value) { myState.cliScriptPath = value }

    var maxQueries: Int
        get() = myState.maxQueries
        set(value) { myState.maxQueries = value }

    var refreshInterval: Int
        get() = myState.refreshInterval
        set(value) { myState.refreshInterval = value }

    var tailBufferSize: Int
        get() = myState.tailBufferSize
        set(value) { myState.tailBufferSize = value }

    var tagDepthMapping: String
        get() = myState.tagDepthMapping
        set(value) { myState.tagDepthMapping = value }

    /**
     * Parse tagDepthMapping into a Map.
     * Format: "laravel=8,symfony=7,default=0"
     */
    fun getTagDepthMap(): Map<String, Int> {
        return tagDepthMapping.split(",")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val (tag, depth) = it.split("=", limit = 2)
                tag.trim() to (depth.trim().toIntOrNull() ?: 0)
            }
    }

    /**
     * Get the depth for a given tag. Falls back to "default" entry, then 0.
     */
    fun getDepthForTag(tag: String?): Int {
        val map = getTagDepthMap()
        if (tag != null && map.containsKey(tag)) return map[tag]!!
        return map["default"] ?: 0
    }
}
