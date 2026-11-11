package com.huevoman77.cazahosts.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.huevoman77.cazahosts.MainActivity
import com.huevoman77.cazahosts.R
import com.huevoman77.cazahosts.data.ScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScanService : Service() {

    private var updater: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (!promote("Iniciando escaneo")) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!promote("Escaneando")) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (updater == null) {
            updater = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                while (isActive) {
                    delay(1500)
                    val manager = getSystemService(NotificationManager::class.java) ?: continue
                    try {
                        manager.notify(NOTIF_ID, buildNotification(ScanRepository.progress.value.phase))
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo actualizar la notificación", e)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updater?.cancel()
        updater = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Si el sistema no deja al servicio en primer plano, la app no debe caer. */
    private fun promote(phase: String): Boolean = try {
        startForeground(NOTIF_ID, buildNotification(phase))
        true
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo iniciar el servicio en primer plano", e)
        false
    }

    private fun buildNotification(phase: String): Notification {
        val progress = ScanRepository.progress.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (progress.total > 0) {
            "$phase - ${progress.scanned}/${progress.total} IPs - ${progress.alive} vivos - ${progress.portsFound} puertos"
        } else {
            "$phase - ${progress.alive} hosts vivos"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CazaHosts")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(progress.total.toInt().coerceAtLeast(1), progress.scanned.toInt().coerceAtMost(progress.total.toInt().coerceAtLeast(1)), false)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Escaneos en curso", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Notificación del escaneo activo"
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "scan_channel"
        private const val NOTIF_ID = 4201
        private const val TAG = "CazaHosts"
    }
}
