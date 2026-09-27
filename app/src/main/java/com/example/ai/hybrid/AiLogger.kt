package com.example.ai.hybrid

/**
 * Robust diagnostic logger that safely logs to android.util.Log on Android devices
 * and falls back to standard output in JVM unit test runners where android.util.Log is not mocked.
 */
object AiLogger {
    fun i(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) android.util.Log.w(tag, msg, tr) else android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            if (tr != null) {
                println("[$tag] WARN: $msg: ${tr.message}")
            } else {
                println("[$tag] WARN: $msg")
            }
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) android.util.Log.e(tag, msg, tr) else android.util.Log.e(tag, msg)
        } catch (_: Throwable) {
            if (tr != null) {
                println("[$tag] ERROR: $msg: ${tr.message}")
            } else {
                println("[$tag] ERROR: $msg")
            }
        }
    }
}
