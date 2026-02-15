package com.mariadbprofiler.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class BacktraceFrame(
    val file: String = "",
    val line: Int = 0,
    val call: String = "",
    // Legacy fields for backwards compatibility
    val function: String = "",
    val class_name: String = ""
) {
    val displayText: String
        get() {
            val fileName = java.io.File(file).name
            val caller = when {
                call.isNotEmpty() -> call
                class_name.isNotEmpty() -> "$class_name::$function"
                function.isNotEmpty() -> function
                else -> ""
            }
            return if (caller.isNotEmpty()) "$fileName:$line  $caller()" else "$fileName:$line"
        }
}
