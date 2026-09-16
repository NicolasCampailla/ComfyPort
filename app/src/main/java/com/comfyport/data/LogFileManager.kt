package com.comfyport.data

import android.content.Context
import java.io.File
import java.util.Locale

class LogFileManager(private val context: Context) {
    private val logsDir = File(context.filesDir, "logs").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    val activeLogFile: File
        get() = File(logsDir, "app.log")

    private val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5 MB
    private val MAX_ROTATED_FILES = 10

    @Synchronized
    fun writeLog(message: String) {
        try {
            val file = activeLogFile
            if (file.exists() && file.length() >= MAX_FILE_SIZE) {
                rotateLogs()
            }
            file.appendText(message + "\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun rotateLogs() {
        try {
            // Delete the oldest rotated file if it exists
            val oldestFile = File(logsDir, "app.log.$MAX_ROTATED_FILES")
            if (oldestFile.exists()) {
                oldestFile.delete()
            }

            // Shift older files down
            for (i in MAX_ROTATED_FILES - 1 downTo 1) {
                val currentFile = File(logsDir, "app.log.$i")
                if (currentFile.exists()) {
                    val nextFile = File(logsDir, "app.log.${i + 1}")
                    currentFile.renameTo(nextFile)
                }
            }

            // Rename active file to app.log.1
            val active = activeLogFile
            if (active.exists()) {
                val rotatedOne = File(logsDir, "app.log.1")
                active.renameTo(rotatedOne)
            }

            // Create a new empty active log file
            activeLogFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun clearLogs() {
        try {
            val files = logsDir.listFiles()
            if (files != null) {
                for (file in files) {
                    file.delete()
                }
            }
            activeLogFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getActiveLogSizeFormatted(): String {
        val file = activeLogFile
        if (!file.exists()) return "0 KB"
        val bytes = file.length()
        return if (bytes < 1024) {
            "$bytes B"
        } else if (bytes < 1024 * 1024) {
            String.format(Locale.US, "%.2f KB", bytes / 1024.0)
        } else {
            String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun readActiveLogFile(): String {
        val file = activeLogFile
        if (!file.exists()) return ""
        return try {
            file.readText()
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }
}
