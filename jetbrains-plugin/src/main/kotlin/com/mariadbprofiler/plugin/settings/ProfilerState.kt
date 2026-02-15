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

    companion object {
        val DEFAULT_FRAME_RESOLVER_SCRIPT = """
            // Available variables:
            //   trace - List of frames (each has: file, line, call, function, class_name)
            //   tag   - Query tag (String, may be null)
            //   query - SQL query (String)
            // Return: index of the backtrace frame to display (int)

            // === Tag-to-depth mapping ===
            def depthMap = [
                'default': 0
            ]

            def depth = depthMap[tag ?: 'default'] ?: depthMap['default'] ?: 0
            if (depth < trace.size()) return depth
            return 0
        """.trimIndent()
    }

    data class State(
        var logDir: String = "/tmp/mariadb_profiler",
        var phpPath: String = "php",
        var cliScriptPath: String = "",
        var maxQueries: Int = 10000,
        var refreshInterval: Int = 5,
        var tailBufferSize: Int = 500,
        var frameResolverScript: String = DEFAULT_FRAME_RESOLVER_SCRIPT
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

    var frameResolverScript: String
        get() = myState.frameResolverScript
        set(value) { myState.frameResolverScript = value }
}
