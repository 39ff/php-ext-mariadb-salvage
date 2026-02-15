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
        var tailBufferSize: Int = 500
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
}
