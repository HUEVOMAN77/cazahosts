package com.huevoman77.cazahosts.util

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Guarda la ultima excepcion fatal en disco para poder reportarla desde el
 * telefono, donde no hay logcat a mano.
 */
object CrashLog {

    private const val TAG = "CazaHosts"
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(app.filesDir, FILE).writeText(
                    buildString {
                        appendLine("Fecha: ${Date()}")
                        appendLine("Hilo: ${thread.name}")
                        appendLine("Error: ${error.javaClass.name}: ${error.message}")
                        appendLine()
                        append(Log.getStackTraceString(error))
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun last(context: Context): String {
        val file = File(context.applicationContext.filesDir, FILE)
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
