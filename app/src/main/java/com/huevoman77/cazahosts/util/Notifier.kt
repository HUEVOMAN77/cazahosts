package com.huevoman77.cazahosts.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.huevoman77.cazahosts.R

object Notifier {

    private const val CHANNEL_ID = "alerts_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Alertas de disponibilidad", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Avisos cuando un host vigilado cambia de estado"
        manager.createNotificationChannel(channel)
    }

    fun stateChanged(context: Context, ip: String, alive: Boolean) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val title = if (alive) "$ip volvió a estar en línea" else "$ip dejó de responder"
        val text = if (alive) {
            "El equipo volvió a responder en la red."
        } else {
            "El equipo dejó de responder en la red."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setAutoCancel(true)
            .build()
        manager.notify(ip.hashCode(), notification)
    }
}
