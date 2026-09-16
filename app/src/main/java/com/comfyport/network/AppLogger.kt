package com.comfyport.network

import android.content.Context
import android.util.Log
import com.comfyport.data.LogLevel
import com.comfyport.data.LogFileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logFileManager: LogFileManager? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun init(context: Context) {
        logFileManager = LogFileManager(context.applicationContext)
    }

    private fun getFormattedTime(): String {
        return dateFormat.format(Date())
    }

    private fun sanitizeMessage(level: LogLevel, message: String): String {
        if (level == LogLevel.VERBOSE || level == LogLevel.DEBUG) {
            return message
        }
        var sanitized = message

        // 1. Redact API keys
        val apiKeyRegexes = listOf(
            Regex("""sk-[a-zA-Z0-9-_]{20,}"""),
            Regex("""sk-ant-[a-zA-Z0-9-_]{20,}"""),
            Regex("""AIzaSy[a-zA-Z0-9_-]{30,}"""),
            Regex("""(?i)"[^"]*key"[^:]*:\s*"[^"]+"""")
        )
        for (regex in apiKeyRegexes) {
            sanitized = sanitized.replace(regex) { matchResult ->
                val value = matchResult.value
                if (value.startsWith("\"") && value.contains(":")) {
                    val parts = value.split(":", limit = 2)
                    parts[0] + ": \"[REDACTED_API_KEY]\""
                } else {
                    "[REDACTED_API_KEY]"
                }
            }
        }

        // 2. Redact prompt body
        val promptRegexes = listOf(
            Regex("""(?i)"prompt"\s*:\s*"[^"]+""""),
            Regex("""(?i)prompt\s+body\s*:\s*.*"""),
            Regex("""(?i)prompt\s*=\s*[^,\)]+"""),
            Regex("""(?i)prompt\s*:\s*.*""")
        )
        for (regex in promptRegexes) {
            sanitized = sanitized.replace(regex) { matchResult ->
                val value = matchResult.value
                if (value.startsWith("\"")) {
                    "\"prompt\": \"[REDACTED_PROMPT]\""
                } else if (value.contains("=")) {
                    "prompt=[REDACTED_PROMPT]"
                } else if (value.contains(":")) {
                    val parts = value.split(":", limit = 2)
                    parts[0] + ": [REDACTED_PROMPT]"
                } else {
                    "[REDACTED_PROMPT]"
                }
            }
        }

        // 3. Redact JSON payloads
        val jsonRegex = Regex("""\{[\s\S]*\}""")
        if (jsonRegex.containsMatchIn(sanitized)) {
            if (sanitized.contains("\"") && (sanitized.contains(":") || sanitized.contains(","))) {
                sanitized = sanitized.replace(jsonRegex, "[REDACTED_JSON_PAYLOAD]")
            }
        }

        return sanitized
    }

    private fun log(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
        val sanitizedMsg = sanitizeMessage(level, msg)

        val logBuilder = StringBuilder()
        logBuilder.append(sanitizedMsg)

        if (tr != null) {
            if (level == LogLevel.VERBOSE || level == LogLevel.DEBUG) {
                val stackTrace = try {
                    Log.getStackTraceString(tr)
                } catch (_: Throwable) {
                    tr.stackTraceToString()
                }
                logBuilder.append("\n").append(stackTrace)
            } else {
                logBuilder.append(" [Exception: ").append(tr.javaClass.name).append(": ").append(tr.message).append("]")
            }
        }

        val fullMessage = logBuilder.toString()

        // 1. Write to Logcat (with JVM unit test fallback)
        try {
            when (level) {
                LogLevel.VERBOSE -> Log.v(tag, fullMessage)
                LogLevel.DEBUG -> Log.d(tag, fullMessage)
                LogLevel.INFO -> Log.i(tag, fullMessage)
                LogLevel.WARNING -> Log.w(tag, fullMessage)
                LogLevel.ERROR -> Log.e(tag, fullMessage)
            }
        } catch (_: Throwable) {
            println("[$tag] $fullMessage")
        }

        // 2. Write to Log File
        val fileMessage = "[${getFormattedTime()}] [${level.name}] [$tag] $fullMessage"
        logFileManager?.writeLog(fileMessage)
    }

    @JvmStatic
    fun v(tag: String, msg: String) {
        log(LogLevel.VERBOSE, tag, msg, null)
    }

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable?) {
        log(LogLevel.VERBOSE, tag, msg, tr)
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        log(LogLevel.DEBUG, tag, msg, null)
    }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable?) {
        log(LogLevel.DEBUG, tag, msg, tr)
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        log(LogLevel.INFO, tag, msg, null)
    }

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable?) {
        log(LogLevel.INFO, tag, msg, tr)
    }

    @JvmStatic
    fun w(tag: String, msg: String) {
        log(LogLevel.WARNING, tag, msg, null)
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable?) {
        log(LogLevel.WARNING, tag, msg, tr)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        log(LogLevel.ERROR, tag, msg, null)
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?) {
        log(LogLevel.ERROR, tag, msg, tr)
    }

    @JvmStatic
    fun getActiveLogSizeFormatted(): String {
        return logFileManager?.getActiveLogSizeFormatted() ?: "0 KB"
    }

    @JvmStatic
    fun readActiveLogFile(): String {
        return logFileManager?.readActiveLogFile() ?: ""
    }

    @JvmStatic
    fun clearLogs() {
        logFileManager?.clearLogs()
    }

    @JvmStatic
    fun getActiveLogFile(): File? {
        return logFileManager?.activeLogFile
    }
}
