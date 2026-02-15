package com.mariadbprofiler.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mariadbprofiler.plugin.model.QueryEntry
import com.mariadbprofiler.plugin.settings.ProfilerState
import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.codehaus.groovy.control.CompilerConfiguration

@Service(Service.Level.PROJECT)
class FrameResolverService(private val project: Project) {

    private val log = Logger.getInstance(FrameResolverService::class.java)

    private var cachedScriptText: String? = null
    private var compiledScript: Script? = null
    private var compilationError: String? = null
    private val lock = Any()

    /**
     * Resolve which backtrace frame index to display for this entry.
     * Executes the user's Groovy script with trace/tag/query bindings.
     * Returns 0 on any error.
     */
    fun resolve(entry: QueryEntry): Int {
        if (entry.backtrace.isEmpty()) return -1

        val state = service<ProfilerState>()
        val scriptText = state.frameResolverScript

        val script = getCompiledScript(scriptText) ?: return 0

        return try {
            val traceList = entry.backtrace.map { frame ->
                mapOf(
                    "file" to frame.file,
                    "line" to frame.line,
                    "call" to frame.call,
                    "function" to frame.function,
                    "class_name" to frame.class_name
                )
            }

            val binding = Binding().apply {
                setVariable("trace", traceList)
                setVariable("tag", entry.tag)
                setVariable("query", entry.query)
            }

            synchronized(lock) {
                script.binding = binding
                val result = script.run()
                when (result) {
                    is Number -> result.toInt().coerceIn(0, entry.backtrace.size - 1)
                    else -> 0
                }
            }
        } catch (e: Exception) {
            log.debug("Frame resolver script execution error: ${e.message}")
            0
        }
    }

    /**
     * Get or recompile the Groovy script. Caches the compiled script
     * and only recompiles when the script text changes.
     */
    private fun getCompiledScript(scriptText: String): Script? {
        synchronized(lock) {
            if (scriptText == cachedScriptText) {
                return compiledScript
            }

            return try {
                val config = CompilerConfiguration()
                val shell = GroovyShell(this::class.java.classLoader, Binding(), config)
                val script = shell.parse(scriptText)
                compiledScript = script
                cachedScriptText = scriptText
                compilationError = null
                script
            } catch (e: Exception) {
                log.warn("Frame resolver script compilation error: ${e.message}")
                compiledScript = null
                cachedScriptText = scriptText
                compilationError = e.message
                null
            }
        }
    }

    /**
     * Get the last compilation error, if any.
     */
    fun getCompilationError(): String? {
        synchronized(lock) {
            return compilationError
        }
    }

    /**
     * Force recompilation on next resolve() call.
     */
    fun invalidateCache() {
        synchronized(lock) {
            cachedScriptText = null
            compiledScript = null
            compilationError = null
        }
    }
}
