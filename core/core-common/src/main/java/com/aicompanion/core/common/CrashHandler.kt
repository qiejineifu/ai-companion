package com.aicompanion.core.common

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler private constructor(private val context: Context) :
    Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val crashLogDir = File(context.filesDir, "crash_logs")

    companion object {
        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        saveCrashLog(throwable)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            crashLogDir.mkdirs()
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val logFile = File(crashLogDir, "crash_${sdf.format(Date())}.log")

            PrintWriter(logFile).use { pw ->
                pw.println("Time: ${Date()}")
                pw.println("App Version: 0.3.0")
                pw.println("Android SDK: ${Build.VERSION.SDK_INT}")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                pw.println("---")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                pw.println(sw.toString())
            }

            // Keep only last 10 crash logs
            val logs = crashLogDir.listFiles()?.sortedByDescending { it.lastModified() }
            logs?.drop(10)?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    fun getCrashLogs(): List<File> {
        return crashLogDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
