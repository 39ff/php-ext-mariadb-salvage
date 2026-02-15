package com.mariadbprofiler.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class BacktraceFrame(
    val file: String = "",
    val line: Int = 0,
    val function: String = "",
    val class_name: String = ""
) {
    val displayText: String
        get() {
            val fileName = file.substringAfterLast('/')
            val caller = if (class_name.isNotEmpty()) "$class_name::$function" else function
            return "$fileName:$line  $caller()"
        }
}
